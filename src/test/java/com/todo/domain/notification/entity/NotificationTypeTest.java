package com.todo.domain.notification.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @ParameterizedTest
    @MethodSource("typeAndExpectedReferenceType")
    void 모든_NotificationType은_정확한_ReferenceType으로_매핑된다(NotificationType type, ReferenceType expected) {
        assertThat(type.referenceType()).isEqualTo(expected);
    }

    private static Stream<Arguments> typeAndExpectedReferenceType() {
        return Stream.of(
                Arguments.of(NotificationType.CHAT_MESSAGE, ReferenceType.CHAT),
                Arguments.of(NotificationType.TODO_CREATED, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_ASSIGNED, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_UNASSIGNED, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_SUBMITTED, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_DEADLINE_APPROACHING, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_WORK_ITEM_EXPIRED, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_REACTION_ADDED, ReferenceType.TODO),
                Arguments.of(NotificationType.TODO_ALL_COMPLETED, ReferenceType.TODO),
                Arguments.of(NotificationType.TEAM_MEMBER_JOINED, ReferenceType.TEAM),
                Arguments.of(NotificationType.TEAM_MEMBER_LEFT, ReferenceType.TEAM),
                Arguments.of(NotificationType.TEAM_MEMBER_REMOVED, ReferenceType.TEAM),
                Arguments.of(NotificationType.TEAM_LEADER_CHANGED, ReferenceType.TEAM),
                Arguments.of(NotificationType.NEW_DEVICE_LOGIN, ReferenceType.NONE),
                Arguments.of(NotificationType.PASSWORD_CHANGED, ReferenceType.NONE),
                Arguments.of(NotificationType.AVAILABILITY_POLL_CREATED, ReferenceType.AVAILABILITY_POLL)
        );
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void referenceType은_null이_아니다(NotificationType type) {
        assertThat(type.referenceType()).isNotNull();
    }
}
