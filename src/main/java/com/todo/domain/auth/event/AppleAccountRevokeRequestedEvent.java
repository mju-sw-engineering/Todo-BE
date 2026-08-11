package com.todo.domain.auth.event;

public record AppleAccountRevokeRequestedEvent(Long userId, String appleRefreshToken, String appleClientId) {
}
