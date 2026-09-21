from datetime import UTC, date, datetime
from uuid import uuid4

from sqlalchemy import JSON, Date, DateTime, ForeignKey, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


def now():
    return datetime.now(UTC)


def identifier():
    return str(uuid4())


class Base(DeclarativeBase):
    pass


class Process(Base):
    __tablename__ = "processes"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    slug: Mapped[str] = mapped_column(String(500), unique=True)
    name: Mapped[str] = mapped_column(Text)
    official_url: Mapped[str] = mapped_column(Text, unique=True)
    category: Mapped[str] = mapped_column(String(40), default="IN_DEVELOPMENT")
    status: Mapped[str] = mapped_column(String(40), default="UNKNOWN")
    confidence: Mapped[str] = mapped_column(String(20), default="UNCONFIRMED")
    first_detected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    last_checked_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Source(Base):
    __tablename__ = "sources"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    url: Mapped[str] = mapped_column(Text, unique=True)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_success_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    http_status: Mapped[int | None]
    error: Mapped[str | None] = mapped_column(String(200))
    content_hash: Mapped[str | None] = mapped_column(String(64))
    etag: Mapped[str | None] = mapped_column(Text)
    last_modified: Mapped[str | None] = mapped_column(Text)


class Snapshot(Base):
    __tablename__ = "snapshots"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    source_id: Mapped[str] = mapped_column(ForeignKey("sources.id"), index=True)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    normalized_content: Mapped[list] = mapped_column(JSON)
    content_hash: Mapped[str] = mapped_column(String(64))


class Event(Base):
    __tablename__ = "events"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    process_id: Mapped[str] = mapped_column(ForeignKey("processes.id"), index=True)
    source_id: Mapped[str] = mapped_column(ForeignKey("sources.id"))
    event_type: Mapped[str] = mapped_column(String(60))
    priority: Mapped[str] = mapped_column(String(20))
    title: Mapped[str] = mapped_column(Text)
    fingerprint: Mapped[str] = mapped_column(String(64), unique=True)
    evidence: Mapped[dict] = mapped_column(JSON)
    detected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    notification_sent: Mapped[bool] = mapped_column(default=False)
    confidence: Mapped[str] = mapped_column(
        String(20), default="CONFIRMED", server_default="CONFIRMED"
    )
    notify_eligible: Mapped[bool] = mapped_column(default=False, server_default="false")
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    start_date: Mapped[date | None] = mapped_column(Date)
    end_date: Mapped[date | None] = mapped_column(Date)
    modality: Mapped[str | None] = mapped_column(String(30))
    population: Mapped[str | None] = mapped_column(String(30))


class Publication(Base):
    __tablename__ = "publications"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    source_id: Mapped[str] = mapped_column(ForeignKey("sources.id"), unique=True)
    process_id: Mapped[str | None] = mapped_column(ForeignKey("processes.id"), index=True)
    title: Mapped[str] = mapped_column(Text)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    paragraphs: Mapped[list] = mapped_column(JSON)
    documents: Mapped[list] = mapped_column(JSON)
    detected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    confidence: Mapped[str] = mapped_column(String(20))


class Stage(Base):
    __tablename__ = "stages"
    __table_args__ = (UniqueConstraint("process_id", "kind", "modality", "population"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    process_id: Mapped[str] = mapped_column(ForeignKey("processes.id"), index=True)
    kind: Mapped[str] = mapped_column(String(30))
    modality: Mapped[str] = mapped_column(String(30))
    population: Mapped[str] = mapped_column(String(30))
    start_date: Mapped[date | None] = mapped_column(Date)
    end_date: Mapped[date | None] = mapped_column(Date)
    status: Mapped[str] = mapped_column(String(30), default="SCHEDULED")
    confidence: Mapped[str] = mapped_column(String(20))
    raw_text: Mapped[str] = mapped_column(Text)
    timezone: Mapped[str] = mapped_column(String(40), default="America/Bogota")
    source_id: Mapped[str] = mapped_column(ForeignKey("sources.id"))
    event_id: Mapped[str | None] = mapped_column(ForeignKey("events.id"))
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revision: Mapped[int] = mapped_column(default=1)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class EventEvidence(Base):
    __tablename__ = "event_evidence"
    __table_args__ = (UniqueConstraint("event_id", "source_id", "content_hash"),)
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    event_id: Mapped[str] = mapped_column(ForeignKey("events.id"), index=True)
    source_id: Mapped[str] = mapped_column(ForeignKey("sources.id"))
    content_hash: Mapped[str] = mapped_column(String(64))
    excerpt: Mapped[str] = mapped_column(Text)
    captured_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class Change(Base):
    __tablename__ = "changes"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=identifier)
    process_id: Mapped[str] = mapped_column(ForeignKey("processes.id"), index=True)
    source_id: Mapped[str] = mapped_column(ForeignKey("sources.id"))
    event_id: Mapped[str] = mapped_column(ForeignKey("events.id"))
    old_value: Mapped[dict | None] = mapped_column(JSON)
    new_value: Mapped[dict] = mapped_column(JSON)
    detected_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
