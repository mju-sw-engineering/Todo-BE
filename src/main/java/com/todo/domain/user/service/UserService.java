package com.todo.domain.user.service;

import com.todo.domain.auth.entity.AuthProvider;
import com.todo.domain.auth.entity.ReauthPurpose;
import com.todo.domain.auth.repository.EmailVerificationRepository;
import com.todo.domain.auth.repository.PasswordResetTokenRepository;
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
import com.todo.domain.auth.event.AppleAccountRevokeRequestedEvent;
import com.todo.domain.auth.service.ReauthService;
import com.todo.domain.user.dto.request.DeleteUserRequest;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.request.UpdatePasswordRequest;
import com.todo.domain.user.dto.request.UpdateProfileImageRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.dto.response.UserProfileResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.service.FileDeletionOutboxService;
import com.todo.global.mail.repository.MailOutboxRepository;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ReauthService reauthService;
    private final FileDeletionOutboxService fileDeletionOutboxService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    public MyPageResponse getMyPage(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        return buildMyPageResponse(user);
    }

    /**
     * 팀 목록 없이 프로필 필드만 반환한다. 로그인 직후처럼 팀 목록이 필요 없는 시점에
     * getMyPage 대신 사용해 불필요한 팀 조회 쿼리를 없앤다.
     */
    public UserProfileResponse getMyProfile(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        return UserProfileResponse.from(user)
                .withProfileImageUrl(fileService.resolveImageUrl(user.getProfileImageUrl()));
    }

    @Transactional
    public MyPageResponse updateNickname(String userId, UpdateNicknameRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        user.updateNickname(request.nickname());
        return buildMyPageResponse(user);
    }

    /**
     * 로그인 상태에서 현재 비밀번호를 확인하고 새 비밀번호로 바꾼다. Apple 계정은
     * 비밀번호 자체가 없으므로 이 경로를 이용할 수 없다.
     */
    @Transactional
    public void updatePassword(String userId, UpdatePasswordRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new BusinessException("이메일 계정 전용 기능입니다. Apple 계정은 이 방법을 이용할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException("현재 비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 이전 이미지가 있고 새 키와 다를 때만 삭제 예약한다. 같은 키를 다시 보낸 경우까지 지우면
     * 방금 설정한 이미지가 삭제 대상이 되는 사고가 난다.
     */
    @Transactional
    public MyPageResponse updateProfileImage(String userId, UpdateProfileImageRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        fileService.validateProfileImageKey(user.getId(), request.profileImageKey());

        String oldImageKey = user.getProfileImageUrl();
        user.updateProfileImage(request.profileImageKey());

        if (oldImageKey != null && !oldImageKey.equals(request.profileImageKey())) {
            fileDeletionOutboxService.enqueueAll(List.of(oldImageKey));
        }

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

        // Apple revoke는 탈퇴 트랜잭션이 커밋된 뒤에만 시도한다 (AppleAccountRevokeEventListener).
        // 커밋 전에 부르면 Apple 호출이 끝날 때까지 DB 커넥션을 붙잡아두게 되고,
        // 반대로 revoke가 성공한 뒤 탈퇴가 실패하면 Apple 연동만 끊기고 계정은 남는 불일치가 생긴다.
        if (user.getProvider() == AuthProvider.APPLE && user.getAppleRefreshToken() != null) {
            eventPublisher.publishEvent(
                    new AppleAccountRevokeRequestedEvent(user.getId(), user.getAppleRefreshToken(), user.getAppleClientId()));
        }

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
        passwordResetTokenRepository.deleteByUserId(id);
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
