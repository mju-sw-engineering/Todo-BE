DROP TABLE IF EXISTS chat_read_statuses;
DROP TABLE IF EXISTS chat_messages;

CREATE TABLE team_chat_messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id    BIGINT       NOT NULL,
    sender_id  BIGINT,
    content    TEXT         NOT NULL,
    is_bot     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_tcm_team   FOREIGN KEY (team_id)   REFERENCES teams(id),
    CONSTRAINT fk_tcm_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);

CREATE TABLE team_chat_read_statuses (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id              BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    last_read_message_id BIGINT,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    CONSTRAINT uq_tcrs      UNIQUE (team_id, user_id),
    CONSTRAINT fk_tcrs_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_tcrs_user FOREIGN KEY (user_id) REFERENCES users(id)
);
