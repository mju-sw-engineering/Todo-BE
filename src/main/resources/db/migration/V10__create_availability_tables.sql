CREATE TABLE availability_polls (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT       NOT NULL,
    created_by  BIGINT,
    title       VARCHAR(100) NOT NULL,
    start_hour  TINYINT      NOT NULL,
    end_hour    TINYINT      NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_ap_team FOREIGN KEY (team_id)    REFERENCES teams(id),
    CONSTRAINT fk_ap_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE availability_poll_dates (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id  BIGINT NOT NULL,
    date     DATE   NOT NULL,
    CONSTRAINT fk_apd_poll FOREIGN KEY (poll_id) REFERENCES availability_polls(id) ON DELETE CASCADE
);

CREATE TABLE availability_slots (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    poll_id    BIGINT  NOT NULL,
    user_id    BIGINT,
    date       DATE    NOT NULL,
    hour       TINYINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_slot      UNIQUE (poll_id, user_id, date, hour),
    CONSTRAINT fk_slot_poll FOREIGN KEY (poll_id)  REFERENCES availability_polls(id) ON DELETE CASCADE,
    CONSTRAINT fk_slot_user FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_slot_poll_date_hour ON availability_slots (poll_id, date, hour);
