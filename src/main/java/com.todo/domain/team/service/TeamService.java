package com.todo.domain.team.service;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.request.JoinTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.JoinTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
import com.todo.domain.team.dto.response.TeamMemberResponse;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_CODE_RETRY = 5;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

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
