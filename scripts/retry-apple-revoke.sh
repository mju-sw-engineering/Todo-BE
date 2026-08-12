#!/usr/bin/env bash
# FAILED로 확정된 apple_revoke_outbox 건을 다시 재시도 대상(PENDING)으로 되돌린다.
#
# 최대 재시도 횟수를 넘겨 FAILED로 확정된 건은 스케줄러(AppleRevokeOutboxPoller)가
# 더 이상 건드리지 않는다. 원인(Apple 서버 장애 등)이 해소됐다고 판단되면 이 스크립트로
# 상태를 PENDING으로 되돌려 다음 폴링 주기(기본 1분)에 다시 시도하게 한다.
#
# 사용법: DB_URL=... DB_USERNAME=... DB_PASSWORD=... bash scripts/retry-apple-revoke.sh <outboxId>
set -euo pipefail

if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "사용법: DB_URL=... DB_USERNAME=... DB_PASSWORD=... bash scripts/retry-apple-revoke.sh <outboxId>" >&2
  exit 1
fi

outbox_id="$1"

if ! [[ "$outbox_id" =~ ^[0-9]+$ ]]; then
  echo "outboxId는 숫자여야 합니다: $outbox_id" >&2
  exit 1
fi

: "${DB_URL:?DB_URL 환경변수가 필요합니다 (예: jdbc:mysql://localhost:3306/todo)}"
: "${DB_USERNAME:?DB_USERNAME 환경변수가 필요합니다}"
: "${DB_PASSWORD:?DB_PASSWORD 환경변수가 필요합니다}"

# jdbc:mysql://호스트:포트/DB명?옵션... 에서 mysql CLI가 쓸 값만 뽑아낸다.
if [[ "$DB_URL" =~ jdbc:mysql://([^:/]+):([0-9]+)/([^?]+) ]]; then
  db_host="${BASH_REMATCH[1]}"
  db_port="${BASH_REMATCH[2]}"
  db_name="${BASH_REMATCH[3]}"
else
  echo "DB_URL 형식을 이해할 수 없습니다: $DB_URL" >&2
  exit 1
fi

mysql_args=(-h "$db_host" -P "$db_port" -u "$DB_USERNAME" -D "$db_name")

echo "현재 상태:"
MYSQL_PWD="$DB_PASSWORD" mysql "${mysql_args[@]}" -e \
  "SELECT id, user_id, status, attempt_count, next_attempt_at FROM apple_revoke_outbox WHERE id = $outbox_id;"

MYSQL_PWD="$DB_PASSWORD" mysql "${mysql_args[@]}" -e \
  "UPDATE apple_revoke_outbox SET status = 'PENDING', attempt_count = 0, next_attempt_at = NOW() WHERE id = $outbox_id;"

echo ""
echo "변경 후 상태:"
MYSQL_PWD="$DB_PASSWORD" mysql "${mysql_args[@]}" -e \
  "SELECT id, user_id, status, attempt_count, next_attempt_at FROM apple_revoke_outbox WHERE id = $outbox_id;"

echo ""
echo "완료: outboxId=$outbox_id 를 PENDING으로 되돌렸습니다. 다음 폴링 주기에 재시도됩니다."
