#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

blockers=0

echo "mybatis-plus data access audit"
echo "  root: $ROOT"

echo
printf 'Checking Mapper interfaces extend BaseMapper...\n'
missing_base=()
while IFS= read -r mapper; do
  if ! grep -q 'extends BaseMapper<' "$mapper"; then
    missing_base+=("$mapper")
  fi
done < <(find src/main/java/com/apphub/backend -path '*mapper*' -name '*.java' | sort)

if ((${#missing_base[@]})); then
  blockers=1
  printf 'BLOCKER: mapper interfaces missing BaseMapper:\n'
  printf '  %s\n' "${missing_base[@]}"
else
  printf 'PASS: all mapper interfaces extend BaseMapper.\n'
fi

echo
printf 'Checking reusable ServiceImpl data access boundary...\n'
service_impl_count=$(grep -R "extends ServiceImpl<" -n src/main/java/com/apphub/backend --include='*.java' | wc -l | tr -d ' ')
printf '  ServiceImpl implementations: %s\n' "$service_impl_count"

if ((service_impl_count == 0)); then
  blockers=1
  printf 'BLOCKER: no ServiceImpl-based reusable data access services found.\n'
fi

echo
printf 'Direct Mapper dependencies outside mapper/data-service impl packages (migration backlog):\n'
# These are not failed yet because legacy reading/sys modules are being migrated in phases.
grep -RIn "domain.mapper.*Mapper\|private final .*Mapper" src/main/java/com/apphub/backend --include='*.java' \
  | grep -v '/domain/mapper/' \
  | grep -v '/domain/service/impl/' \
  | grep -v '/service/impl/.*DataServiceImpl.java' \
  | grep -v '/service/crud/impl/' \
  | grep -v 'ObjectMapper' \
  | sed -n '1,240p' || true

echo
if ((blockers)); then
  echo "audit result: FAIL"
  exit 1
fi

echo "audit result: PASS_WITH_MIGRATION_BACKLOG"
