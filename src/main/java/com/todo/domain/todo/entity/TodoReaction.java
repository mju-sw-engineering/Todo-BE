package com.todo.domain.todo.entity;

import com.todo.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "todo_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"todo_work_item_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_work_item_id", nullable = false)
    private TodoWorkItem todoWorkItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TodoReactionType reactionType;

    public static TodoReaction create(TodoWorkItem todoWorkItem, User user, TodoReactionType reactionType) {
        TodoReaction reaction = new TodoReaction();
        reaction.todoWorkItem = todoWorkItem;
        reaction.user = user;
        reaction.reactionType = reactionType;
        return reaction;
    }

    public boolean hasSameType(TodoReactionType reactionType) {
        return this.reactionType == reactionType;
    }

    public void updateType(TodoReactionType reactionType) {
        this.reactionType = reactionType;
    }
}
