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

INSERT INTO admin_user (username, password_hash, display_name, enabled, created_at, updated_at)
SELECT 'admin', '$2a$10$IwZ9b12R6T0GiluTfHNCh.XLAh6JuQnPyqsAn9xrjjABxBDZ.Gob2', 'ops-admin', 1,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM admin_user WHERE username = 'admin');

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
        REFERENCES agent_conversation_session (id) ON DELETE CASCADE,
    INDEX idx_agent_message_session_created (session_id, created_at)
);

CREATE TABLE IF NOT EXISTS agent_observation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    message_id BIGINT NOT NULL,
    trace_id VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    agent_name VARCHAR(64) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    model VARCHAR(128),
    llm_input MEDIUMTEXT,
    llm_output MEDIUMTEXT,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    next_decision VARCHAR(64),
    duration_ms BIGINT,
    error_message MEDIUMTEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_agent_observation_event UNIQUE (event_id),
    CONSTRAINT fk_agent_observation_message FOREIGN KEY (message_id)
        REFERENCES agent_conversation_message (id) ON DELETE CASCADE,
    INDEX idx_agent_observation_message_sequence (message_id, sequence_no)
);
