# 论文 AI Agent 数据库表结构 V2

## 是否需要删除原来的表

不建议直接删除原表，除非这是本地测试库并且没有重要数据。

- 本地开发、没有数据：可以按“全量重建 SQL”删除旧表后重新创建，最干净。
- 已经有用户、论文、分析报告：不要删表，执行“增量迁移 SQL”即可。

本次架构升级后，必须新增的是用户自己的大模型配置字段：

- `user_settings.llm_base_url`
- `user_settings.llm_model`
- `user_settings.llm_api_key`

历史记忆不需要新建表，直接从 `analysis_reports` 和 `analysis_sentences` 按 `user_id` 汇总即可。Agent 执行链路也不需要新表，已经通过 `analysis_reports.agent_trace_json` 保存。

## 增量迁移 SQL

如果你之前已经执行过旧版建表 SQL，就执行下面这一段。

```sql
USE papercheck_agent;

ALTER TABLE user_settings
  ADD COLUMN llm_base_url VARCHAR(255) NULL COMMENT '用户自定义大模型接口地址' AFTER suggestion_count,
  ADD COLUMN llm_model VARCHAR(128) NULL COMMENT '用户自定义大模型名称' AFTER llm_base_url,
  ADD COLUMN llm_api_key VARCHAR(512) NULL COMMENT '用户自定义大模型 API Key' AFTER llm_model;

ALTER TABLE papers
  ADD KEY idx_papers_user_hash (user_id, file_hash);

ALTER TABLE analysis_tasks
  ADD KEY idx_analysis_tasks_user_status_created_at (user_id, status, created_at),
  ADD KEY idx_analysis_tasks_updated_at (updated_at);

ALTER TABLE analysis_reports
  ADD KEY idx_analysis_reports_user_score_created_at (user_id, overall_risk_score, created_at);

ALTER TABLE analysis_sentences
  ADD KEY idx_analysis_sentences_report_score (report_id, risk_score),
  ADD KEY idx_analysis_sentences_task_risk (task_id, risk_level, risk_score);
```

如果上面的索引已经存在，MySQL 会提示重复索引名，这种情况跳过即可。真正必须新增的是 `user_settings` 的三个 `llm_` 字段。

## 全量重建 SQL

如果是本地测试库，可以删除旧表后重新创建。

```sql
CREATE DATABASE IF NOT EXISTS papercheck_agent
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE papercheck_agent;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS analysis_sentences;
DROP TABLE IF EXISTS analysis_reports;
DROP TABLE IF EXISTS analysis_tasks;
DROP TABLE IF EXISTS papers;
DROP TABLE IF EXISTS user_settings;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
  id BIGINT PRIMARY KEY COMMENT '用户ID，雪花算法生成',
  username VARCHAR(32) NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
  nickname VARCHAR(64) NOT NULL COMMENT '用户昵称',
  role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=正常,0=禁用',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除,1=已删除',
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE user_settings (
  id BIGINT PRIMARY KEY COMMENT '设置ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  default_style VARCHAR(32) NOT NULL DEFAULT 'academic' COMMENT '默认分析风格',
  enable_rag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否默认开启 RAG',
  high_risk_threshold INT NOT NULL DEFAULT 75 COMMENT '高风险阈值',
  suggestion_count INT NOT NULL DEFAULT 2 COMMENT '每个高风险句子的改写建议数量',
  llm_base_url VARCHAR(255) NULL COMMENT '用户自定义大模型接口地址',
  llm_model VARCHAR(128) NULL COMMENT '用户自定义大模型名称',
  llm_api_key VARCHAR(512) NULL COMMENT '用户自定义大模型 API Key',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_user_settings_user_id (user_id),
  CONSTRAINT fk_user_settings_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户分析偏好和大模型配置';

CREATE TABLE papers (
  id BIGINT PRIMARY KEY COMMENT '论文文件ID',
  user_id BIGINT NOT NULL COMMENT '上传用户ID',
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  content_type VARCHAR(128) COMMENT '文件 MIME 类型',
  file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
  storage_path VARCHAR(512) NOT NULL COMMENT '文件存储路径',
  file_hash VARCHAR(64) NOT NULL COMMENT '文件哈希，用于重复文件识别',
  created_at DATETIME NOT NULL COMMENT '上传时间',
  KEY idx_papers_user_id_created_at (user_id, created_at),
  KEY idx_papers_file_hash (file_hash),
  KEY idx_papers_user_hash (user_id, file_hash),
  CONSTRAINT fk_papers_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='论文文件表';

CREATE TABLE analysis_tasks (
  id BIGINT PRIMARY KEY COMMENT '分析任务ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  paper_id BIGINT NOT NULL COMMENT '论文文件ID',
  status VARCHAR(32) NOT NULL DEFAULT 'queued' COMMENT '任务状态：queued/processing/done/failed',
  progress INT NOT NULL DEFAULT 0 COMMENT '分析进度：0-100',
  depth VARCHAR(32) NOT NULL DEFAULT 'standard' COMMENT '分析深度',
  style VARCHAR(32) NOT NULL DEFAULT 'academic' COMMENT '分析风格',
  enable_rag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用 RAG',
  suggestion_count INT NOT NULL DEFAULT 2 COMMENT '改写建议数量',
  error_message VARCHAR(500) COMMENT '失败原因',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  started_at DATETIME COMMENT '开始分析时间',
  finished_at DATETIME COMMENT '完成时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  KEY idx_analysis_tasks_user_id_created_at (user_id, created_at),
  KEY idx_analysis_tasks_user_status_created_at (user_id, status, created_at),
  KEY idx_analysis_tasks_status (status),
  KEY idx_analysis_tasks_paper_id (paper_id),
  KEY idx_analysis_tasks_updated_at (updated_at),
  CONSTRAINT fk_analysis_tasks_user_id FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_analysis_tasks_paper_id FOREIGN KEY (paper_id) REFERENCES papers(id),
  CONSTRAINT chk_analysis_tasks_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 分析任务表';

CREATE TABLE analysis_reports (
  id BIGINT PRIMARY KEY COMMENT '报告ID',
  task_id BIGINT NOT NULL COMMENT '分析任务ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  paper_id BIGINT NOT NULL COMMENT '论文文件ID',
  title VARCHAR(255) NOT NULL COMMENT '报告标题',
  overall_risk_score INT NOT NULL COMMENT '整体 AI 风格风险分',
  summary TEXT NOT NULL COMMENT '整体分析摘要',
  agent_trace_json JSON COMMENT 'Agent 工具调用轨迹',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_analysis_reports_task_id (task_id),
  KEY idx_analysis_reports_user_id_created_at (user_id, created_at),
  KEY idx_analysis_reports_user_score_created_at (user_id, overall_risk_score, created_at),
  CONSTRAINT fk_analysis_reports_task_id FOREIGN KEY (task_id) REFERENCES analysis_tasks(id),
  CONSTRAINT fk_analysis_reports_user_id FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_analysis_reports_paper_id FOREIGN KEY (paper_id) REFERENCES papers(id),
  CONSTRAINT chk_analysis_reports_score CHECK (overall_risk_score >= 0 AND overall_risk_score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 分析报告表';

CREATE TABLE analysis_sentences (
  id BIGINT PRIMARY KEY COMMENT '句子分析ID',
  report_id BIGINT NOT NULL COMMENT '报告ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  paragraph_index INT NOT NULL COMMENT '段落序号',
  sentence_index INT NOT NULL COMMENT '句子序号',
  original_text TEXT NOT NULL COMMENT '原句内容',
  risk_score INT NOT NULL COMMENT '句子风险分',
  risk_level VARCHAR(32) NOT NULL COMMENT '风险等级：low/medium/high',
  reasons_json JSON COMMENT '风险原因 JSON',
  advice TEXT COMMENT '修改建议',
  suggested_texts_json JSON COMMENT '改写候选 JSON',
  rag_references_json JSON COMMENT 'RAG 或联网搜索参考 JSON',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  KEY idx_analysis_sentences_report_order (report_id, paragraph_index, sentence_index),
  KEY idx_analysis_sentences_report_score (report_id, risk_score),
  KEY idx_analysis_sentences_task_id (task_id),
  KEY idx_analysis_sentences_task_risk (task_id, risk_level, risk_score),
  KEY idx_analysis_sentences_risk_score (risk_score),
  CONSTRAINT fk_analysis_sentences_report_id FOREIGN KEY (report_id) REFERENCES analysis_reports(id),
  CONSTRAINT fk_analysis_sentences_task_id FOREIGN KEY (task_id) REFERENCES analysis_tasks(id),
  CONSTRAINT chk_analysis_sentences_score CHECK (risk_score >= 0 AND risk_score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='句子级 AI 风格风险分析结果';
```

## 表设计说明

| 表名 | 作用 |
|---|---|
| `users` | 保存登录用户、角色、状态和逻辑删除标记 |
| `user_settings` | 保存用户分析偏好和用户自己的大模型配置 |
| `papers` | 保存上传论文文件的元数据和存储路径 |
| `analysis_tasks` | 保存异步分析任务状态、进度、配置和乐观锁版本 |
| `analysis_reports` | 保存整篇论文的总体风险分、摘要和 Agent 工具轨迹 |
| `analysis_sentences` | 保存句子级风险分、原因、建议、改写结果和 RAG/搜索参考 |

## 当前项目对应关系

- 前端登录后才能填写大模型 Base URL、模型名称、API Key，对应 `user_settings` 的三个 `llm_` 字段。
- Java 后端创建任务后会读取 `user_settings`，通过 RabbitMQ 或 HTTP 直连传给 Python AI 服务。
- Python AI 服务分析完成后写入 `analysis_reports` 和 `analysis_sentences`。
- Java 历史记忆接口从 `analysis_reports` 和 `analysis_sentences` 按当前登录用户聚合，不需要额外新建记忆表。
