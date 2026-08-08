package com.todo.domain.user.service;

import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.domain.auth.repository.ReauthTokenRepository;
import com.todo.domain.auth.repository.RefreshTokenRepository;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.repository.NotificationRepository;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.service.TeamService;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.service.TodoWorkItemLifecycleService;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.service.FileDeletionOutboxService;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final FileService fileService;
    private final TeamService teamService;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final TodoReactionRepository todoReactionRepository;
    private final TodoWorkItemLifecycleService todoWorkItemLifecycleService;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final TeamChatReadStatusRepository teamChatReadStatusRepository;
    private final TodoRepository todoRepository;
    private final NotificationRepository notificationRepository;
    private final UserConsentRepository userConsentRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final MailOutboxRepository mailOutboxRepository;
    private final ReauthTokenRepository reauthTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ReauthService reauthService;
    private final FileDeletionOutboxService fileDeletionOutboxService;

    public MyPageResponse getMyPage(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        return buildMyPageResponse(user);
    }

    @Transactional
    public MyPageResponse updateNickname(String userId, UpdateNicknameRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
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
    public void deleteUser(String userId, DeleteUserRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        // 복구 유예기간이 없어 오조작과 토큰 탈취를 되돌릴 수 없으므로 재인증을 요구한다.
        // 토큰은 여기서 소비되며 같은 토큰으로 다시 탈퇴를 시도할 수 없다.
        reauthService.consume(userId, request.reauthToken(), ReauthPurpose.WITHDRAWAL);

        Long id = user.getId();
        String email = user.getEmail();

        enqueueUserFilesForDeletion(user);

        // 1. 팀 정리 — 리더면 권한 이양, 잔여 멤버가 없는 팀만 팀 전체 삭제
        handleLeaderTeams(user);

        // 2. 개인 데이터 삭제. 여기를 빠뜨리면 아래 users 삭제가 FK 위반으로 실패한다.
        notificationRepository.deleteByReceiverId(id);
        teamChatReadStatusRepository.deleteByUserId(id);
        todoReactionRepository.deleteByUserId(id);
        userConsentRepository.deleteByUserId(id);
        reauthTokenRepository.deleteByUserId(id);
        refreshTokenRepository.deleteByUserId(id);
        if (email != null) {
            emailVerificationRepository.deleteByEmail(email);
            mailOutboxRepository.deleteByRecipient(email);
        }

        // 3. 남은 팀의 진행 중 WorkItem을 정리하고, 미배정 알림을 탈퇴 트랜잭션에 함께 저장한다.
        teamMemberRepository.findTeamsByUserId(id)
                .forEach(team -> todoWorkItemLifecycleService.handleTeamDeparture(team.getId(), user));
        todoWorkItemLifecycleService.anonymizeFinishedForWithdrawal(id);

        // 4. 공동 기록 익명화 — 삭제하지 않고 작성자 관계만 끊는다.
        teamChatMessageRepository.clearSenderByUserId(id);
        todoRepository.clearCreatorByUserId(id);
        // 방금 생성한 미배정 알림까지 포함해 actor를 익명화한다.
        notificationRepository.clearActorByUserId(id);

        teamMemberRepository.deleteByUserId(id);

        // 5. 사용자 삭제 후 flush — 정리 누락은 여기서 FK 위반으로 검출된다.
        userRepository.delete(user);
        userRepository.flush();
    }

    private void enqueueUserFilesForDeletion(User user) {
        List<String> objectKeys = new ArrayList<>();
        objectKeys.add(user.getProfileImageUrl());
        objectKeys.addAll(todoWorkItemRepository.findProofImageKeysByAssigneeId(user.getId()));
        objectKeys.addAll(todoWorkItemRepository.findProofThumbnailKeysByAssigneeId(user.getId()));
        fileDeletionOutboxService.enqueueAll(objectKeys);
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
