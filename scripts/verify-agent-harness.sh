#!/usr/bin/env bash

set -euo pipefail

repo_root="$(pwd)"

fail() {
    echo "[agent-harness] FAIL: $1" >&2
    exit 1
}

test -d .agents/commands || fail ".agents/commands가 없습니다."
test -L .claude/commands || fail ".claude/commands가 심링크가 아닙니다."
test -L .codex/commands || fail ".codex/commands가 심링크가 아닙니다."
test -e .claude/commands || fail ".claude/commands 심링크가 깨졌습니다."
test -e .codex/commands || fail ".codex/commands 심링크가 깨졌습니다."

test "$(readlink .claude/commands)" = "../.agents/commands" \
    || fail ".claude/commands 대상이 올바르지 않습니다."
test "$(readlink .codex/commands)" = "../.agents/commands" \
    || fail ".codex/commands 대상이 올바르지 않습니다."

grep -qx '@AGENTS.md' CLAUDE.md || fail "CLAUDE.md가 AGENTS.md를 import하지 않습니다."
python3 -m json.tool .claude/settings.json >/dev/null \
    || fail ".claude/settings.json이 유효한 JSON이 아닙니다."

for command in feature plan impl review commit pr merge; do
    test -f ".agents/commands/${command}.md" || fail "${command} 커맨드가 없습니다."
done

if grep -Eq 'gh label create "agent:|--label "<현재 에이전트 라벨>"' \
    .agents/commands/pr.md; then
    fail "PR 커맨드가 AI 에이전트 라벨을 자동 생성하거나 부착합니다."
fi

grep -q 'AI 작업 metadata를 절대 추가하지 않음' .githooks/prepare-commit-msg \
    || fail "자동 커밋 메시지에 AI attribution 금지 규칙이 없습니다."

bash -n .githooks/pre-commit
bash -n .githooks/prepare-commit-msg
bash -n scripts/setup-hooks.sh

temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/todo-agent-harness.XXXXXX")"
trap 'rm -rf "$temp_dir"' EXIT

git -C "$temp_dir" init -q -b main
git -C "$temp_dir" config user.name "Harness Test"
git -C "$temp_dir" config user.email "harness@example.com"
printf 'initial\n' > "$temp_dir/README.md"
git -C "$temp_dir" add README.md
git -C "$temp_dir" commit -q -m "chore: initialize harness test"

printf 'safe\n' > "$temp_dir/safe.txt"
git -C "$temp_dir" add safe.txt
if (cd "$temp_dir" && "$repo_root/.githooks/pre-commit" >/dev/null 2>&1); then
    fail "main 브랜치 커밋 차단이 동작하지 않습니다."
fi

git -C "$temp_dir" switch -q -c chore/harness-test
(cd "$temp_dir" && "$repo_root/.githooks/pre-commit" >/dev/null 2>&1) \
    || fail "작업 브랜치의 안전한 변경이 차단됐습니다."

git -C "$temp_dir" reset -q
printf 'SECRET=value\n' > "$temp_dir/.env"
git -C "$temp_dir" add -f .env
if (cd "$temp_dir" && "$repo_root/.githooks/pre-commit" >/dev/null 2>&1); then
    fail ".env 차단이 동작하지 않습니다."
fi

git -C "$temp_dir" reset -q
printf 'jwt:\n  secret: ${JWT_SECRET}\n' > "$temp_dir/application-prod.yml"
git -C "$temp_dir" add application-prod.yml
(cd "$temp_dir" && "$repo_root/.githooks/pre-commit" >/dev/null 2>&1) \
    || fail "환경변수 플레이스홀더가 차단됐습니다."

printf 'jwt:\n  secret: literal-secret\n' > "$temp_dir/application-prod.yml"
git -C "$temp_dir" add application-prod.yml
if (cd "$temp_dir" && "$repo_root/.githooks/pre-commit" >/dev/null 2>&1); then
    fail "application 설정의 리터럴 시크릿 차단이 동작하지 않습니다."
fi

echo "[agent-harness] PASS"
