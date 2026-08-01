package com.todo.domain.user.service;

import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.chat.repository.ChatMessageRepository;
import com.todo.domain.chat.repository.ChatReadStatusRepository;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.service.TeamService;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.ratelimit.SimpleRateLimiter;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String WITHDRAWAL_RATE_LIMIT_PREFIX = "withdrawal:password:";
    private static final int WITHDRAWAL_PASSWORD_ATTEMPT_LIMIT = 5;
    private static final Duration WITHDRAWAL_PASSWORD_ATTEMPT_WINDOW = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final FileService fileService;
    private final TeamService teamService;
    private final TodoParticipantRepository todoParticipantRepository;
    private final TodoReactionRepository todoReactionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final TodoRepository todoRepository;
    private final NotificationRepository notificationRepository;
    private final UserConsentRepository userConsentRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final MailOutboxRepository mailOutboxRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpleRateLimiter rateLimiter;

    public MyPageResponse getMyPage(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        return buildMyPageResponse(user);
    }

    @Transactional
    public MyPageResponse updateNickname(String loginId, UpdateNicknameRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        user.updateNickname(request.nickname());
        return buildMyPageResponse(user);
    }

    /**
     * 회원 탈퇴. 개인정보는 실제로 삭제하고 팀 공동 기록은 작성자만 익명화해 보존한다.
     *
     * <p>사용자 FK에는 ON DELETE CASCADE/SET NULL을 두지 않는다. 모든 참조를 앱이 명시적으로
     * 정리하고, 마지막 users 삭제를 flush해 정리를 빠뜨린 참조가 있으면 FK 위반으로 즉시 드러나게 한다.
     */
    @Transactional
    public void deleteUser(String loginId, DeleteUserRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        verifyPassword(user, request.password());

        Long userId = user.getId();
        String email = user.getEmail();

        // 1. 팀 정리 — 리더면 권한 이양, 잔여 멤버가 없는 팀만 팀 전체 삭제
        handleLeaderTeams(user);

        // 2. 개인 데이터 삭제. 여기를 빠뜨리면 아래 users 삭제가 FK 위반으로 실패한다.
        notificationRepository.deleteByReceiverId(userId);
        chatReadStatusRepository.deleteByUserId(userId);
        todoReactionRepository.deleteByUserId(userId);
        userConsentRepository.deleteByUserId(userId);
        if (email != null) {
            emailVerificationRepository.deleteByEmail(email);
            mailOutboxRepository.deleteByRecipient(email);
        }

        // 3. 공동 기록 익명화 — 삭제하지 않고 작성자 관계만 끊는다.
        chatMessageRepository.clearSenderByUserId(userId);
        todoRepository.clearCreatorByUserId(userId);

        // 4. 참가 기록: 완료·실패는 익명화해 남기고, 진행 중 배정만 제거한다.
        anonymizeAndRemoveParticipations(userId);

        teamMemberRepository.deleteByUserId(userId);

        // 5. 사용자 삭제 후 flush — 정리 누락은 여기서 FK 위반으로 검출된다.
        userRepository.delete(user);
        userRepository.flush();
    }

    private void verifyPassword(User user, String rawPassword) {
        if (!rateLimiter.tryAcquire(
                WITHDRAWAL_RATE_LIMIT_PREFIX + user.getId(),
                WITHDRAWAL_PASSWORD_ATTEMPT_LIMIT,
                WITHDRAWAL_PASSWORD_ATTEMPT_WINDOW
        )) {
            throw new BusinessException("잠시 후 다시 시도해 주세요", HttpStatus.TOO_MANY_REQUESTS);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED);
        }
    }

    private void handleLeaderTeams(User user) {
        List<Long> leaderTeamIds = teamMemberRepository.findTeamIdsByUserIdAndRole(user.getId(), TeamMemberRole.LEADER);
        for (Long teamId : leaderTeamIds) {
            List<TeamMember> others = teamMemberRepository.findByTeamIdExcludingUser(teamId, user.getId());

            if (others.isEmpty()) {
                teamService.deleteTeamWithAllData(teamId);
            } else {
                try {
                    others.get(0).updateRole(TeamMemberRole.LEADER);
                } catch (Exception e) {
                    throw new BusinessException("권한 이양 중 문제가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    /**
     * 완료·실패 참가 기록은 팀 달성 이력이므로 상태와 시각만 남기고 익명화한다.
     * 다른 팀원이 그 기록에 남긴 반응도 함께 보존된다.
     *
     * <p>진행 중 배정은 실적이 아니므로 제거하고, 참가자 구성이 바뀐 Todo의 상태를 재평가한다.
     */
    private void anonymizeAndRemoveParticipations(Long userId) {
        List<Long> affectedTodoIds = todoParticipantRepository.findTodoIdsByUserIdAndStatusInProgress(userId);
        List<Long> inProgressParticipantIds = todoParticipantRepository.findInProgressIdsByUserId(userId);

        // 삭제 대상 참가 기록에 달린 반응을 먼저 정리한다(todo_reactions FK는 RESTRICT).
        if (!inProgressParticipantIds.isEmpty()) {
            todoReactionRepository.deleteByTodoParticipantIdIn(inProgressParticipantIds);
        }

        todoParticipantRepository.anonymizeFinishedByUserId(userId);
        todoParticipantRepository.deleteInProgressByUserId(userId);

        reevaluateTodoStatuses(affectedTodoIds);
    }

    /**
     * 참가자가 빠진 Todo의 상태를 제출 경로와 같은 기준으로 재평가한다.
     * 잔여 0명이면 FAIL, 잔여 전원 성공이면 SUCCESS, 그 외에는 IN_PROGRESS를 유지한다.
     */
    private void reevaluateTodoStatuses(List<Long> todoIds) {
        if (todoIds.isEmpty()) {
            return;
        }

        todoRepository.markAsFailWhenNoParticipantsRemain(todoIds);
        todoRepository.markAsSuccessWhenRemainingAllSucceeded(todoIds);
    }

    private MyPageResponse buildMyPageResponse(User user) {
        List<TeamSummaryResponse> teams = teamMemberRepository.findTeamsByUserId(user.getId()).stream()
                .map(this::toTeamSummaryResponse)
                .toList();

        return MyPageResponse.from(user)
                .withProfileImageUrl(fileService.resolveImageUrl(user.getProfileImageUrl()))
                .withTeams(teams);
    }

    private TeamSummaryResponse toTeamSummaryResponse(Team team) {
        return TeamSummaryResponse.from(team)
                .withImageUrl(fileService.resolveImageUrl(team.getTeamImage()));
    }
}
