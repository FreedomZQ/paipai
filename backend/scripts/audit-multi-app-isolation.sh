#!/usr/bin/env bash
# 多 App 隔离静态审计脚本。
#
# 目标：用低运维成本在本地/CI 提前发现“第二个 App 接入时最容易抄错”的问题，
# 包括裸 UserDefaults key、旧 auth compat route、Keychain 未按 appCode 隔离、Flyway placeholder 冲突等。
#
# 设计取舍：
# - blocker：会直接造成上线失败、隐私/账号串线、或数据库初始化失败的问题，退出码 1。
# - warning：需要人工复核的模板化债务，不直接阻断个人开发者日常迭代，避免误报拖慢上线。
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$(cd -- "$BACKEND_DIR/.." && pwd)"
IOS_DIR="$APP_DIR/paipai/ios/PaipaiReadAlongV2"
MIGRATION_DIR="$BACKEND_DIR/src/main/resources/db/first_version"

python3 - "$BACKEND_DIR" "$IOS_DIR" "$MIGRATION_DIR" <<'PY'
from pathlib import Path
import re
import sys

backend = Path(sys.argv[1])
ios = Path(sys.argv[2])
migrations = Path(sys.argv[3])

blockers = []
warnings = []

SKIP_PARTS = {
    'target', 'build', '.build', '.git', 'DerivedData', 'sandboxes', 'tmp', '.idea', '.swiftpm'
}
TEXT_SUFFIXES = {'.java', '.swift', '.kt', '.md', '.txt', '.yml', '.yaml', '.sql', '.sh', '.properties'}

def iter_files(root: Path):
    if not root.exists():
        return
    for path in root.rglob('*'):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        if any(part in SKIP_PARTS for part in path.parts):
            continue
        yield path

def rel(path: Path):
    try:
        return str(path.relative_to(backend.parent))
    except ValueError:
        return str(path)

def add(kind, path, line_no, msg, line=None):
    item = f"{rel(path)}:{line_no}: {msg}"
    if line:
        item += f" :: {line.strip()}"
    (blockers if kind == 'blocker' else warnings).append(item)

# 1) 数据库初始化：首发库只允许单一 V1 基线。
if migrations.exists():
    migration_paths = sorted(migrations.glob('V*.sql'))
    migration_files = [p.name for p in migration_paths]
    if migration_files != ['V1__init.sql']:
        blockers.append(f"db/first_version must contain only unified baseline V1__init.sql, found: {migration_files}")
    seen_versions = {}
    for path in migration_paths:
        match = re.match(r'V(\d+)__.+\.sql$', path.name)
        if not match:
            blockers.append(f"db/first_version file must follow Flyway V<number>__description.sql naming: {path.name}")
            continue
        version = int(match.group(1))
        if version in seen_versions:
            blockers.append(f"duplicate Flyway migration version V{version}: {seen_versions[version]} and {path.name}")
        seen_versions[version] = path.name
        text = path.read_text(encoding='utf-8', errors='ignore')
        for idx, line in enumerate(text.splitlines(), 1):
            # Flyway 会解析 ${...}，headers 里需要用 SQL 拼接 '$' || '{API_KEY}' 保留字面值。
            if '${' in line:
                add('blocker', path, idx, 'raw Flyway placeholder-style token found; use SQL concatenation to preserve literal values', line)
    init_sql = migrations / 'V1__init.sql'
    if init_sql.exists():
        text = init_sql.read_text(encoding='utf-8', errors='ignore')
        for token in [
            'CREATE TABLE public.sys_app',
            'CREATE TABLE public.sys_remote_config',
            'CREATE TABLE public.reading_usage_session',
            'reading_usage_policy',
            'paipai_readingcompanion',
        ]:
            if token not in text:
                blockers.append(f"db/first_version/V1__init.sql missing required token: {token}")

# 2) iOS 本地存储：裸 UserDefaults key 会造成未来多 App 或 bundle 复用时数据串线。
if ios.exists():
    for path in iter_files(ios):
        text = path.read_text(encoding='utf-8', errors='ignore')
        for idx, line in enumerate(text.splitlines(), 1):
            if 'UserDefaults.standard' in line and 'AppScopedDefaults' not in str(path):
                add('blocker', path, idx, 'raw UserDefaults.standard usage; route through AppScopedDefaults', line)
            if '/api/v1/auth/' in line:
                add('blocker', path, idx, 'legacy auth compat route without appCode found', line)
            if 'com.paipai.readalong.auth' in line or 'current-bearer-session"' in line:
                # SecureSessionStore 允许 account 由 appCode 拼接；固定字符串才阻断。
                if 'appCode' not in line and 'bundleIdentifier' not in line:
                    add('blocker', path, idx, 'Keychain service/account must include appCode or bundle identifier', line)
            if re.search(r'"paipai\.[A-Za-z0-9_.-]+"', line) and 'AppDefaultKey' not in str(path):
                add('warning', path, idx, 'hard-coded paipai local key; verify AppScopedDefaults wraps it', line)

# 3) 后端路由与 appCode：兼容层可存在，但 system auth 等跨 App 入口必须显式带 appCode。
for root in [backend / 'src/main/java', backend / 'src/test/java']:
    for path in iter_files(root):
        text = path.read_text(encoding='utf-8', errors='ignore')
        for idx, line in enumerate(text.splitlines(), 1):
            if '/api/v1/auth/apple' in line or '/api/v1/auth/me' in line or '/api/v1/auth/logout' in line:
                add('blocker', path, idx, 'legacy auth route without appCode found', line)
            if 'private static final String APP_CODE' in line and 'AppModule.APP_CODE' not in line and 'AppDefinition' not in text:
                add('warning', path, idx, 'private APP_CODE constant should be traceable to AppModule/AppDefinition', line)
            if re.search(r'"paipai_readingcompanion"', line) and 'ReadingAppModule' not in str(path) and 'AppCodes.java' not in str(path) and 'app-definition' not in str(path):
                # 测试与配置里允许显式 appCode；源码业务类里标 warning，给人工复核。
                # AppCodes.java 是唯一允许保留产品 appCode 字面量的常量中心，业务类应引用常量或 AppModule。
                if '/src/main/java/' in str(path):
                    add('warning', path, idx, 'literal paipai appCode in main source; verify it is an AppModule boundary, not scattered business logic', line)

print('multi-app isolation audit')
print(f'  backend:    {backend}')
print(f'  ios:        {ios}')
print(f'  migrations: {migrations}')
print('')

if warnings:
    print('WARNINGS:')
    for item in warnings:
        print(f'  - {item}')
    print('')
else:
    print('WARNINGS: none')

if blockers:
    print('BLOCKERS:')
    for item in blockers:
        print(f'  - {item}')
    sys.exit(1)

print('BLOCKERS: none')
print('audit result: PASS')
PY
