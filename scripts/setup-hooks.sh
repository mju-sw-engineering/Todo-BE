#!/usr/bin/env bash
set -e

echo "Git hook 설정을 시작합니다..."

git config core.hooksPath .githooks
chmod +x .githooks/*

echo "완료: .githooks 디렉토리를 Git hook 경로로 설정했습니다."
echo ""
echo "설정된 hook 목록:"
for hook in .githooks/*; do
    echo "  - $(basename "$hook")"
done
echo ""
echo "Claude CLI가 PATH에 있으면 커밋 시 메시지가 자동 생성됩니다."
echo "Claude CLI 설치 여부: $(command -v claude &>/dev/null && echo '확인됨' || echo '미설치 (hook은 조용히 스킵됨)')"
