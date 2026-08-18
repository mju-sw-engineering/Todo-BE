package com.todo.domain.team.event;

/**
 * 강퇴·팀 탈퇴·회원 탈퇴로 사용자의 팀 멤버십이 사라졌을 때 발행한다.
 * 커밋 후 해당 사용자의 WebSocket 세션을 끊어, 남아 있는 브로커 구독이
 * 재연결 시 SUBSCRIBE 검증을 다시 거치게 만든다.
 */
public record TeamMembershipRevokedEvent(Long userId) {
}
