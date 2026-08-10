CREATE TABLE IF NOT EXISTS wx_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    open_id VARCHAR(128) NOT NULL UNIQUE,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(255),
    enabled TINYINT(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_conversation_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    title VARCHAR(120) NOT NULL,
    preview VARCHAR(240) NOT NULL,
    message_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_agent_session_user_session UNIQUE (user_id, session_id)
);

CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_agent_message_session_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id)
        REFERENCES agent_conversation_session (id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_message_session_created
    ON agent_conversation_message (session_id, created_at);

CREATE TABLE IF NOT EXISTS service_point_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL
);

INSERT IGNORE INTO service_point_category (name, created_at) VALUES
    ('停车场', CURRENT_TIMESTAMP), ('文旅服务点', CURRENT_TIMESTAMP), ('卫生间', CURRENT_TIMESTAMP);
