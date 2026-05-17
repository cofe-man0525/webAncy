ALTER TABLE user_settings
  ADD COLUMN llm_base_url VARCHAR(255) NULL AFTER suggestion_count,
  ADD COLUMN llm_model VARCHAR(128) NULL AFTER llm_base_url,
  ADD COLUMN llm_api_key VARCHAR(512) NULL AFTER llm_model;
