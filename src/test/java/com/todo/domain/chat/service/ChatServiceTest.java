package com.todo.domain.chat.service;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.response.ChatMessagePageResponse;
import com.todo.domain.chat.dto.response.ChatMessageResponse;
import com.todo.domain.chat.entity.ChatMessage;
import com.todo.domain.chat.repository.ChatMessageRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks
    private ChatService chatService;

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private TodoRepository todoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Test
    void 메시지_저장_성공() {
        User sender = userWithId(1L);
        Todo todo = todoWithId(10L, teamWithId(100L), sender);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(sender));
        given(todoRepository.findById(10L)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
        given(chatMessageRepository.save(any(ChatMessage.class))).willAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 1000L);
            return message;
        });

        ChatMessageResponse response = chatService.saveMessage(10L, "user1", new ChatMessageRequest("안녕"));

        assertThat(response.messageId()).isEqualTo(1000L);
        assertThat(response.senderId()).isEqualTo(1L);
        assertThat(response.senderNickname()).isEqualTo("닉네임1");
        assertThat(response.content()).isEqualTo("안녕");
    }

    @Test
    void 메시지_저장은_사용자가_없으면_401_예외를_던진다() {
        given(userRepository.findByLoginId("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.saveMessage(10L, "unknown", new ChatMessageRequest("안녕")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용자를 찾을 수 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 메시지_저장은_투두가_없으면_404_예외를_던진다() {
        User sender = userWithId(1L);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(sender));
        given(todoRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.saveMessage(10L, "user1", new ChatMessageRequest("안녕")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 투두입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 메시지_저장은_팀원이_아니면_403_예외를_던진다() {
        User sender = userWithId(1L);
        Todo todo = todoWithId(10L, teamWithId(100L), sender);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(sender));
        given(todoRepository.findById(10L)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> chatService.saveMessage(10L, "user1", new ChatMessageRequest("안녕")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅에 참여할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        then(chatMessageRepository).should(never()).save(any());
    }

    @Test
    void 메시지_목록은_커서가_없으면_최신_메시지를_조회한다() {
        User user = userWithId(1L);
        Todo todo = todoWithId(10L, teamWithId(100L), user);
        ChatMessage first = messageWithId(100L, todo, user, "첫번째");
        ChatMessage second = messageWithId(99L, todo, user, "두번째");
        ChatMessage extra = messageWithId(98L, todo, user, "추가");
        givenReadableTodo(user, todo);
        given(chatMessageRepository.findLatestMessages(10L, PageRequest.of(0, 3)))
                .willReturn(List.of(first, second, extra));

        ChatMessagePageResponse response = chatService.getMessages(10L, "user1", null, 2);

        assertThat(response.messages()).extracting(ChatMessageResponse::messageId).containsExactly(100L, 99L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(99L);
    }

    @Test
    void 메시지_목록은_커서가_있으면_커서_이전_메시지를_조회한다() {
        User user = userWithId(1L);
        Todo todo = todoWithId(10L, teamWithId(100L), user);
        ChatMessage message = messageWithId(90L, todo, user, "이전");
        givenReadableTodo(user, todo);
        given(chatMessageRepository.findMessagesByCursor(10L, 99L, PageRequest.of(0, 3)))
                .willReturn(List.of(message));

        ChatMessagePageResponse response = chatService.getMessages(10L, "user1", 99L, 2);

        assertThat(response.messages()).extracting(ChatMessageResponse::messageId).containsExactly(90L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursorId()).isNull();
    }

    @Test
    void 메시지_목록은_팀원이_아니면_403_예외를_던진다() {
        User user = userWithId(1L);
        Todo todo = todoWithId(10L, teamWithId(100L), user);
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoRepository.findById(10L)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(false);

        assertThatThrownBy(() -> chatService.getMessages(10L, "user1", null, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("채팅 내역을 조회할 권한이 없습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private void givenReadableTodo(User user, Todo todo) {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(todoRepository.findById(10L)).willReturn(Optional.of(todo));
        given(teamMemberRepository.existsByTeamIdAndUserId(100L, 1L)).willReturn(true);
    }

    private User userWithId(Long id) {
        User user = User.create("user" + id, "encoded", "닉네임" + id, "profiles/" + id + ".png");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Team teamWithId(Long id) {
        Team team = Team.create("팀", null, "ABCDEFGH");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private Todo todoWithId(Long id, Team team, User creator) {
        Todo todo = Todo.create(team, creator, "투두", null, LocalDateTime.of(2026, 6, 4, 12, 0));
        ReflectionTestUtils.setField(todo, "id", id);
        return todo;
    }

    private ChatMessage messageWithId(Long id, Todo todo, User sender, String content) {
        ChatMessage message = ChatMessage.create(todo, sender, content);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
