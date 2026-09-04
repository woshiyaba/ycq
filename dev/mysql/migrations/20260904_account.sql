-- Additive migration: keep existing users, goods, comments and chats.
USE user_service;
CREATE TABLE IF NOT EXISTS user_profile (
  open_id varchar(100) NOT NULL PRIMARY KEY,
  nick_name varchar(40) NOT NULL,
  avatar_url varchar(2048) NOT NULL,
  bio varchar(300) NOT NULL DEFAULT '',
  gender tinyint NOT NULL DEFAULT 0,
  region varchar(100) NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE goods_service;
CREATE TABLE IF NOT EXISTS user_follow (
  follower_id varchar(100) NOT NULL,
  followed_id varchar(100) NOT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(follower_id, followed_id),
  KEY idx_followed(followed_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_address (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id varchar(100) NOT NULL,
  name varchar(40) NOT NULL,
  phone varchar(30) NOT NULL,
  region varchar(100) NOT NULL,
  detail varchar(200) NOT NULL,
  is_default tinyint NOT NULL DEFAULT 0,
  default_owner varchar(100) GENERATED ALWAYS AS (IF(is_default=1,user_id,NULL)) STORED,
  UNIQUE KEY uq_default_address(default_owner),
  KEY idx_address_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS browse_history (
  user_id varchar(100) NOT NULL,
  kind varchar(20) NOT NULL,
  target_id int NOT NULL,
  visited_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(user_id,kind,target_id),
  KEY idx_history_user(user_id,visited_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='goods_comment' AND COLUMN_NAME='read_at')=0,
  'ALTER TABLE goods_comment ADD COLUMN read_at datetime NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

USE im_service;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat' AND COLUMN_NAME='post_id')=0,
  'ALTER TABLE chat ADD COLUMN post_id int NOT NULL DEFAULT 0, ADD INDEX idx_chat_post(post_id)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat' AND INDEX_NAME='uq_chat_target')=0,
  'ALTER TABLE chat ADD UNIQUE KEY uq_chat_target(u1,u2,goods_id,post_id)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
