package com.todo.domain.feed.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 마일스톤 배지 카탈로그. 획득 여부는 저장하지 않고 조회 시점에 활동 기록으로 판정한다.
 * id/label/icon은 프론트 배지 카드와 공유하는 계약이므로 함부로 바꾸지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum BadgeType {

    /** 활동을 하루라도 남기면 획득 */
    FIRST_HONEY("first-honey", "첫 꿀", "drop"),

    /** 최장 연속 활동 7일 이상 */
    STREAK_7("streak-7", "7일 연속", "bee"),

    /** 한 달의 모든 날에 활동한 달 1개 이상 */
    FIRST_FULL_HIVE("first-full-hive", "첫 완주", "hive"),

    /** 최장 연속 활동 30일 이상 */
    STREAK_30("streak-30", "30일 연속", "bee"),

    /** 한 달의 모든 날에 활동한 달 3개 이상 */
    FULL_HIVE_3("full-hive-3", "3개월 완주", "hive"),

    /** 2인 이상 팀에서 팀원 전원이 같은 날 기록을 남긴 적이 있으면 획득 */
    TEAM_ALL_IN("team-all-in", "팀 전원 참여", "drop");

    private final String id;
    private final String label;
    private final String icon;
}
