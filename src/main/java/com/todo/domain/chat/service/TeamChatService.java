package com.todo.domain.chat.service;

import com.todo.domain.chat.dto.request.ChatMessageRequest;
import com.todo.domain.chat.dto.request.MarkAsReadRequest;
import com.todo.domain.chat.dto.request.TypingStatusRequest;
import com.todo.domain.chat.dto.response.ChatUnreadCountResponse;
import com.todo.domain.chat.dto.response.TeamChatMessagePageResponse;
import com.todo.domain.chat.dto.response.TeamChatMessageResponse;
import com.todo.domain.chat.dto.response.TypingStatusResponse;
import com.todo.domain.chat.entity.TeamChatMessage;
import com.todo.domain.chat.entity.TeamChatReadStatus;
import com.todo.domain.chat.repository.TeamChatMessageRepository;
import com.todo.domain.chat.repository.TeamChatReadStatusRepository;
import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
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
public class TeamChatService {

    private final TeamChatMessageRepository teamChatMessageRepository;
    private final TeamChatReadStatusRepository teamChatReadStatusRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;

    @Transactional
    public TeamChatMessageResponse saveMessage(Long teamId, String userId, ChatMessageRequest request) {
        User sender = findUser(userId);
        checkTeamMember(teamId, sender.getId());
        Team team = teamReference(teamId);

        TeamChatMessage message = teamChatMessageRepository.save(
                TeamChatMessage.create(team, sender, request.content())
        );

        pushChatNotifications(teamId, sender, request.content());

        return TeamChatMessageResponse.from(message);
    }

    public TeamChatMessagePageResponse getMessages(Long teamId, String userId, Long cursorId, int size) {
        User user = findUser(userId);
        checkTeamMember(teamId, user.getId());

        List<TeamChatMessage> messages = fetchMessages(teamId, cursorId, size + 1);

        boolean hasNext = messages.size() > size;
        List<TeamChatMessage> result = hasNext ? messages.subList(0, size) : messages;
        Long nextCursorId = hasNext ? result.get(result.size() - 1).getId() : null;

        return new TeamChatMessagePageResponse(
                result.stream().map(TeamChatMessageResponse::from).toList(),
                hasNext,
                nextCursorId
        );
    }

    public TypingStatusResponse handleTyping(Long teamId, String userId, TypingStatusRequest request) {
        User user = findUser(userId);
        checkTeamMember(teamId, user.getId());
        return TypingStatusResponse.of(user, request.isTyping());
    }

    @Transactional
    public void markAsRead(Long teamId, String userId, MarkAsReadRequest request) {
        User user = findUser(userId);
        checkTeamMember(teamId, user.getId());
        Team team = teamReference(teamId);

        TeamChatReadStatus readStatus = teamChatReadStatusRepository
                .findByUserIdAndTeamId(user.getId(), teamId)
                .orElseGet(() -> teamChatReadStatusRepository.save(TeamChatReadStatus.create(team, user)));

        readStatus.updateLastReadMessageId(request.lastReadMessageId());
    }

    public ChatUnreadCountResponse getUnreadCount(Long teamId, String userId) {
        User user = findUser(userId);
        checkTeamMember(teamId, user.getId());

        long unreadCount = teamChatReadStatusRepository.findByUserIdAndTeamId(user.getId(), teamId)
                .map(status -> status.getLastReadMessageId() == null
                        ? teamChatReadStatusRepository.countAllMessages(teamId)
                        : teamChatReadStatusRepository.countUnreadMessages(teamId, status.getLastReadMessageId()))
                .orElseGet(() -> teamChatReadStatusRepository.countAllMessages(teamId));

        return ChatUnreadCountResponse.of(teamId, unreadCount);
    }

    private User findUser(String userId) {
        return userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
    }

    /**
     * checkTeamMember()가 통과했다면 팀이 존재한다는 게 이미 보장되고, 엔티티는 team_id 저장에만 쓰인다.
     * 따라서 SELECT 없이 프록시만 참조한다.
     */
    private Team teamReference(Long teamId) {
        return teamRepository.getReferenceById(teamId);
    }

    private void checkTeamMember(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new BusinessException("채팅에 참여할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
    }

    private List<TeamChatMessage> fetchMessages(Long teamId, Long cursorId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (cursorId == null) {
            return teamChatMessageRepository.findLatestMessages(teamId, pageRequest);
        }
        return teamChatMessageRepository.findMessagesByCursor(teamId, cursorId, pageRequest);
    }

    /**
     * 채팅 알림은 저장하지 않고 WebSocket 푸시만 한다.
     * 팀 단위 상시 채팅방은 메시지 건수가 많아 메시지마다 (팀원 수 - 1)개의 알림 행을 남기면
     * 메시지가 보관 기간 후 삭제돼도 알림만 영구히 쌓인다. 미접속자용 미읽음 개수는
     * team_chat_read_statuses로 계산하므로 알림 행이 필요하지 않다.
     */
    private void pushChatNotifications(Long teamId, User sender, String content) {
        List<TeamMember> receivers = teamMemberRepository.findByTeamIdExcludingUser(teamId, sender.getId());
        notificationService.pushAll(
                receivers.stream().map(TeamMember::getUser).toList(),
                notificationMessageFactory.chatMessage(sender.getNickname(), content),
                teamId
        );
    }
}
