package com.todo.domain.auth.entity;

/**
 * 재인증 토큰이 승인하는 작업.
 *
 * <p>용도를 토큰에 묶어 탈퇴용으로 발급받은 토큰이 다른 민감한 작업을 승인하지 못하게 한다.
 */
public enum ReauthPurpose {

    WITHDRAWAL
}
