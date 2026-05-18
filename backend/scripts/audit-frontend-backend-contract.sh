#!/usr/bin/env bash
# 前后端接口契约静态审计。
#
# 中文说明：
# 这个脚本不替代真实集成测试，但能用极低成本发现最常见的上线前问题：
# - iOS BackendClient 写了后端不存在的路径；
# - 后端删除/改名了接口但前端还在调用；
# - 重新出现不带 appCode 的旧认证路径；
# - 前端发布包退回 localhost / 127.0.0.1。
#
# 允许动态占位：{childId}、{taskId}、{appCode} 等会被规范化为 {} 后比较。
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$(cd -- "$BACKEND_DIR/.." && pwd)"
IOS_CLIENT="${IOS_CLIENT:-$APP_DIR/front/ios/PaipaiReadAlong/Core/Services/BackendClient.swift}"
BACKEND_SRC="$BACKEND_DIR/src/main/java/com/apphub/backend"

python3 - "$BACKEND_SRC" "$IOS_CLIENT" <<'PY'
from pathlib import Path
import re
import sys

backend_src = Path(sys.argv[1])
ios_client = Path(sys.argv[2])
blockers = []
warnings = []

METHOD_MAP = {
    'GetMapping': {'GET'},
    'PostMapping': {'POST'},
    'PutMapping': {'PUT'},
    'PatchMapping': {'PATCH'},
    'DeleteMapping': {'DELETE'},
}

STATIC_PREFIXES_INTENTIONALLY_PUBLIC = {
    '/api/v1/bootstrap/config',
    '/api/v1/plans',
    '/api/v1/legal/docs',
}

LEGACY_AUTH_PATHS = {
    '/api/v1/auth/me',
    '/api/v1/auth/logout',
    '/api/v1/auth/apple/exchange',
}


def strip_comments(text: str) -> str:
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//.*', '', text)
    return text


def normalize(path: str) -> str:
    path = path.replace('\\(', '{').replace(')', '}')
    path = re.sub(r'\{[^}/]+\}', '{}', path)
    path = re.sub(r'/+', '/', path)
    if len(path) > 1 and path.endswith('/'):
        path = path[:-1]
    return path


def extract_mapping_paths(annotation: str):
    # Supports @GetMapping("/x"), @PostMapping({"/a", "/b"}), @RequestMapping("/base")
    return re.findall(r'"([^"]*)"', annotation)

backend_routes = set()
backend_paths_by_file = {}
for path in backend_src.rglob('*.java'):
    text = strip_comments(path.read_text(encoding='utf-8', errors='ignore'))
    class_prefixes = ['']
    class_match = re.search(r'(?:public\s+)?(?:class|interface)\s+\w+', text)
    if class_match:
        # 中文说明：class 上的 @RequestMapping 和 class 之间可能夹着 @Validated / @Tag 等注解，
        # 不能只匹配“@RequestMapping 紧贴 class”的格式，否则会漏掉真实后端路由。
        before_class = text[:class_match.start()]
        class_mappings = re.findall(r'@RequestMapping\s*\(([^)]*)\)', before_class, flags=re.S)
        if class_mappings:
            found = extract_mapping_paths(class_mappings[-1])
            class_prefixes = found or ['']
    method_pattern = re.compile(r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\s*(?:\(([^)]*)\))?', re.S)
    for mm in method_pattern.finditer(text):
        ann = mm.group(0)
        paths = extract_mapping_paths(ann) or ['']
        for prefix in class_prefixes:
            for p in paths:
                full = normalize('/' + prefix.strip('/') + '/' + p.strip('/'))
                for method in METHOD_MAP[mm.group(1)]:
                    backend_routes.add((method, full))
                    backend_paths_by_file.setdefault((method, full), []).append(str(path))

if not ios_client.exists():
    blockers.append(f'missing iOS BackendClient: {ios_client}')
else:
    ios_text = strip_comments(ios_client.read_text(encoding='utf-8', errors='ignore'))
    # Infer endpoint var for submitTransactionIntake ternary.
    endpoint_assignments = {}
    for name, a, b in re.findall(r'let\s+(\w+)\s*=\s*[^\n?]+\?\s*"([^"]+)"\s*:\s*"([^"]+)"', ios_text, flags=re.S):
        endpoint_assignments[name] = [a, b]

    iost_calls = []
    send_call_pattern = re.compile(r'send\s*\((.*?)\)', re.S)
    for call in send_call_pattern.finditer(ios_text):
        args = call.group(1)
        path_values = []
        literal_match = re.search(r'path\s*:\s*"([^"]+)"', args)
        if literal_match:
            path_values.append(literal_match.group(1))
        var_match = re.search(r'path\s*:\s*(\w+)', args)
        if var_match and var_match.group(1) in endpoint_assignments:
            path_values.extend(endpoint_assignments[var_match.group(1)])
        route_match = re.search(r'path\s*:\s*routes\.(\w+)', args)
        if route_match:
            # routes.* are app-scoped dynamic paths; compare by route name separately below.
            continue
        if not path_values:
            continue
        method_match = re.search(r'method\s*:\s*"([A-Z]+)"', args)
        method = method_match.group(1) if method_match else 'GET'
        for p in path_values:
            iost_calls.append((method, normalize(p)))

    for method, path in sorted(set(iost_calls)):
        if path in LEGACY_AUTH_PATHS:
            blockers.append(f'iOS uses forbidden legacy auth path: {method} {path}')
            continue
        if (method, path) not in backend_routes:
            # Some account/system paths can be intentionally constructed outside BackendClient via BackendRoute routes.*;
            # literal paths should exist exactly on the reading compat surface.
            blockers.append(f'iOS BackendClient path has no matching backend mapping: {method} {path}')

    # Ensure app-scoped dynamic routes exist on backend as normalized patterns.
    expected_dynamic = [
        ('GET', '/api/v1/system/auth/apps/{}/me'),
        ('POST', '/api/v1/system/auth/apps/{}/apple/exchange'),
        ('POST', '/api/v1/system/auth/apps/{}/logout'),
        ('POST', '/api/v1/powersync/{}/bootstrap'),
        ('POST', '/api/v1/powersync/{}/token'),
        ('POST', '/api/v1/powersync/{}/rebuild'),
        ('POST', '/api/v1/powersync/{}/upload'),
    ]
    for route in expected_dynamic:
        if route not in backend_routes:
            blockers.append(f'app-scoped dynamic backend route missing: {route[0]} {route[1]}')

    for idx, line in enumerate(ios_text.splitlines(), 1):
        if ('localhost' in line or '127.0.0.1' in line) and 'Refusing to fall back' not in line:
            blockers.append(f'{ios_client}:{idx}: iOS source contains localhost/127.0.0.1 fallback')
        if 'UserDefaults.standard' in line and 'AppScopedDefaults' not in line:
            blockers.append(f'{ios_client}:{idx}: raw UserDefaults.standard usage in BackendClient')

print('frontend/backend contract audit')
print(f'  backend routes: {len(backend_routes)}')
print(f'  iOS client:     {ios_client}')
print('')

if warnings:
    print('WARNINGS:')
    for item in warnings:
        print(f'  - {item}')
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
