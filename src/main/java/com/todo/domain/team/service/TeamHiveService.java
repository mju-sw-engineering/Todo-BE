package com.todo.domain.team.service;

import com.todo.domain.team.dto.response.TeamHiveResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 팀 벌집 성장 — 팀이 함께 모은 누적 기록 수로 벌집 레벨(1~4)을 계산한다.
 * "기록"은 피드와 같은 활동 기준(투두 생성·제출·체크인)이며,
 * 같은 사람이 같은 날 같은 투두를 여러 방식으로 손대도 1개로 센다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamHiveService {

    /** 레벨 문턱값 — index i 레벨(Lv.i+1)이 시작되는 누적 기록 수 */
    private static final int[] LEVEL_THRESHOLDS = {0, 30, 100, 300};

    /** 팀 생성 시각이 없는 예외적인 데이터를 위한 조회 하한 */
    private static final LocalDate ACTIVITY_EPOCH = LocalDate.of(2000, 1, 1);

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final WorkItemCheckInRepository workItemCheckInRepository;

    public TeamHiveResponse getTeamHive(Long teamId, String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        int totalRecords = countTeamRecords(teamId, activityFrom(team));

        int level = 1;
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalRecords >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
                break;
            }
        }
        Integer nextThreshold = level < LEVEL_THRESHOLDS.length ? LEVEL_THRESHOLDS[level] : null;

        return TeamHiveResponse.of(level, totalRecords, LEVEL_THRESHOLDS[level - 1], nextThreshold);
    }

    /** (사람, 날짜, 투두) 단위로 중복을 제거한 팀 전체 활동 수 */
    private int countTeamRecords(Long teamId, LocalDate from) {
        Set<ActivityKey> records = new HashSet<>();
        todoRepository.findCreationActivityByTeamId(teamId, from.atStartOfDay())
                .forEach(r -> records.add(new ActivityKey(r.getUserId(), r.getOccurredAt().toLocalDate(), r.getTodoId())));
        todoWorkItemRepository.findSubmissionActivityByTeamId(teamId, from.atStartOfDay())
                .forEach(r -> records.add(new ActivityKey(r.getUserId(), r.getOccurredAt().toLocalDate(), r.getTodoId())));
        workItemCheckInRepository.findActivityByTeamId(teamId, from)
                .forEach(r -> records.add(new ActivityKey(r.getUserId(), r.getOccurredOn(), r.getTodoId())));
        return records.size();
    }

    private LocalDate activityFrom(Team team) {
        return team.getCreatedAt() != null ? team.getCreatedAt().toLocalDate() : ACTIVITY_EPOCH;
    }

    private record ActivityKey(Long userId, LocalDate date, Long todoId) {
    }
}
