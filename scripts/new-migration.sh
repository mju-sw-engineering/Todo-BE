#!/usr/bin/env bash
# 타임스탬프 버전의 Flyway 마이그레이션 파일을 생성한다.
#
# 사용법: bash scripts/new-migration.sh add_work_item_check_ins
# 결과:   src/main/resources/db/migration/V20260807223000__add_work_item_check_ins.sql
set -euo pipefail

if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "사용법: bash scripts/new-migration.sh <설명(snake_case)>" >&2
  exit 1
fi

description="$1"
if ! echo "$description" | grep -qE '^[a-z0-9_]+$'; then
  echo "설명은 소문자 snake_case로 작성하세요 (예: add_user_index)" >&2
  exit 1
fi

MIGRATION_DIR="src/main/resources/db/migration"
timestamp=$(TZ=Asia/Seoul date +%Y%m%d%H%M%S)
file="$MIGRATION_DIR/V${timestamp}__${description}.sql"

if [ -e "$file" ]; then
  echo "이미 존재합니다: $file" >&2
  exit 1
fi

cat > "$file" <<EOF
-- TODO: 마이그레이션 내용을 작성하세요.
EOF

echo "생성됨: $file"
