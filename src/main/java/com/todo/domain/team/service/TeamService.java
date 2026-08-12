package com.todo.domain.team.service;

import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.InviteTeamRequest;
import com.todo.domain.team.dto.request.JoinByInviteLinkRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.InviteLinkResponse;
import com.todo.domain.team.dto.response.InviteTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamAchievementResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.dto.response.TeamMemberResponse;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.service.TodoWorkItemLifecycleService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.file.service.FileDeletionOutboxService;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_CODE_RETRY = 5;
    private static final int MAX_INVITE_EMAIL_COUNT = 20;
    private static final long INVITE_LINK_TTL_DAYS = 7;
    private static final int INVITE_LINK_TOKEN_BYTES = 32;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final TeamInviteMailService teamInviteMailService;
    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final TodoReactionRepository todoReactionRepository;
    private final TodoWorkItemLifecycleService todoWorkItemLifecycleService;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final TeamChatReadStatusRepository teamChatReadStatusRepository;
    private final FileDeletionOutboxService fileDeletionOutboxService;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.team-invite-path:/teams/join}")
    private String teamInvitePath;

    /**
     * 이메일 초대 링크(app.frontend-base-url)와 다른 값이다. Universal Links는 링크 도메인과
     * apple-app-site-association을 서빙하는 도메인이 같아야 하는데, 후자는 이 백엔드 자신이
     * 서빙하므로 프론트엔드 도메인을 그대로 재사용할 수 없다. SwaggerConfig가 이미 쓰는
     * app.api-server-url(이 백엔드 자신의 공개 URL)을 그대로 재사용한다.
     */
    @Value("${app.api-server-url:http://localhost:8080}")
    private String apiServerUrl;

    @Value("${app.team-invite-link-path:/invite}")
    private String teamInviteLinkPath;

    @Transactional
    public CreateTeamResponse createTeam(String userId, CreateTeamRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        String inviteCode = generateUniqueInviteCode();
        Team team = teamRepository.save(
                Team.create(request.teamName(), normalizeDescription(request.description()), request.teamImageKey(), inviteCode));
        teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.LEADER));

        return CreateTeamResponse.from(team, user.getId())
                .withImageUrl(fileService.resolveImageUrl(team.getTeamImage()));
    }

    public TeamDetailResponse getTeamDetail(Long teamId, String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }
        List<TeamMember> members = teamMemberRepository.findByTeamIdWithUser(teamId);
        List<TeamMemberResponse> resolvedMembers = members.stream()
                .map(m -> TeamMemberResponse.from(m)
                        .withProfileImageUrl(fileService.resolveImageUrl(m.getUser().getProfileImageUrl())))
                .toList();
        return TeamDetailResponse.from(team, resolvedMembers)
                .withImageUrl(fileService.resolveImageUrl(team.getTeamImage()));
    }

    public TeamAchievementResponse getTeamAchievement(Long teamId, String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }
        return TeamAchievementResponse.from(team);
    }

    public TeamListResponse getMyTeams(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        List<Team> teams = teamMemberRepository.findTeamsByUserId(user.getId());
        List<TeamSummaryResponse> summaries = teams.stream()
                .map(team -> TeamSummaryResponse.from(team)
                        .withImageUrl(fileService.resolveImageUrl(team.getTeamImage())))
                .toList();
        return new TeamListResponse(summaries);
    }

    @Transactional
    public JoinTeamResponse joinTeam(String userId, JoinTeamRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        Team team = teamRepository.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> new BusinessException("유효하지 않은 초대 코드입니다", HttpStatus.NOT_FOUND));

        if (teamMemberRepository.existsByTeamIdAndUserId(team.getId(), user.getId())) {
            throw new BusinessException("이미 참여한 팀입니다", HttpStatus.CONFLICT);
        }

        try {
            teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.MEMBER));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException("이미 참여한 팀입니다", HttpStatus.CONFLICT);
        }
        notifyMemberJoined(team, user);
        return JoinTeamResponse.from(team);
    }

    /**
     * 이메일 초대(inviteTeamMembers)와 같은 권한 기준 — 팀 멤버면 누구나 발급/재조회할 수 있다.
     * 링크가 아직 없거나 만료됐으면 새로 발급하고, 유효하면 그대로 반환한다(매 호출마다 갱신하지 않음).
     */
    @Transactional
    public InviteLinkResponse getOrCreateInviteLink(String userId, Long teamId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN));

        LocalDateTime now = LocalDateTime.now();
        if (!team.hasValidInviteLink(now)) {
            team.updateInviteLink(generateInviteLinkToken(), now.plusDays(INVITE_LINK_TTL_DAYS));
        }

        return InviteLinkResponse.of(buildInviteLinkUrl(team.getInviteLinkToken()), team.getInviteLinkExpiresAt());
    }

    /**
     * inviteCode 기반 joinTeam과 별도 경로다. 이메일 초대·수동 코드 참여는 곧 제거될 예정이라
     * 지금 얹어서 확장하지 않고, 나중에 그 코드를 통째로 지워도 이 경로는 영향받지 않게 한다.
     */
    @Transactional
    public JoinTeamResponse joinTeamByInviteLink(String userId, JoinByInviteLinkRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        Team team = teamRepository.findByInviteLinkToken(request.token())
                .filter(t -> t.hasValidInviteLink(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("유효하지 않거나 만료된 초대 링크입니다", HttpStatus.NOT_FOUND));

        if (teamMemberRepository.existsByTeamIdAndUserId(team.getId(), user.getId())) {
            throw new BusinessException("이미 참여한 팀입니다", HttpStatus.CONFLICT);
        }

        try {
            teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.MEMBER));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new BusinessException("이미 참여한 팀입니다", HttpStatus.CONFLICT);
        }
        notifyMemberJoined(team, user);
        return JoinTeamResponse.from(team);
    }

    private void notifyMemberJoined(Team team, User newMember) {
        List<User> receivers = teamMemberRepository.findByTeamIdExcludingUser(team.getId(), newMember.getId()).stream()
                .map(TeamMember::getUser)
                .toList();
        notificationService.sendAll(
                receivers,
                newMember,
                notificationMessageFactory.teamMemberJoined(),
                team.getId()
        );
    }

    @Transactional
    public InviteTeamResponse inviteTeamMembers(String userId, Long teamId, InviteTeamRequest request) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN));

        List<String> emails = normalizeEmails(request.emails());
        if (emails.isEmpty()) {
            throw new BusinessException("초대할 이메일을 입력해주세요", HttpStatus.BAD_REQUEST);
        }
        if (emails.size() > MAX_INVITE_EMAIL_COUNT) {
            throw new BusinessException("한 번에 최대 20명까지 초대할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        String inviteLink = buildInviteLink(team.getInviteCode());
        teamInviteMailService.sendInvitations(team, inviteLink, emails);

        return InviteTeamResponse.from(team, inviteLink, emails);
    }

    @Transactional
    public void removeMember(String userId, Long teamId, Long targetUserId) {
        User requester = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        TeamMember requesterMember = teamMemberRepository.findByTeamIdAndUserId(teamId, requester.getId())
                .orElseThrow(() -> new BusinessException("소속된 팀이 아닙니다", HttpStatus.NOT_FOUND));

        if (requesterMember.getRole() != TeamMemberRole.LEADER) {
            throw new BusinessException("권한이 없습니다", HttpStatus.FORBIDDEN);
        }
        if (requester.getId().equals(targetUserId)) {
            throw new BusinessException("자신을 강퇴할 수 없습니다. 탈퇴 기능을 이용해주세요.", HttpStatus.BAD_REQUEST);
        }

        TeamMember target = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> new BusinessException("팀 멤버를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        todoWorkItemLifecycleService.handleTeamDeparture(teamId, target.getUser());
        notificationService.send(
                target.getUser(),
                requester,
                notificationMessageFactory.teamMemberRemoved(),
                teamId
        );
        teamMemberRepository.delete(target);
    }

    @Transactional
    public void leaveTeam(String userId, Long teamId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new BusinessException("소속된 팀이 아닙니다", HttpStatus.NOT_FOUND));

        List<TeamMember> others = teamMemberRepository.findByTeamIdExcludingUser(teamId, user.getId());

        if (member.getRole() == TeamMemberRole.LEADER) {
            if (others.isEmpty()) {
                deleteTeamWithAllData(teamId);
                return;
            }

            try {
                others.get(0).updateRole(TeamMemberRole.LEADER);
            } catch (Exception e) {
                throw new BusinessException("권한 이양 중 문제가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            notificationService.send(
                    others.get(0).getUser(),
                    null,
                    notificationMessageFactory.teamLeaderChanged(),
                    teamId
            );
        }

        todoWorkItemLifecycleService.handleTeamDeparture(teamId, user);
        notificationService.sendAll(
                others.stream().map(TeamMember::getUser).toList(),
                user,
                notificationMessageFactory.teamMemberLeft(),
                teamId
        );
        teamMemberRepository.delete(member);
    }

    @Transactional
    public void deleteTeamWithAllData(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        List<Long> todoIds = todoRepository.findIdsByTeamId(teamId);
        enqueueTeamFilesForDeletion(team, todoIds);
        deleteTodosWithAllData(todoIds);
        teamChatReadStatusRepository.deleteByTeamId(teamId);
        teamChatMessageRepository.deleteByTeamId(teamId);
        teamMemberRepository.deleteByTeamId(teamId);
        teamRepository.deleteById(teamId);
    }

    private void enqueueTeamFilesForDeletion(Team team, List<Long> todoIds) {
        List<String> objectKeys = new ArrayList<>();
        objectKeys.add(team.getTeamImage());
        if (!todoIds.isEmpty()) {
            objectKeys.addAll(todoWorkItemRepository.findProofImageKeysByTodoIdIn(todoIds));
            objectKeys.addAll(todoWorkItemRepository.findProofThumbnailKeysByTodoIdIn(todoIds));
        }
        fileDeletionOutboxService.enqueueAll(objectKeys);
    }

    private void deleteTodosWithAllData(List<Long> todoIds) {
        if (todoIds.isEmpty()) {
            return;
        }

        List<Long> workItemIds = todoWorkItemRepository.findIdsByTodoIdIn(todoIds);
        if (!workItemIds.isEmpty()) {
            todoReactionRepository.deleteByTodoWorkItemIdIn(workItemIds);
        }
        todoWorkItemRepository.deleteByTodoIdIn(todoIds);
        todoRepository.deleteByIdIn(todoIds);
    }

    private List<String> normalizeEmails(List<String> emails) {
        if (emails == null) {
            return List.of();
        }

        return emails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String buildInviteLink(String inviteCode) {
        String baseUrl = trimTrailingSlash(frontendBaseUrl);
        String path = teamInvitePath.startsWith("/") ? teamInvitePath : "/" + teamInvitePath;
        return baseUrl + path + "?code=" + inviteCode;
    }

    private String buildInviteLinkUrl(String token) {
        String baseUrl = trimTrailingSlash(apiServerUrl);
        String path = teamInviteLinkPath.startsWith("/") ? teamInviteLinkPath : "/" + teamInviteLinkPath;
        return baseUrl + path + "?token=" + token;
    }

    /**
     * inviteCode(8자, ReauthToken 이전 세대 패턴)와 달리 고엔트로피 랜덤값이라
     * 사전 중복 체크 없이 발급한다 (ReauthToken/PasswordResetToken과 동일한 근거).
     */
    private String generateInviteLinkToken() {
        byte[] bytes = new byte[INVITE_LINK_TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /** 공백만 있는 설명은 저장하지 않는다 — 목록·상세에서 빈 문자열 노출을 막는다. */
    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    private String generateUniqueInviteCode() {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < MAX_INVITE_CODE_RETRY; attempt++) {
            StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                code.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
            }
            String inviteCode = code.toString();
            if (!teamRepository.existsByInviteCode(inviteCode)) {
                return inviteCode;
            }
        }
        throw new BusinessException("초대 코드 생성에 실패했습니다. 잠시 후 다시 시도해주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
