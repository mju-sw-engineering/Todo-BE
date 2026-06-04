package com.todo.domain.chat.service;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.response.ChatMessagePageResponse;
import com.todo.domain.chat.dto.response.ChatMessageResponse;
import com.todo.domain.chat.dto.response.ChatUnreadCountResponse;
import com.todo.domain.chat.entity.ChatMessage;
import com.todo.domain.chat.entity.ChatReadStatus;
import com.todo.domain.chat.repository.ChatMessageRepository;
import com.todo.domain.chat.repository.ChatReadStatusRepository;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional
    public ChatMessageResponse saveMessage(Long todoId, String loginId, ChatMessageRequest request) {
        User sender = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), sender.getId())) {
            throw new BusinessException("채팅에 참여할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        ChatMessage message = chatMessageRepository.save(ChatMessage.create(todo, sender, request.content()));

        return ChatMessageResponse.from(message);
    }

    public ChatMessagePageResponse getMessages(Long todoId, String loginId, Long cursorId, int size) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new BusinessException("채팅 내역을 조회할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        List<ChatMessage> messages = fetchMessages(todoId, cursorId, size + 1);

        boolean hasNext = messages.size() > size;
        List<ChatMessage> result = hasNext ? messages.subList(0, size) : messages;

        Long nextCursorId = hasNext ? result.get(result.size() - 1).getId() : null;

        List<ChatMessageResponse> responses = result.stream()
                .map(ChatMessageResponse::from)
                .toList();

        return new ChatMessagePageResponse(responses, hasNext, nextCursorId);
    }

    @Transactional
    public void markAsRead(Long todoId, String loginId, MarkAsReadRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new BusinessException("채팅에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        ChatReadStatus readStatus = chatReadStatusRepository
                .findByUserIdAndTodoId(user.getId(), todoId)
                .orElseGet(() -> chatReadStatusRepository.save(ChatReadStatus.create(user, todo)));

        readStatus.updateLastReadMessageId(request.lastReadMessageId());
    }

    public ChatUnreadCountResponse getUnreadCount(Long todoId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new BusinessException("채팅에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        long unreadCount = chatReadStatusRepository.findByUserIdAndTodoId(user.getId(), todoId)
                .map(status -> status.getLastReadMessageId() == null
                        ? chatReadStatusRepository.countAllMessages(todoId)
                        : chatReadStatusRepository.countUnreadMessages(todoId, status.getLastReadMessageId()))
                .orElseGet(() -> chatReadStatusRepository.countAllMessages(todoId));

        return ChatUnreadCountResponse.of(todoId, unreadCount);
    }

    private List<ChatMessage> fetchMessages(Long todoId, Long cursorId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (cursorId == null) {
            return chatMessageRepository.findLatestMessages(todoId, pageRequest);
        }
        return chatMessageRepository.findMessagesByCursor(todoId, cursorId, pageRequest);
    }
}
