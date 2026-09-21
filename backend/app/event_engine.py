"""Transactional publication changes, semantic deduplication and calendar reminders."""

from datetime import date, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import select, text

from app.classifier import classify, fold
from app.models import (
    Change,
    Event,
    EventEvidence,
    Process,
    Publication,
    Snapshot,
    Source,
    Stage,
    now,
)
from app.parser import digest
from app.publications import parse_notice


def serialized(stage):
    return {
        "start_date": stage.start_date.isoformat() if stage.start_date else None,
        "end_date": stage.end_date.isoformat() if stage.end_date else None,
        "status": stage.status,
    }


def evidence(db, event, source, excerpt):
    content_hash = digest(excerpt)
    existing = db.scalar(
        select(EventEvidence).where(
            EventEvidence.event_id == event.id,
            EventEvidence.source_id == source.id,
            EventEvidence.content_hash == content_hash,
        )
    )
    if existing is None:
        db.add(
            EventEvidence(
                event_id=event.id, source_id=source.id, content_hash=content_hash, excerpt=excerpt
            )
        )


def record_notice(db, html, url, *, baseline=False):
    notice = parse_notice(html, url)
    db.execute(text("SELECT pg_advisory_xact_lock(7241901)"))
    source = db.scalar(select(Source).where(Source.url == notice.url))
    if source is None:
        source = Source(url=notice.url)
        db.add(source)
        db.flush()
    canonical = notice.canonical()
    content_hash = digest(canonical)
    unchanged = source.content_hash == content_hash
    source.last_checked_at = source.last_success_at = now()
    source.http_status = 200
    source.error = None
    if unchanged:
        return {"events_created": 0, "unchanged": True}
    process = db.scalar(select(Process).where(Process.official_url == notice.process_url))
    if process is None and notice.process_url:
        process = Process(
            name=notice.process_name,
            official_url=notice.process_url,
            slug=notice.process_url.rsplit("/", 1)[-1],
            category="UNKNOWN",
        )
        db.add(process)
        db.flush()
    publication = db.scalar(select(Publication).where(Publication.source_id == source.id))
    if publication is None:
        publication = Publication(source_id=source.id, process_id=process.id if process else None)
        db.add(publication)
    publication.title = notice.title
    publication.published_at = notice.published_at
    publication.paragraphs = notice.paragraphs
    publication.documents = notice.documents
    publication.confidence = "CONFIRMED" if notice.published_at and process else "UNCONFIRMED"
    db.add(Snapshot(source_id=source.id, normalized_content=[canonical], content_hash=content_hash))
    source.content_hash = content_hash
    if process is None:
        db.flush()
        return {"events_created": 0, "unchanged": False, "review_required": True}
    created = 0
    today = now().astimezone(ZoneInfo("America/Bogota")).date()
    for finding in classify(notice):
        event_type = finding.event_type
        old, new = None, None
        stage = None
        stale = False
        confidence = finding.confidence
        stage_query = select(Stage).where(
            Stage.process_id == process.id,
            Stage.kind == finding.kind,
            Stage.modality == finding.modality,
            Stage.population == finding.population,
        )
        if (
            finding.kind in {"PAYMENT", "REGISTRATION"}
            and confidence == "CONFIRMED"
            and not finding.postponed
        ):
            stage = db.scalar(stage_query)
            if stage:
                old = serialized(stage)
                stale = notice.published_at < stage.published_at
                conflict = (
                    notice.published_at == stage.published_at and stage.source_id != source.id
                )
                new = {
                    "start_date": finding.start.isoformat() if finding.start else old["start_date"],
                    "end_date": finding.end.isoformat() if finding.end else old["end_date"],
                    "status": "SCHEDULED",
                }
                if new["start_date"] and new["end_date"] and new["start_date"] > new["end_date"]:
                    confidence = "UNCONFIRMED"
                elif conflict and new != old:
                    confidence = "UNCONFIRMED"
                elif new == old and stage.event_id:
                    event = db.get(Event, stage.event_id)
                    evidence(db, event, source, finding.excerpt)
                    if not stale:
                        stage.source_id = source.id
                        stage.published_at = notice.published_at
                    continue
                elif not stale:
                    event_type = finding.kind + "_DATE_CHANGED"
            else:
                new = {
                    "start_date": finding.start.isoformat() if finding.start else None,
                    "end_date": finding.end.isoformat() if finding.end else None,
                    "status": "SCHEDULED",
                }
        priority = "INFO"
        if confidence == "CONFIRMED":
            if finding.kind in {"PAYMENT", "REGISTRATION"} or event_type in {
                "PROCESS_SUSPENDED",
                "PROCESS_RESUMED",
            }:
                priority = "CRITICAL"
            elif event_type in {"EXAM_CITATION", "RESULT_PUBLISHED", "ELIGIBLE_LIST_PUBLISHED"}:
                priority = "IMPORTANT"
        identity = [
            process.id,
            event_type,
            finding.modality,
            finding.population,
            str(finding.start),
            str(finding.end),
            confidence,
        ]
        if event_type.endswith("DATE_CHANGED") and stage:
            identity += [stage.revision + 1, old, new]
        elif finding.start is None and finding.end is None:
            identity += [
                notice.published_at.date().isoformat() if notice.published_at else None,
                ""
                if finding.postponed or event_type in {"PROCESS_SUSPENDED", "PROCESS_RESUMED"}
                else fold(finding.excerpt),
            ]
        fingerprint = digest(identity)
        event = db.scalar(select(Event).where(Event.fingerprint == fingerprint))
        if event is None:
            recent = (
                notice.published_at and now() - timedelta(days=7) <= notice.published_at <= now()
            )
            future_window = bool(
                (finding.end and finding.end >= today) or (finding.start and finding.start >= today)
            )
            recent = bool(
                notice.published_at and notice.published_at <= now() and (recent or future_window)
            )
            expired = finding.end is not None and finding.end < today
            event = Event(
                process_id=process.id,
                source_id=source.id,
                event_type=event_type,
                priority=priority,
                title=notice.title,
                fingerprint=fingerprint,
                confidence=confidence,
                notify_eligible=bool(
                    not baseline
                    and recent
                    and not expired
                    and not stale
                    and confidence == "CONFIRMED"
                ),
                published_at=notice.published_at,
                start_date=finding.start,
                end_date=finding.end,
                modality=finding.modality,
                population=finding.population,
                evidence={
                    "url": notice.url,
                    "excerpt": finding.excerpt,
                    "old": old,
                    "new": new,
                    "confidence": confidence,
                    "baseline": baseline,
                },
            )
            db.add(event)
            db.flush()
            created += 1
        evidence(db, event, source, finding.excerpt)
        if new and confidence == "CONFIRMED" and not stale:
            if stage is None:
                stage = Stage(
                    process_id=process.id,
                    kind=finding.kind,
                    modality=finding.modality,
                    population=finding.population,
                    revision=0,
                )
                db.add(stage)
            stage.start_date = date.fromisoformat(new["start_date"]) if new["start_date"] else None
            stage.end_date = date.fromisoformat(new["end_date"]) if new["end_date"] else None
            stage.status = "SCHEDULED"
            stage.confidence = confidence
            stage.raw_text = finding.excerpt
            stage.source_id = source.id
            stage.published_at = notice.published_at
            stage.event_id = event.id
            stage.revision += 1
            stage.updated_at = now()
            if old and old != new:
                db.add(
                    Change(
                        process_id=process.id,
                        source_id=source.id,
                        event_id=event.id,
                        old_value=old,
                        new_value=new,
                    )
                )
        if finding.postponed or event_type == "PROCESS_SUSPENDED":
            affected = select(Stage).where(Stage.process_id == process.id)
            if finding.postponed:
                affected = affected.where(Stage.kind == finding.kind)
            for current in db.scalars(affected):
                if notice.published_at and notice.published_at >= current.published_at:
                    previous = serialized(current)
                    current.status = "REVIEW_REQUIRED"
                    current.published_at = notice.published_at
                    current.revision += 1
                    db.add(
                        Change(
                            process_id=process.id,
                            source_id=source.id,
                            event_id=event.id,
                            old_value=previous,
                            new_value=serialized(current),
                        )
                    )
    db.flush()
    return {"events_created": created, "unchanged": False}


def due_events(db, today=None):
    """Day-level reminders only; never invent an official closing time."""
    today = today or now().astimezone(ZoneInfo("America/Bogota")).date()
    db.execute(text("SELECT pg_advisory_xact_lock(7241901)"))
    created = 0
    for stage in db.scalars(
        select(Stage).where(Stage.status == "SCHEDULED", Stage.confidence == "CONFIRMED")
    ):
        if stage.start_date == today:
            event_type = stage.kind + "_OPEN"
        elif stage.end_date == today + timedelta(days=1):
            event_type = stage.kind + "_CLOSING_SOON"
        else:
            continue
        fingerprint = digest([stage.id, stage.revision, event_type, today.isoformat()])
        if db.scalar(select(Event).where(Event.fingerprint == fingerprint)):
            continue
        process = db.get(Process, stage.process_id)
        source = db.get(Source, stage.source_id)
        if (
            source.error
            or not source.last_success_at
            or source.last_success_at < now() - timedelta(hours=2)
        ):
            continue
        db.add(
            Event(
                process_id=stage.process_id,
                source_id=stage.source_id,
                event_type=event_type,
                priority="CRITICAL",
                title=process.name,
                fingerprint=fingerprint,
                confidence="CONFIRMED",
                notify_eligible=True,
                start_date=stage.start_date,
                end_date=stage.end_date,
                modality=stage.modality,
                population=stage.population,
                published_at=stage.published_at,
                evidence={
                    "url": source.url,
                    "excerpt": stage.raw_text,
                    "stage_id": stage.id,
                    "timezone": "America/Bogota",
                    "reminder": True,
                },
            )
        )
        created += 1
    db.flush()
    return created
