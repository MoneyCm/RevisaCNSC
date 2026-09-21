from datetime import timedelta

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.encoders import jsonable_encoder
from sqlalchemy import func, select, text
from sqlalchemy.orm import Session

from app.config import settings
from app.db import session
from app.models import Event, EventEvidence, Process, Publication, Source, Stage, now
from app.parser import CATALOG
from app.publications import NOTICES

app = FastAPI(title="Mérito Radar", version="0.1.0")


@app.get("/health")
def health(db: Session = Depends(session)):
    db.execute(text("SELECT 1"))
    sources = db.scalars(select(Source).where(Source.url.in_([CATALOG, NOTICES]))).all()
    review_required = db.scalar(
        select(func.count()).select_from(Publication).where(Publication.process_id.is_(None))
    )
    fresh = now() - timedelta(minutes=settings.monitor_interval_minutes * 2)
    healthy = [
        s for s in sources if s.last_success_at and s.last_success_at > fresh and not s.error
    ]
    return {
        "status": "ok" if len(healthy) == 2 and not review_required else "degraded",
        "database": "ok",
        "review_required": review_required,
        "scheduler": "recent_run" if healthy else "unverified_or_stale",
        "firebase": "not_configured",
        "sources_ok": len(healthy),
        "sources_failed": 2 - len(healthy),
        "last_monitor_run": max(
            (s.last_checked_at for s in sources if s.last_checked_at), default=None
        ),
    }


@app.get("/api/v1/processes")
def processes(
    q: str = Query(default="", max_length=100),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(session),
):
    query = select(Process)
    if q:
        query = query.where(Process.name.icontains(q, autoescape=True))
    return jsonable_encoder(
        db.scalars(
            query.order_by(Process.first_detected_at.desc(), Process.id).offset(offset).limit(limit)
        ).all()
    )


@app.get("/api/v1/processes/{process_id}")
def detail(process_id: str, db: Session = Depends(session)):
    process = db.get(Process, process_id)
    if process is None:
        raise HTTPException(404, "Proceso no encontrado")
    return jsonable_encoder(process)


@app.get("/api/v1/processes/{process_id}/events")
def events(process_id: str, db: Session = Depends(session)):
    if not db.get(Process, process_id):
        raise HTTPException(404, "Proceso no encontrado")
    return jsonable_encoder(
        db.scalars(
            select(Event)
            .where(Event.process_id == process_id)
            .order_by(Event.detected_at.desc())
            .limit(100)
        ).all()
    )


@app.get("/api/v1/alerts")
def alerts(limit: int = Query(default=50, ge=1, le=200), db: Session = Depends(session)):
    return jsonable_encoder(
        db.scalars(select(Event).order_by(Event.detected_at.desc()).limit(limit)).all()
    )


@app.get("/api/v1/sync/status")
def sync_status(db: Session = Depends(session)):
    return health(db)


@app.get("/api/v1/processes/{process_id}/stages")
def stages(process_id: str, db: Session = Depends(session)):
    if not db.get(Process, process_id):
        raise HTTPException(404, "Proceso no encontrado")
    rows = db.scalars(
        select(Stage).where(Stage.process_id == process_id).order_by(Stage.updated_at.desc())
    ).all()
    return [
        {**jsonable_encoder(row), "official_url": db.get(Source, row.source_id).url} for row in rows
    ]


@app.get("/api/v1/processes/{process_id}/publications")
def publications(process_id: str, db: Session = Depends(session)):
    if not db.get(Process, process_id):
        raise HTTPException(404, "Proceso no encontrado")
    rows = db.scalars(
        select(Publication)
        .where(Publication.process_id == process_id)
        .order_by(Publication.published_at.desc())
        .limit(100)
    ).all()
    return [
        {**jsonable_encoder(row), "official_url": db.get(Source, row.source_id).url} for row in rows
    ]


@app.get("/api/v1/events/{event_id}/evidence")
def event_evidence(event_id: str, db: Session = Depends(session)):
    if not db.get(Event, event_id):
        raise HTTPException(404, "Evento no encontrado")
    rows = db.scalars(select(EventEvidence).where(EventEvidence.event_id == event_id)).all()
    return [
        {**jsonable_encoder(row), "official_url": db.get(Source, row.source_id).url} for row in rows
    ]


@app.get("/api/v1/publications/unassigned")
def unassigned_publications(db: Session = Depends(session)):
    rows = db.scalars(
        select(Publication)
        .where(Publication.process_id.is_(None))
        .order_by(Publication.detected_at.desc())
        .limit(100)
    ).all()
    return [
        {**jsonable_encoder(row), "official_url": db.get(Source, row.source_id).url} for row in rows
    ]
