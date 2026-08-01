package com.todo.domain.user.support;

/**
 * 탈퇴한 사용자의 공동 기록 표시 규칙.
 *
 * <p>탈퇴 시 사용자 계정은 하드 딜리트하지만 팀 공동 기록(Todo, 채팅, 완료된 참가 기록)은
 * 작성자 관계만 null로 익명화해 보존한다. 그 결과 응답 매핑에서 작성자가 null인 행을 만나므로,
 * 표시명과 ID/이미지 노출 규칙을 이 클래스 하나로 통일한다.
 */
public final class WithdrawnUserDisplay {

    public static final String NICKNAME = "탈퇴한 사용자";

    private WithdrawnUserDisplay() {
    }

    /**
     * 닉네임이 없으면(작성자 탈퇴) 익명 표시명을 돌려준다.
     */
    public static String nicknameOrWithdrawn(String nickname) {
        return nickname == null ? NICKNAME : nickname;
    }
}
