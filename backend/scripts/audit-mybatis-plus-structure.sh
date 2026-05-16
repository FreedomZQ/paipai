#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

blockers=0

echo "mybatis-plus structure audit"
echo "  root: $ROOT"

missing_entity_annotations=()
manual_entity_accessors=()
while IFS= read -r entity; do
  for annotation in '@TableName' '@TableId' '@Data' '@NoArgsConstructor' '@AllArgsConstructor'; do
    if ! grep -q "$annotation" "$entity"; then
      missing_entity_annotations+=("$entity missing $annotation")
    fi
  done
  if grep -Eq 'public[[:space:]]+[[:alnum:]_<>, ?]+[[:space:]]+(get|set|is)[[:alnum:]_]*[[:space:]]*\(' "$entity"; then
    manual_entity_accessors+=("$entity")
  fi
done < <(find src/main/java/com/apphub/backend -name '*Entity.java' | sort)

printf '\nChecking entity @TableName/@TableId + Lombok annotations...\n'
if ((${#missing_entity_annotations[@]})); then
  blockers=1
  printf 'BLOCKER: entity annotation gaps:\n'
  printf '  %s\n' "${missing_entity_annotations[@]}"
else
  printf 'PASS: all entities have @TableName, @TableId, @Data, @NoArgsConstructor, @AllArgsConstructor.\n'
fi

printf '\nChecking entity boilerplate accessors removed...\n'
if ((${#manual_entity_accessors[@]})); then
  blockers=1
  printf 'BLOCKER: manual entity getters/setters remain:\n'
  printf '  %s\n' "${manual_entity_accessors[@]}"
else
  printf 'PASS: entity getters/setters are delegated to Lombok.\n'
fi

printf '\nChecking Mapper interfaces extend BaseMapper...\n'
missing_base=()
while IFS= read -r mapper; do
  if ! grep -q 'extends BaseMapper<' "$mapper"; then
    missing_base+=("$mapper")
  fi
done < <(find src/main/java/com/apphub/backend -path '*/mapper/*Mapper.java' | sort)
if ((${#missing_base[@]})); then
  blockers=1
  printf 'BLOCKER: Mapper interfaces missing BaseMapper:\n'
  printf '  %s\n' "${missing_base[@]}"
else
  printf 'PASS: all MyBatis Mapper interfaces extend BaseMapper.\n'
fi

printf '\nChecking Mapper -> CRUD Service -> ServiceImpl triplets...\n'
missing_triplets=()
while IFS= read -r mapper; do
  entity=$(grep -Eo 'extends BaseMapper<[[:space:]]*[A-Za-z0-9_]+' "$mapper" | sed -E 's/.*< *//')
  [[ -n "$entity" ]] || continue
  base="${entity%Entity}"
  if ! find src/main/java/com/apphub/backend -path '*/service/crud/*CrudService.java' -name "${base}CrudService.java" | grep -q .; then
    missing_triplets+=("$mapper missing ${base}CrudService")
  fi
  if ! find src/main/java/com/apphub/backend -path '*/service/crud/impl/*CrudServiceImpl.java' -name "${base}CrudServiceImpl.java" | grep -q .; then
    missing_triplets+=("$mapper missing ${base}CrudServiceImpl")
  elif ! grep -R "extends ServiceImpl<.*$(basename "$mapper" .java),[[:space:]]*$entity>" src/main/java/com/apphub/backend --include="${base}CrudServiceImpl.java" >/dev/null; then
    missing_triplets+=("$mapper ${base}CrudServiceImpl missing ServiceImpl<Mapper, Entity>")
  fi
done < <(find src/main/java/com/apphub/backend -path '*/mapper/*Mapper.java' | sort)
if ((${#missing_triplets[@]})); then
  blockers=1
  printf 'BLOCKER: missing CRUD service triplets:\n'
  printf '  %s\n' "${missing_triplets[@]}"
else
  printf 'PASS: each Mapper has a matching CRUD Service interface and ServiceImpl.\n'
fi

printf '\nDirect Mapper dependencies outside Mapper/DataService/CRUD impl packages (migration backlog, should stay out of new code):\n'
grep -RIn "domain.mapper.*Mapper\|sys\..*\.mapper.*Mapper\|private final .*Mapper" src/main/java/com/apphub/backend --include='*.java' \
  | grep -v '/domain/mapper/' \
  | grep -v '/mapper/' \
  | grep -v '/domain/service/impl/' \
  | grep -v '/service/impl/.*DataServiceImpl.java' \
  | grep -v '/service/crud/impl/' \
  | grep -v 'ObjectMapper' \
  | sed -n '1,240p' || true

printf '\nSummary counts:\n'
printf '  Entities: %s\n' "$(find src/main/java/com/apphub/backend -name '*Entity.java' | wc -l | tr -d ' ')"
printf '  Mappers: %s\n' "$(find src/main/java/com/apphub/backend -path '*/mapper/*Mapper.java' | wc -l | tr -d ' ')"
printf '  CRUD Service interfaces: %s\n' "$(find src/main/java/com/apphub/backend -path '*/service/crud/*CrudService.java' | wc -l | tr -d ' ')"
printf '  CRUD ServiceImpl classes: %s\n' "$(find src/main/java/com/apphub/backend -path '*/service/crud/impl/*CrudServiceImpl.java' | wc -l | tr -d ' ')"

if ((blockers)); then
  echo "audit result: FAIL"
  exit 1
fi

echo "audit result: PASS_WITH_DIRECT_MAPPER_BACKLOG"
