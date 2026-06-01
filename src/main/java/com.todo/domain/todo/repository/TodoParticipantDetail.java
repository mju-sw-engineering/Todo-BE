package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.ParticipantStatus;

public interface TodoParticipantDetail {
    Long getTodoParticipantId();
    Long getUserId();
    String getNickname();
    String getProfileImageUrl();
    String getProofImageKey();
    String getProofThumbnailKey();
    ParticipantStatus getStatus();
}
