# MySQL 数据库表设计

数据库名建议：

```sql
CREATE DATABASE IF NOT EXISTS papercheck_agent
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE papercheck_agent;
```

## 完整建表 SQL

```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常,0=禁用',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE user_settings (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  default_style VARCHAR(32) NOT NULL DEFAULT 'academic',
  enable_rag TINYINT(1) NOT NULL DEFAULT 1,
  high_risk_threshold INT NOT NULL DEFAULT 75,
  suggestion_count INT NOT NULL DEFAULT 2,
  llm_base_url VARCHAR(255),
  llm_model VARCHAR(128),
  llm_api_key VARCHAR(512),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_user_settings_user_id (user_id),
  CONSTRAINT fk_user_settings_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户分析偏好';

CREATE TABLE papers (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128),
  file_size BIGINT NOT NULL,
  storage_path VARCHAR(512) NOT NULL,
  file_hash VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_papers_user_id_created_at (user_id, created_at),
  KEY idx_papers_file_hash (file_hash),
  CONSTRAINT fk_papers_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='论文文件表';

CREATE TABLE analysis_tasks (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  paper_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'queued',
  progress INT NOT NULL DEFAULT 0,
  depth VARCHAR(32) NOT NULL DEFAULT 'standard',
  style VARCHAR(32) NOT NULL DEFAULT 'academic',
  enable_rag TINYINT(1) NOT NULL DEFAULT 1,
  suggestion_count INT NOT NULL DEFAULT 2,
  error_message VARCHAR(500),
  created_at DATETIME NOT NULL,
  started_at DATETIME,
  finished_at DATETIME,
  updated_at DATETIME NOT NULL,
  version INT NOT NULL DEFAULT 0,
  KEY idx_analysis_tasks_user_id_created_at (user_id, created_at),
  KEY idx_analysis_tasks_status (status),
  KEY idx_analysis_tasks_paper_id (paper_id),
  CONSTRAINT fk_analysis_tasks_user_id FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_analysis_tasks_paper_id FOREIGN KEY (paper_id) REFERENCES papers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析任务表';

CREATE TABLE analysis_reports (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  paper_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  overall_risk_score INT NOT NULL,
  summary TEXT NOT NULL,
  agent_trace_json JSON,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_analysis_reports_task_id (task_id),
  KEY idx_analysis_reports_user_id_created_at (user_id, created_at),
  CONSTRAINT fk_analysis_reports_task_id FOREIGN KEY (task_id) REFERENCES analysis_tasks(id),
  CONSTRAINT fk_analysis_reports_user_id FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_analysis_reports_paper_id FOREIGN KEY (paper_id) REFERENCES papers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析报告表';

CREATE TABLE analysis_sentences (
  id BIGINT PRIMARY KEY,
  report_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  paragraph_index INT NOT NULL,
  sentence_index INT NOT NULL,
  original_text TEXT NOT NULL,
  risk_score INT NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  reasons_json JSON,
  advice TEXT,
  suggested_texts_json JSON,
  rag_references_json JSON,
  created_at DATETIME NOT NULL,
  KEY idx_analysis_sentences_report_order (report_id, paragraph_index, sentence_index),
  KEY idx_analysis_sentences_task_id (task_id),
  KEY idx_analysis_sentences_risk_score (risk_score),
  CONSTRAINT fk_analysis_sentences_report_id FOREIGN KEY (report_id) REFERENCES analysis_reports(id),
  CONSTRAINT fk_analysis_sentences_task_id FOREIGN KEY (task_id) REFERENCES analysis_tasks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='句子级分析结果';
```

## 表关系说明

| 表名 | 作用 |
|---|---|
| users | 登录用户 |
| user_settings | 用户默认分析偏好 |
| papers | 上传的论文或图片文件 |
| analysis_tasks | 异步分析任务 |
| analysis_reports | 一篇论文对应的总体报告 |
| analysis_sentences | 句子级风险、原因、建议和推荐语句 |

## 并发安全设计

- `analysis_tasks.version` 使用 MyBatis-Plus 乐观锁，避免任务状态被并发覆盖。
- 上传任务时限制单用户最多 3 个 `queued/processing` 任务。
- 报告表对 `task_id` 设置唯一索引，避免同一个任务重复生成报告。
- 历史记录查询始终按 `user_id` 过滤，避免越权访问。
- JWT 鉴权后从当前登录用户中取 `userId`，不信任前端传入的用户 ID。

## 推荐本地 Docker 启动 MySQL

```bash
docker run --name papercheck-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=papercheck_agent \
  -p 3306:3306 \
  -d mysql:8.0
```

然后进入数据库执行上面的建表 SQL。
