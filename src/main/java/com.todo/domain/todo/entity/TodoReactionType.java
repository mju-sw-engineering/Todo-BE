package com.todo.domain.todo.entity;

public enum TodoReactionType {
    LIKE("👍"),
    HEART("❤️"),
    SURPRISED("😮"),
    DISLIKE("👎"),
    ANGRY("😡");

    private final String emoji;

    TodoReactionType(String emoji) {
        this.emoji = emoji;
    }

    public String getEmoji() {
        return emoji;
    }
}
