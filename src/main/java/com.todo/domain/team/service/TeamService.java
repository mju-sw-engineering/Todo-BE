package com.todo.domain.team.service;

import com.todo.domain.chat.repository.ChatMessageRepository;
import com.todo.domain.evaluation.repository.DailyEvaluationRepository;
import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.InviteTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.request.UpdateTeamPersonaRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.InviteTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.dto.response.TeamMemberResponse;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.dto.response.UpdateTeamPersonaResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final TeamInviteMailService teamInviteMailService;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.team-invite-path:/teams/join}")
    private String teamInvitePath;

    @Transactional
    public CreateTeamResponse createTeam(String loginId, CreateTeamRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        String inviteCode = generateUniqueInviteCode();
        Team team = teamRepository.save(Team.create(request.teamName(), request.teamImageKey(), inviteCode, request.aiPersona()));
        teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.LEADER));

        return CreateTeamResponse.from(team, user.getId())
                .withImageUrl(fileService.resolveImageUrl(team.getTeamImage()));
    }

    public TeamDetailResponse getTeamDetail(Long teamId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
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

    public TeamListResponse getMyTeams(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        List<Team> teams = teamMemberRepository.findTeamsByUserId(user.getId());
        List<TeamSummaryResponse> summaries = teams.stream()
                .map(team -> TeamSummaryResponse.from(team)
                        .withImageUrl(fileService.resolveImageUrl(team.getTeamImage())))
                .toList();
        return new TeamListResponse(summaries);
    }

    @Transactional
    public JoinTeamResponse joinTeam(String loginId, JoinTeamRequest request) {
        User user = userRepository.findByLoginId(loginId)
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
        return JoinTeamResponse.from(team);
    }

    public InviteTeamResponse inviteTeamMembers(String loginId, Long teamId, InviteTeamRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN));

        if (member.getRole() != TeamMemberRole.LEADER) {
            throw new BusinessException("팀 초대 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

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
    public UpdateTeamPersonaResponse updateTeamPersona(String loginId, Long teamId, UpdateTeamPersonaRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다", HttpStatus.NOT_FOUND));
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new BusinessException("팀에 접근할 권한이 없습니다", HttpStatus.FORBIDDEN));

        if (member.getRole() != TeamMemberRole.LEADER) {
            throw new BusinessException("팀 설정을 변경할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        team.updateAiPersona(request.aiPersona());
        return UpdateTeamPersonaResponse.from(team);
    }

    @Transactional
    public void removeMember(String loginId, Long teamId, Long targetUserId) {
        User requester = userRepository.findByLoginId(loginId)
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

        teamMemberRepository.delete(target);
    }

    @Transactional
    public void leaveTeam(String loginId, Long teamId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, user.getId())
                .orElseThrow(() -> new BusinessException("소속된 팀이 아닙니다", HttpStatus.NOT_FOUND));

        if (member.getRole() == TeamMemberRole.LEADER) {
            List<TeamMember> others = teamMemberRepository.findByTeamIdExcludingUser(teamId, user.getId());

            if (others.isEmpty()) {
                deleteTeamWithAllData(teamId);
                return;
            }

            try {
                others.get(0).updateRole(TeamMemberRole.LEADER);
            } catch (Exception e) {
                throw new BusinessException("권한 이양 중 문제가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        teamMemberRepository.delete(member);
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

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
