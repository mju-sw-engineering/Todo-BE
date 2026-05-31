package com.todo.domain.chat.entity;

import com.todo.domain.todo.entity.Todo;
import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = true)
    private User sender;

    @Column(nullable = false)
    private String content;

    public static ChatMessage create(Todo todo, User sender, String content) {
        ChatMessage message = new ChatMessage();
        message.todo = todo;
        message.sender = sender;
        message.content = content;
        return message;
    }
}
