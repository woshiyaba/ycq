USE goods_service;

CREATE TABLE IF NOT EXISTS content_post (
    id INT NOT NULL AUTO_INCREMENT,
    kind VARCHAR(16) NOT NULL,
    author_id VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    images TEXT NOT NULL,
    region VARCHAR(100) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_content_feed (kind, status, created_at, id),
    KEY idx_content_author (author_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recruitment_job (
    post_id INT NOT NULL,
    work_type VARCHAR(16) NOT NULL,
    industry VARCHAR(40) NOT NULL,
    salary DECIMAL(10,2) NOT NULL DEFAULT 0,
    salary_unit VARCHAR(16) NOT NULL,
    settlement VARCHAR(20) NOT NULL,
    address VARCHAR(200) NOT NULL,
    headcount INT NOT NULL,
    company VARCHAR(100) NOT NULL,
    requirements TEXT NOT NULL,
    benefits TEXT NOT NULL,
    contact_name VARCHAR(40) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    PRIMARY KEY (post_id),
    KEY idx_recruitment_filter (work_type, industry, settlement),
    CONSTRAINT fk_recruitment_post FOREIGN KEY (post_id) REFERENCES content_post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_comment (
    id INT NOT NULL AUTO_INCREMENT,
    post_id INT NOT NULL,
    author_id VARCHAR(32) NOT NULL,
    parent_id INT NOT NULL DEFAULT 0,
    reply_comment_id INT NOT NULL DEFAULT 0,
    recipient_id VARCHAR(32) NOT NULL,
    body TEXT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_content_comments (post_id, deleted, created_at, id),
    KEY idx_content_notification (recipient_id, deleted, read_at, id),
    KEY idx_content_parent (parent_id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES content_post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_reaction (
    post_id INT NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    type VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id, type),
    KEY idx_content_user_reaction (user_id, type, created_at),
    CONSTRAINT fk_reaction_post FOREIGN KEY (post_id) REFERENCES content_post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
