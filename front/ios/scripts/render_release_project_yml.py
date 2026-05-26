#!/usr/bin/env python3
"""Render an XcodeGen project.yml for the no-backend iOS release build."""
import argparse
import os
import re
import sys
from pathlib import Path

ENV_KEYS = {
    "PRODUCT_BUNDLE_IDENTIFIER": "RELEASE_IOS_BUNDLE_IDENTIFIER",
    "DEVELOPMENT_TEAM": "RELEASE_IOS_DEVELOPMENT_TEAM",
    "MARKETING_VERSION": "RELEASE_IOS_MARKETING_VERSION",
    "CURRENT_PROJECT_VERSION": "RELEASE_IOS_CURRENT_PROJECT_VERSION",
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


def replace_setting_line(text, key, value):
    pattern = re.compile(rf"^(\s*{re.escape(key)}:\s*)(?:'.*?'|\".*?\"|[^#\n]+)(\s*(?:#.*)?$)", re.MULTILINE)
    replacement = rf"\g<1>'{value}'\g<2>"
    new_text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        fail(f"could not replace setting {key}")
    return new_text


def main():
    parser = argparse.ArgumentParser(description="Render no-backend release project.yml from RELEASE_IOS_* env vars")
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
    for setting, value in values.items():
        text = replace_setting_line(text, setting, value)

    forbidden = ["PAIPAI_API_BASE_URL", "__FILL_FROM_DB_", "http://127.0.0.1", "localhost"]
    for token in forbidden:
        if token.lower() in text.lower():
            fail(f"rendered project contains forbidden release token: {token}")

    output.write_text(text)
    print(f"[P0][OK] rendered release project file: {output}")


if __name__ == "__main__":
    main()
