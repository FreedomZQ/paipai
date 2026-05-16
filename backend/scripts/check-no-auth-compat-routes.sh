#!/usr/bin/env bash
# 作用：阻止无 appCode 的旧 auth compat 路由重新进入运行时代码、iOS 客户端或联调/发布文档。
# 约定：
#   - 任何 `/api/v1/auth/...` Apple auth / me / logout 路由一旦出现在受检目录，即视为失败
#   - 历史沙箱、target 产物和临时目录不参与检查
#   - 适合本地 pre-release / CI 静态门禁
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$(cd -- "$BACKEND_DIR/.." && pwd)"
IOS_DIR="$APP_DIR/paipai/ios/PaipaiReadAlongV2"

python3 - "$BACKEND_DIR" "$IOS_DIR" <<'PY'
from pathlib import Path
import sys

backend_dir = Path(sys.argv[1])
ios_dir = Path(sys.argv[2])
checks = [
    backend_dir / 'src/main/java',
    backend_dir / 'src/test/java',
    backend_dir / 'README.md',
    backend_dir / 'docs',
    backend_dir / 'files',
    ios_dir,
]
forbidden = [
    '/api/v1/auth/apple/exchange',
    '/api/v1/auth/apple/refresh',
    '/api/v1/auth/apple/revoke',
    '/api/v1/auth/me',
    '/api/v1/auth/logout',
]
allowed_suffixes = {'.java', '.kt', '.swift', '.md', '.txt', '.yml', '.yaml'}
violations = []

for root in checks:
    if not root.exists():
        continue
    paths = [root] if root.is_file() else [p for p in root.rglob('*') if p.is_file()]
    for path in paths:
        if path.suffix.lower() not in allowed_suffixes:
            continue
        text = path.read_text(encoding='utf-8', errors='ignore')
        lines = text.splitlines()
        for idx, line in enumerate(lines, start=1):
            for token in forbidden:
                if token in line:
                    violations.append((str(path), idx, token, line.strip()))

if violations:
    print('forbidden auth compat routes found:', file=sys.stderr)
    for path, line_no, token, line in violations:
        print(f'  {path}:{line_no}: contains {token} :: {line}', file=sys.stderr)
    sys.exit(1)

print('auth compat route guard: PASS (no legacy /api/v1/auth Apple auth/me/logout routes found)')
PY
