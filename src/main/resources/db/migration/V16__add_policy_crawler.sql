CREATE TABLE policy_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
  project_id BIGINT NOT NULL COMMENT 'Project ID',
  name VARCHAR(128) NOT NULL COMMENT 'Policy source name',
  url VARCHAR(1024) NOT NULL COMMENT 'Policy source URL',
  url_hash CHAR(64) NOT NULL COMMENT 'SHA-256 URL hash',
  crawl_frequency VARCHAR(32) NOT NULL COMMENT 'MANUAL, DAILY, WEEKLY',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED or DISABLED',
  description VARCHAR(1000) NULL COMMENT 'Description',
  last_crawled_at DATETIME NULL COMMENT 'Last crawl time',
  last_error TEXT NULL COMMENT 'Last crawl error',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  created_by BIGINT NULL COMMENT 'Creator user ID',
  updated_by BIGINT NULL COMMENT 'Updater user ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  UNIQUE KEY uk_policy_source_project_url (project_id, url_hash, deleted),
  KEY idx_policy_source_project (project_id, status, deleted),
  KEY idx_policy_source_frequency (crawl_frequency, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Policy/news source configuration';

CREATE TABLE policy_article (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
  project_id BIGINT NOT NULL COMMENT 'Project ID',
  source_id BIGINT NOT NULL COMMENT 'Policy source ID',
  title VARCHAR(256) NOT NULL COMMENT 'Article title',
  url VARCHAR(1024) NOT NULL COMMENT 'Article URL',
  url_hash CHAR(64) NOT NULL COMMENT 'SHA-256 URL hash',
  summary VARCHAR(1000) NULL COMMENT 'Article summary',
  content LONGTEXT NOT NULL COMMENT 'Clean article content',
  publish_date DATE NULL COMMENT 'Publish date',
  category VARCHAR(128) NULL COMMENT 'Category',
  policy_no VARCHAR(128) NULL COMMENT 'Policy number',
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, INDEXING, SUCCESS, FAILED',
  error_message TEXT NULL COMMENT 'Indexing or crawl error',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  created_by BIGINT NULL COMMENT 'Creator user ID',
  updated_by BIGINT NULL COMMENT 'Updater user ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  UNIQUE KEY uk_policy_article_project_url (project_id, url_hash, deleted),
  KEY idx_policy_article_project (project_id, deleted),
  KEY idx_policy_article_source (source_id, deleted),
  KEY idx_policy_article_publish (publish_date),
  KEY idx_policy_article_index_status (index_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Policy/news crawled article';

CREATE TABLE policy_crawl_task (
  task_id BIGINT PRIMARY KEY COMMENT 'generate_task ID',
  project_id BIGINT NOT NULL COMMENT 'Project ID',
  source_id BIGINT NULL COMMENT 'Policy source ID; null means all enabled sources',
  source_name VARCHAR(128) NULL COMMENT 'Source name snapshot',
  status VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT 'Task status',
  progress INT NOT NULL DEFAULT 0 COMMENT 'Progress percent',
  fetched_count INT NOT NULL DEFAULT 0 COMMENT 'Fetched article count',
  indexed_count INT NOT NULL DEFAULT 0 COMMENT 'Indexed article count',
  failed_count INT NOT NULL DEFAULT 0 COMMENT 'Failed article/source count',
  message VARCHAR(1000) NULL COMMENT 'Task message',
  error_message TEXT NULL COMMENT 'Error details',
  started_at DATETIME NULL COMMENT 'Start time',
  finished_at DATETIME NULL COMMENT 'Finish time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  created_by BIGINT NULL COMMENT 'Creator user ID',
  updated_by BIGINT NULL COMMENT 'Updater user ID',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
  KEY idx_policy_crawl_project (project_id, status, deleted),
  KEY idx_policy_crawl_source (source_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Policy/news crawl task status';

INSERT IGNORE INTO permission (permission_code, permission_name, permission_type, created_by, updated_by)
VALUES ('policy:manage', 'Policy Source Management', 'API', 1, 1);

INSERT IGNORE INTO role_permission (role_id, permission_id, created_by, updated_by)
SELECT role_id_value, p.id, 1, 1
FROM permission p
JOIN (
  SELECT 1 AS role_id_value UNION ALL SELECT 2 AS role_id_value
) r ON 1 = 1
WHERE p.permission_code = 'policy:manage' AND p.deleted = 0;
