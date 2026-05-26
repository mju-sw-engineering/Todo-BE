package com.todo.domain.user.service;

import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final FileService fileService;

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
