from sqlalchemy import select, text

from app.models import Event, Process, Snapshot, Source, now
from app.parser import CATALOG, digest, parse_catalog


def ingest(db, html: str, url: str = CATALOG) -> dict:
    rows = parse_catalog(html)
    db.execute(text("SELECT pg_advisory_xact_lock(7241901)"))
    source = db.scalar(select(Source).where(Source.url == url))
    if source is None:
        source = Source(url=url)
        db.add(source)
        db.flush()
    baseline = source.content_hash is None
    new_events = 0
    checked = now()
    for row in rows:
        process = db.scalar(select(Process).where(Process.official_url == row["official_url"]))
        if process is None:
            process = Process(**row)
            db.add(process)
            db.flush()
            if not baseline:
                db.add(
                    Event(
                        process_id=process.id,
                        source_id=source.id,
                        event_type="PROCESS_DISCOVERED",
                        priority="CRITICAL",
                        notify_eligible=True,
                        title=f"Nuevo proceso CNSC: {process.name}",
                        fingerprint=digest([process.id, "PROCESS_DISCOVERED"]),
                        evidence={"url": url, "new": row, "old": None, "confidence": "CONFIRMED"},
                    )
                )
                new_events += 1
        elif process.name != row["name"]:
            process.name = row["name"]
            process.updated_at = checked
        process.last_checked_at = checked
    content_hash = digest(rows)
    if content_hash != source.content_hash:
        db.add(Snapshot(source_id=source.id, normalized_content=rows, content_hash=content_hash))
    source.content_hash = content_hash
    source.last_checked_at = checked
    source.last_success_at = checked
    source.http_status = 200
    source.error = None
    return {"processes_checked": len(rows), "events_created": new_events, "baseline": baseline}
