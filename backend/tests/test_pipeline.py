from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, select

from app.api import app
from app.db import session
from app.ingest import ingest
from app.models import Event, Process, Snapshot, Source
from app.parser import ParseError

HTML = Path(__file__).with_name("fixtures").joinpath("catalog.html").read_text(encoding="utf-8")
ADDED = '<div class="views-field-name"><a href="/convocatorias/test-only">Proceso sintético sólo en test</a></div>'


def count(db, model):
    return db.scalar(select(func.count()).select_from(model))


def test_real_pipeline_baseline_repeat_discovery_and_api(db):
    first = ingest(db, HTML)
    db.flush()
    assert first["baseline"]
    assert count(db, Event) == 0
    assert ingest(db, HTML)["events_created"] == 0
    db.flush()
    assert count(db, Snapshot) == 1
    updated = HTML.replace("</main>", ADDED + "</main>")
    assert ingest(db, updated)["events_created"] == 1
    db.flush()
    assert ingest(db, updated)["events_created"] == 0
    db.flush()
    event = db.scalar(select(Event))
    assert event.event_type == "PROCESS_DISCOVERED"
    assert event.priority == "CRITICAL"
    assert event.evidence["old"] is None
    assert not event.notification_sent
    app.dependency_overrides[session] = lambda: db
    try:
        with TestClient(app) as client:
            response = client.get("/api/v1/processes?q=Territorial%2012")
            assert response.status_code == 200
            process = response.json()[0]
            assert process["status"] == "UNKNOWN"
            assert (
                client.get(f"/api/v1/processes/{process['id']}")
                .json()["official_url"]
                .startswith("https://www.cnsc.gov.co/")
            )
            assert client.get("/api/v1/processes/missing").status_code == 404
            assert client.get("/api/v1/processes?limit=9999").status_code == 422
            assert client.get("/health").json()["database"] == "ok"
    finally:
        app.dependency_overrides.clear()


def test_parser_failure_preserves_previous_data(db):
    ingest(db, HTML)
    db.flush()
    before = count(db, Process)
    with pytest.raises(ParseError):
        ingest(db, "<html>Error</html>")
    assert count(db, Process) == before
    assert count(db, Event) == 0


def test_missing_process_is_not_finished(db):
    ingest(db, HTML.replace("</main>", ADDED + "</main>"))
    db.flush()
    ingest(db, HTML)
    db.flush()
    process = db.scalar(select(Process).where(Process.slug == "test-only"))
    assert process.status == "UNKNOWN"


def test_http_failure_preserves_state(db, monkeypatch):
    from contextlib import contextmanager

    import httpx

    from app import monitor

    ingest(db, HTML)
    db.flush()
    before = count(db, Process)

    @contextmanager
    def begin():
        yield db

    class Factory:
        def __call__(self):
            return begin()

    factory = Factory()
    factory.begin = begin
    monkeypatch.setattr(monitor, "Session", factory)

    def fail(self, *args):
        raise httpx.ConnectError("offline")

    monkeypatch.setattr(monitor.OfficialClient, "fetch", fail)
    with pytest.raises(httpx.ConnectError):
        monitor.run()
    db.flush()
    assert count(db, Process) == before
    assert count(db, Event) == 0
    assert db.scalar(select(Source)).error == "ConnectError"


def test_not_modified_uses_snapshot(db, monkeypatch):
    from contextlib import contextmanager

    from app import monitor

    ingest(db, HTML)
    db.flush()

    @contextmanager
    def begin():
        yield db

    class Factory:
        def __call__(self):
            return begin()

    factory = Factory()
    factory.begin = begin
    monkeypatch.setattr(monitor, "Session", factory)
    from zoneinfo import ZoneInfo

    from app.models import now

    source = db.scalar(select(Source))
    source.etag = '"test-etag"'
    source.last_success_at = now().astimezone(ZoneInfo("America/Bogota"))

    def cached(self, url, headers):
        assert headers["If-None-Match"] == '"test-etag"'
        return 304, {}, ""

    monkeypatch.setattr(monitor.OfficialClient, "fetch", cached)
    assert monitor.run()["cached"]
    db.flush()
    assert count(db, Snapshot) == 1
    assert count(db, Event) == 0
    assert db.scalar(select(Source)).http_status == 304
