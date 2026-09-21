from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    database_url: str = "postgresql+psycopg://merito:change-me@localhost:5432/merito"
    monitor_interval_minutes: int = Field(default=30, ge=15)
    notice_max_pages: int = Field(default=10, ge=2, le=50)
    user_agent: str = "MeritoRadar/0.1 (public information monitoring)"


settings = Settings()
