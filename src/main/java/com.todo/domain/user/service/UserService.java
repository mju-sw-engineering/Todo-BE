package com.todo.domain.user.service;

import com.todo.domain.chat.repository.ChatMessageRepository;
import com.todo.domain.team.dto.response.TeamSummaryResponse;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.service.TeamService;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
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
    private final TeamService teamService;
    private final TodoParticipantRepository todoParticipantRepository;
    private final TodoReactionRepository todoReactionRepository;
    private final ChatMessageRepository chatMessageRepository;

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

    @Transactional
    public void deleteUser(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));

        // 1. LEADER인 팀 처리
        List<TeamMember> leaderMemberships = teamMemberRepository.findLeaderMembershipsByUserId(user.getId());
        for (TeamMember leaderMembership : leaderMemberships) {
            Long teamId = leaderMembership.getTeam().getId();
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

        // 2. 채팅 발신자 null 처리 (메시지는 유지)
        chatMessageRepository.clearSenderByUserId(user.getId());

        // 3. 유저 데이터 삭제
        todoReactionRepository.deleteByUserId(user.getId());
        List<Long> userParticipantIds = todoParticipantRepository.findIdsByUserId(user.getId());
        if (!userParticipantIds.isEmpty()) {
            todoReactionRepository.deleteByTodoParticipantIdIn(userParticipantIds);
        }
        todoParticipantRepository.deleteByUserId(user.getId());
        teamMemberRepository.deleteByUserId(user.getId());

        // 4. 유저 삭제
        userRepository.delete(user);
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
