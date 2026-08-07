#!/usr/bin/env bash
# Flyway 마이그레이션 버전 검사.
#
# 1) 중복 버전 — 같은 V번호가 두 개면 Flyway가 기동을 거부해 운영이 다운된다 (2026-08-07 V10 중복 사고).
# 2) 역순 버전 (PR에서만) — 새로 추가된 마이그레이션이 base 브랜치의 최대 버전보다 작거나 같으면,
#    이미 그 뒤 버전까지 적용된 운영 DB에서 out-of-order로 실패하거나 조용히 스킵된다.
#
# BASE_REF 환경변수(예: main)가 있으면 2)를 함께 검사한다. 없으면 1)만 검사한다.
# 버전 비교는 숫자 기준이라 순번(V19)이든 타임스탬프(V20260807221100)든 동작한다.
set -euo pipefail

MIGRATION_DIR="src/main/resources/db/migration"

fail=0

# 1) 중복 버전 검사
duplicates=$(find "$MIGRATION_DIR" -name 'V*__*.sql' -exec basename {} \; \
  | grep -oE '^V[0-9]+' | sort | uniq -d)
if [ -n "$duplicates" ]; then
  echo "::error::중복된 마이그레이션 버전이 있습니다: ${duplicates//$'\n'/, }"
  fail=1
fi

# 2) base 대비 역순 버전 검사 (PR에서만)
if [ -n "${BASE_REF:-}" ]; then
  base_max=$(git ls-tree -r --name-only "origin/${BASE_REF}" "$MIGRATION_DIR" 2>/dev/null \
    | grep -oE 'V[0-9]+__' | grep -oE '[0-9]+' | sort -n | tail -1 || true)

  if [ -n "$base_max" ]; then
    # --no-renames: 리네임을 삭제+추가로 취급해 바뀐 파일명도 검사한다.
    added=$(git diff --name-only --no-renames --diff-filter=A \
      "origin/${BASE_REF}"...HEAD -- "$MIGRATION_DIR" || true)

    for file in $added; do
      name=$(basename "$file")
      # 새 마이그레이션은 타임스탬프 버전(V<yyyyMMddHHmmss>__)만 허용한다.
      # 순번 방식은 두 브랜치가 같은 다음 번호를 고르면 충돌한다. AGENTS.md 참조.
      if ! echo "$name" | grep -qE '^V[0-9]{14}__.+\.sql$'; then
        echo "::error::$file — 새 마이그레이션은 V<yyyyMMddHHmmss>__<설명>.sql 형식이어야 합니다. scripts/new-migration.sh로 생성하세요."
        fail=1
        continue
      fi
      version=$(echo "$name" | grep -oE '^V[0-9]+' | grep -oE '[0-9]+')
      if [ "$version" -le "$base_max" ]; then
        echo "::error::$file 의 버전(V$version)이 ${BASE_REF}의 최대 버전(V$base_max)보다 뒤여야 합니다. 타임스탬프를 현재 시각으로 갱신해 리네임하세요."
        fail=1
      fi
    done
  fi
fi

if [ "$fail" -eq 0 ]; then
  echo "마이그레이션 버전 검사 통과"
fi
exit "$fail"
