package com.todo.domain.user.service;

import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.service.TeamService;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.dto.request.UpdateNicknameRequest;
import com.todo.domain.user.dto.response.MyPageResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private FileService fileService;
    @Mock
    private TeamService teamService;
    @Mock
    private TodoParticipantRepository todoParticipantRepository;
    @Mock
    private TodoReactionRepository todoReactionRepository;
    @Mock
    private TeamChatMessageRepository teamChatMessageRepository;
    @Mock
    private TeamChatReadStatusRepository teamChatReadStatusRepository;
    @Mock
    private TodoRepository todoRepository;

    @Test
    void 마이페이지_조회_성공_소속팀없음() {
        User user = User.create("user1", "encodedPwd", "닉네임", "profiles/user1.png");
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());
        given(fileService.resolveImageUrl("profiles/user1.png")).willReturn("https://example.com/profiles/user1.png");

        MyPageResponse response = userService.getMyPage("user1");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("user1");
        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profiles/user1.png");
        assertThat(response.teams()).isEmpty();
    }

    @Test
    void 마이페이지_조회_성공_소속팀있음() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        Team team = Team.create("스터디 팀", "teams/study.png", "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", 10L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of(team));
        given(fileService.resolveImageUrl("teams/study.png")).willReturn("https://example.com/teams/study.png");

        MyPageResponse response = userService.getMyPage("user1");

        assertThat(response.nickname()).isEqualTo("닉네임");
        assertThat(response.teams()).hasSize(1);
        assertThat(response.teams().get(0).teamId()).isEqualTo(10L);
        assertThat(response.teams().get(0).teamName()).isEqualTo("스터디 팀");
        assertThat(response.teams().get(0).teamImageUrl()).isEqualTo("https://example.com/teams/study.png");
    }

    @Test
    void 닉네임_수정_성공() {
        User user = User.create("user1", "encodedPwd", "기존닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamsByUserId(1L)).willReturn(List.of());

        MyPageResponse response = userService.updateNickname("user1", new UpdateNicknameRequest("새닉네임"));

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    void 마이페이지_조회_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyPage("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 닉네임_수정_실패_존재하지_않는_사용자() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateNickname("unknown", new UpdateNicknameRequest("새닉네임")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("로그인이 필요합니다")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 회원탈퇴_성공_혼자_있는_리더팀은_팀데이터까지_삭제한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of(10L));
        given(teamMemberRepository.findByTeamIdExcludingUser(10L, 1L)).willReturn(List.of());
        given(todoRepository.findIdsByCreatorId(1L)).willReturn(List.of());

        userService.deleteUser("user1");

        verify(teamService).deleteTeamWithAllData(10L);
        verify(teamChatReadStatusRepository).deleteByUserId(1L);
        verify(teamChatMessageRepository).clearSenderByUserId(1L);
        verify(todoReactionRepository).deleteByUserId(1L);
        verify(todoParticipantRepository).deleteByUserId(1L);
        verify(teamMemberRepository).deleteByUserId(1L);
        verify(userRepository).delete(user);
    }

    @Test
    void 회원탈퇴_성공_작성한_투두를_참조_데이터보다_나중에_삭제한다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoRepository.findIdsByCreatorId(1L)).willReturn(List.of(100L, 101L));
        given(todoParticipantRepository.findIdsByTodoIdIn(List.of(100L, 101L))).willReturn(List.of(1000L, 1001L));

        userService.deleteUser("user1");

        var inOrder = inOrder(todoReactionRepository, todoParticipantRepository, todoRepository);
        inOrder.verify(todoReactionRepository).deleteByTodoParticipantIdIn(List.of(1000L, 1001L));
        inOrder.verify(todoParticipantRepository).deleteByTodoIdIn(List.of(100L, 101L));
        inOrder.verify(todoRepository).deleteByIdIn(List.of(100L, 101L));
        verify(teamChatReadStatusRepository).deleteByUserId(1L);
        verify(teamChatMessageRepository).clearSenderByUserId(1L);
        verify(userRepository).delete(user);
    }

    @Test
    void 회원탈퇴_성공_작성한_투두에_참가자가_없으면_리액션_삭제를_건너뛴다() {
        User user = User.create("user1", "encodedPwd", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.findTeamIdsByUserIdAndRole(1L, TeamMemberRole.LEADER)).willReturn(List.of());
        given(todoRepository.findIdsByCreatorId(1L)).willReturn(List.of(100L));
        given(todoParticipantRepository.findIdsByTodoIdIn(List.of(100L))).willReturn(List.of());

        userService.deleteUser("user1");

        verify(todoReactionRepository, never()).deleteByTodoParticipantIdIn(anyList());
        verify(todoParticipantRepository).deleteByTodoIdIn(List.of(100L));
        verify(todoRepository).deleteByIdIn(List.of(100L));
        verify(teamChatReadStatusRepository).deleteByUserId(1L);
        verify(teamChatMessageRepository).clearSenderByUserId(1L);
        verify(userRepository).delete(user);
    }
}
