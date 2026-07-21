-- Flyway baseline.
-- 운영 DB 실제 스키마 덤프(DataGrip Generate DDL, 2026-07-21)와 대조하여 확정했다.
-- FK/unique 제약조건은 덤프와 구조가 일치하며, 제약조건 이름만 가독성을 위해 재작명했다.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(255),
    email VARCHAR(255),
    terms_agreed BIT NOT NULL,
    terms_agreed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_login_id (login_id)
) ENGINE=InnoDB;

CREATE TABLE teams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_name VARCHAR(255) NOT NULL,
    team_image VARCHAR(255),
    invite_code VARCHAR(255) NOT NULL,
    success_count INT NOT NULL,
    consecutive_todo_count INT NOT NULL,
    ai_persona VARCHAR(20) DEFAULT 'ANGEL' NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_teams_invite_code (invite_code)
) ENGINE=InnoDB;

CREATE TABLE team_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('LEADER', 'MEMBER') NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_members_team_id_user_id (team_id, user_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE todos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    deadline DATETIME(6) NOT NULL,
    status ENUM('FAIL', 'IN_PROGRESS', 'SUCCESS') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_todos_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_todos_creator FOREIGN KEY (creator_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE todo_participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    todo_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status ENUM('FAIL', 'IN_PROGRESS', 'SUCCESS') NOT NULL,
    proof_image_key VARCHAR(255),
    proof_thumbnail_key VARCHAR(255),
    submitted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_todo_participants_todo_id_user_id (todo_id, user_id),
    CONSTRAINT fk_todo_participants_todo FOREIGN KEY (todo_id) REFERENCES todos (id),
    CONSTRAINT fk_todo_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE todo_reactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    todo_participant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type ENUM('ANGRY', 'DISLIKE', 'HEART', 'LIKE', 'SURPRISED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_todo_reactions_todo_participant_id_user_id (todo_participant_id, user_id),
    CONSTRAINT fk_todo_reactions_todo_participant FOREIGN KEY (todo_participant_id) REFERENCES todo_participants (id),
    CONSTRAINT fk_todo_reactions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    todo_id BIGINT NOT NULL,
    sender_id BIGINT,
    content VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_todo FOREIGN KEY (todo_id) REFERENCES todos (id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE chat_read_statuses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    todo_id BIGINT NOT NULL,
    last_read_message_id BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_read_statuses_user_id_todo_id (user_id, todo_id),
    CONSTRAINT fk_chat_read_statuses_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_read_statuses_todo FOREIGN KEY (todo_id) REFERENCES todos (id)
) ENGINE=InnoDB;

CREATE TABLE daily_evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    evaluation_date DATE NOT NULL,
    persona ENUM('ANGEL', 'DEVIL') NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_evaluations_team_id_evaluation_date (team_id, evaluation_date),
    CONSTRAINT fk_daily_evaluations_team FOREIGN KEY (team_id) REFERENCES teams (id)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receiver_id BIGINT NOT NULL,
    type ENUM('CHAT_MESSAGE', 'TODO_CREATED') NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    is_read BIT NOT NULL,
    reference_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE email_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL,
    token VARCHAR(255),
    expires_at DATETIME(6) NOT NULL,
    used BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;
