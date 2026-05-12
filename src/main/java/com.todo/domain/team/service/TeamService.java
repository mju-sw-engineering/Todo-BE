package com.todo.domain.team.service;

import com.todo.domain.team.dto.request.CreateTeamRequest;
import com.todo.domain.team.dto.response.CreateTeamResponse;
import com.todo.domain.team.dto.response.TeamDetailResponse;
import com.todo.domain.team.dto.response.TeamListResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_CODE_RETRY = 5;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png");

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

    // self-injection: 파일 I/O 이후 DB 쓰기를 Spring 프록시를 통해 새 트랜잭션으로 실행
    @Lazy
    @Autowired
    private TeamService self;

    public CreateTeamResponse createTeam(String loginId, CreateTeamRequest request, MultipartFile teamImage) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        if (teamImage != null && !teamImage.isEmpty()) {
            validateImageExtension(teamImage);
        }

        // 파일 I/O: DB 트랜잭션 시작 전 처리
        String teamImageUrl = null;
        if (teamImage != null && !teamImage.isEmpty()) {
            teamImageUrl = fileService.saveTeamImage(teamImage);
        }

        // DB 쓰기: 프록시를 통해 새 트랜잭션으로 실행
        return self.persistTeam(user, request.teamName(), teamImageUrl);
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
        return TeamDetailResponse.from(team, members);
    }

    public TeamListResponse getMyTeams(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        List<Team> teams = teamMemberRepository.findTeamsByUserId(user.getId());
        return TeamListResponse.from(teams);
    }

    @Transactional
    public CreateTeamResponse persistTeam(User user, String teamName, String teamImageUrl) {
        String inviteCode = generateUniqueInviteCode();
        Team team = teamRepository.save(Team.create(teamName, teamImageUrl, inviteCode));
        teamMemberRepository.save(TeamMember.create(team, user, TeamMemberRole.LEADER));
        return CreateTeamResponse.from(team, user.getId());
    }

    private void validateImageExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException("지원하지 않는 이미지 형식입니다.", HttpStatus.BAD_REQUEST);
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("지원하지 않는 이미지 형식입니다.", HttpStatus.BAD_REQUEST);
        }
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
