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
import com.todo.domain.notification.entity.NotificationType;
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

    @Transactional
    public TeamChatMessageResponse saveMessage(Long teamId, String loginId, ChatMessageRequest request) {
        User sender = findUser(loginId);
        Team team = findTeam(teamId);
        checkTeamMember(teamId, sender.getId());

        TeamChatMessage message = teamChatMessageRepository.save(
                TeamChatMessage.create(team, sender, request.content())
        );

        sendChatNotifications(team, sender, request.content());

        return TeamChatMessageResponse.from(message);
    }

    public TeamChatMessagePageResponse getMessages(Long teamId, String loginId, Long cursorId, int size) {
        User user = findUser(loginId);
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

    public TypingStatusResponse handleTyping(Long teamId, String loginId, TypingStatusRequest request) {
        User user = findUser(loginId);
        checkTeamMember(teamId, user.getId());
        return TypingStatusResponse.of(user, request.isTyping());
    }

    @Transactional
    public void markAsRead(Long teamId, String loginId, MarkAsReadRequest request) {
        User user = findUser(loginId);
        Team team = findTeam(teamId);
        checkTeamMember(teamId, user.getId());

        TeamChatReadStatus readStatus = teamChatReadStatusRepository
                .findByUserIdAndTeamId(user.getId(), teamId)
                .orElseGet(() -> teamChatReadStatusRepository.save(TeamChatReadStatus.create(team, user)));

        readStatus.updateLastReadMessageId(request.lastReadMessageId());
    }

    public ChatUnreadCountResponse getUnreadCount(Long teamId, String loginId) {
        User user = findUser(loginId);
        checkTeamMember(teamId, user.getId());

        long unreadCount = teamChatReadStatusRepository.findByUserIdAndTeamId(user.getId(), teamId)
                .map(status -> status.getLastReadMessageId() == null
                        ? teamChatReadStatusRepository.countAllMessages(teamId)
                        : teamChatReadStatusRepository.countUnreadMessages(teamId, status.getLastReadMessageId()))
                .orElseGet(() -> teamChatReadStatusRepository.countAllMessages(teamId));

        return ChatUnreadCountResponse.of(teamId, unreadCount);
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND));
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

    private void sendChatNotifications(Team team, User sender, String content) {
        List<TeamMember> receivers = teamMemberRepository.findByTeamIdExcludingUser(team.getId(), sender.getId());
        String title = sender.getNickname() + "님이 메시지를 보냈습니다.";
        notificationService.sendAll(
                receivers.stream().map(TeamMember::getUser).toList(),
                NotificationType.CHAT_MESSAGE,
                title,
                content,
                team.getId()
        );
    }
}
