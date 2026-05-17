from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "papercheck-ai-agent"
    mysql_url: str = "mysql+pymysql://root:root@localhost:3306/papercheck_agent?charset=utf8mb4"
    redis_url: str = "redis://localhost:6379/0"
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "guest"
    rabbitmq_password: str = "guest"
    rabbitmq_queue: str = "papercheck.analysis.queue"
    rabbitmq_autostart: bool = True
    java_backend_root: Path = Path("D:/桌面文件/agent论文查重后端java")
    llm_base_url: str | None = None
    llm_api_key: str | None = None
    llm_model: str | None = None
    agent_llm_router: bool = False
    agent_memory_limit: int = 5
    vector_rag_enabled: bool = True
    vector_db_path: Path = Path("./data/papercheck_vector_store.sqlite")
    web_search_enabled: bool = False
    web_search_provider: str = "tavily"
    web_search_api_key: str | None = None
    web_search_max_results: int = 3
    web_search_sentence_limit: int = 6

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


@lru_cache
def get_settings() -> Settings:
    return Settings()
