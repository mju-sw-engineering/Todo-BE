package com.todo.domain.chat.entity;

import com.todo.domain.todo.entity.Todo;
import com.todo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_read_statuses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "todo_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    private Long lastReadMessageId;

    public static ChatReadStatus create(User user, Todo todo) {
        ChatReadStatus status = new ChatReadStatus();
        status.user = user;
        status.todo = todo;
        return status;
    }

    public void updateLastReadMessageId(Long messageId) {
        if (this.lastReadMessageId == null || messageId > this.lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }
}
