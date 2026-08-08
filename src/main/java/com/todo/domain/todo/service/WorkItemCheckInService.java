package com.todo.domain.todo.service;

import com.todo.domain.todo.dto.request.CheckInRequest;
import com.todo.domain.todo.dto.response.CheckInResponse;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemCheckIn;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkItemCheckInService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WorkItemCheckInRepository workItemCheckInRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public CheckInResponse checkIn(String userId, Long workItemId, CheckInRequest request) {
        User user = findAuthenticatedUser(userId);
        TodoWorkItem workItem = findWorkItemWithTeam(workItemId);
        requireTeamMember(workItem, user);

        if (workItem.getAssignee() == null || !workItem.getAssignee().getId().equals(user.getId())) {
            throw new BusinessException("본인에게 배정된 투두만 체크인할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        if (workItem.getStatus() != WorkItemStatus.IN_PROGRESS) {
            throw new BusinessException("진행 중인 투두만 체크인할 수 있습니다.", HttpStatus.CONFLICT);
        }

        LocalDate today = LocalDate.now(KST);
        if (workItemCheckInRepository.existsByWorkItemIdAndUserIdAndCheckDate(workItemId, user.getId(), today)) {
            throw new BusinessException("오늘은 이미 체크인했습니다.", HttpStatus.CONFLICT);
        }

        try {
            WorkItemCheckIn checkIn = workItemCheckInRepository.saveAndFlush(
                    WorkItemCheckIn.create(workItem, user, today, request.memo())
            );
            return CheckInResponse.from(checkIn);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 사전 존재 검사를 함께 통과한 경우 unique 제약이 최종 방어선이다.
            throw new BusinessException("오늘은 이미 체크인했습니다.", HttpStatus.CONFLICT);
        }
    }

    public List<CheckInResponse> getCheckIns(String userId, Long workItemId) {
        User user = findAuthenticatedUser(userId);
        TodoWorkItem workItem = findWorkItemWithTeam(workItemId);
        requireTeamMember(workItem, user);

        return workItemCheckInRepository.findByWorkItemIdWithUser(workItemId).stream()
                .map(CheckInResponse::from)
                .toList();
    }

    private User findAuthenticatedUser(String userId) {
        return userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
    }

    private TodoWorkItem findWorkItemWithTeam(Long workItemId) {
        return todoWorkItemRepository.findByIdWithTodoAndTeam(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));
    }

    private void requireTeamMember(TodoWorkItem workItem, User user) {
        Long teamId = workItem.getTodo().getTeam().getId();
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
    }
}
