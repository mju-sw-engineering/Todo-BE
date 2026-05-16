package com.todo.domain.todo.repository;

import com.todo.domain.todo.entity.ParticipantStatus;

public interface TodoParticipantDetail {
    Long getUserId();
    String getNickname();
    String getProfileImageUrl();
    String getProofImageKey();
    ParticipantStatus getStatus();
}
