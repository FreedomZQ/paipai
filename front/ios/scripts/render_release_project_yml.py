#!/usr/bin/env python3
"""Render an XcodeGen project.yml for iOS release builds from release_ios values.

The source project.yml intentionally contains placeholders so local development cannot
accidentally archive with stale production identifiers. This script creates a materialized
release project file after validating the values that should come from the backend
release_ios namespace.
"""
import argparse
import os
import re
import sys
from pathlib import Path

PLACEHOLDERS = {
    "PRODUCT_BUNDLE_IDENTIFIER": "__FILL_FROM_DB_release_ios.bundle_identifier__",
    "DEVELOPMENT_TEAM": "__FILL_FROM_DB_release_ios.development_team__",
    "MARKETING_VERSION": "__FILL_FROM_DB_release_ios.marketing_version__",
    "CURRENT_PROJECT_VERSION": "__FILL_FROM_DB_release_ios.current_project_version__",
}

ENV_KEYS = {
    "PRODUCT_BUNDLE_IDENTIFIER": "RELEASE_IOS_BUNDLE_IDENTIFIER",
    "DEVELOPMENT_TEAM": "RELEASE_IOS_DEVELOPMENT_TEAM",
    "MARKETING_VERSION": "RELEASE_IOS_MARKETING_VERSION",
    "CURRENT_PROJECT_VERSION": "RELEASE_IOS_CURRENT_PROJECT_VERSION",
    "PAIPAI_API_BASE_URL": "RELEASE_IOS_PAIPAI_API_BASE_URL",
}


def fail(message):
    print(f"[P0][FAIL] {message}", file=sys.stderr)
    raise SystemExit(1)


def require_env(name):
    value = os.environ.get(name, "").strip()
    if not value:
        fail(f"missing required env {name}")
    return value


def validate(values):
    bundle_id = values["PRODUCT_BUNDLE_IDENTIFIER"]
    if not re.fullmatch(r"[A-Za-z0-9]+(\.[A-Za-z0-9][A-Za-z0-9_-]*)+", bundle_id):
        fail(f"invalid bundle identifier: {bundle_id}")

    team = values["DEVELOPMENT_TEAM"]
    if not re.fullmatch(r"[A-Z0-9]{10}", team):
        fail("DEVELOPMENT_TEAM must be a 10-character Apple Team ID")

    marketing = values["MARKETING_VERSION"]
    if not re.fullmatch(r"\d+(\.\d+){1,2}", marketing):
        fail("MARKETING_VERSION must look like 1.0 or 1.0.0")

    build = values["CURRENT_PROJECT_VERSION"]
    if not re.fullmatch(r"[1-9]\d*", build):
        fail("CURRENT_PROJECT_VERSION must be a positive integer build number")

    api_base = values["PAIPAI_API_BASE_URL"]
    if not api_base.startswith("https://"):
        fail("RELEASE_IOS_PAIPAI_API_BASE_URL must be HTTPS for release builds")
    if "127.0.0.1" in api_base or "localhost" in api_base:
        fail("release API base URL must not point at localhost")


def replace_setting_line(text, key, value):
    # Match YAML setting lines like: KEY: 'value' or KEY: value
    pattern = re.compile(rf"^(\s*{re.escape(key)}:\s*)(?:'.*?'|\".*?\"|[^#\n]+)(\s*(?:#.*)?$)", re.MULTILINE)
    replacement = rf"\g<1>'{value}'\g<2>"
    new_text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        fail(f"could not replace setting {key}")
    return new_text


def main():
    parser = argparse.ArgumentParser(description="Render release project.yml from RELEASE_IOS_* env vars")
    parser.add_argument("--source", default="project.yml", help="source project.yml path")
    parser.add_argument("--output", default="project.release.yml", help="output project.yml path")
    args = parser.parse_args()

    source = Path(args.source)
    output = Path(args.output)
    if not source.exists():
        fail(f"source project file not found: {source}")

    values = {setting: require_env(env_name) for setting, env_name in ENV_KEYS.items()}
    validate(values)

    text = source.read_text()
    for setting, placeholder in PLACEHOLDERS.items():
        if placeholder not in text:
            fail(f"source project file does not contain expected placeholder for {setting}")
        text = replace_setting_line(text, setting, values[setting])

    text = replace_setting_line(text, "PAIPAI_API_BASE_URL", values["PAIPAI_API_BASE_URL"])

    # Keep the release-rendered file free of development-only placeholders and localhost hints,
    # including comments. The source project.yml remains the documented local-development file.
    text = "\n".join(
        line for line in text.splitlines()
        if "__FILL_FROM_DB_" not in line
        and "http://127.0.0.1" not in line
        and "localhost" not in line.lower()
    ) + "\n"

    if "__FILL_FROM_DB_" in text:
        fail("rendered project still contains release placeholders")
    if "http://127.0.0.1" in text or "localhost" in text.lower():
        fail("rendered project still contains local backend URL")

    output.write_text(text)
    print(f"[P0][OK] rendered release project file: {output}")


if __name__ == "__main__":
    main()
