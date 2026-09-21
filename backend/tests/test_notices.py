from datetime import UTC, date, datetime
from html import escape
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, select

from app.api import app
from app.classifier import classify
from app.db import session
from app.event_engine import due_events, record_notice
from app.models import Change, Event, EventEvidence, Process, Snapshot, Stage
from app.parser import ParseError
from app.publications import NOTICES, notice_index, parse_notice

FIXTURES = Path(__file__).with_name("fixtures")


def document(body, title="Fechas de inscripciones", *, stamp="18/09/2026 - 10:00", node=101):
    paragraphs = body if isinstance(body, list) else [body]
    return f'''<main><h1>{escape(title)}</h1><article data-history-node-id="{node}">
    <div class="field--name-created">Vie, {stamp}</div>
    <div class="field--name-body">{"".join("<p>" + escape(p) + "</p>" for p in paragraphs)}</div>
    <div class="field--name-field-convocatoria-asociada"><a href="/convocatorias/test-only">Concurso sólo pruebas</a></div>
    </article></main>'''


def count(db, model):
    return db.scalar(select(func.count()).select_from(model))


@pytest.fixture(autouse=True)
def clock(monkeypatch):
    monkeypatch.setattr("app.event_engine.now", lambda: datetime(2026, 9, 19, 12, tzinfo=UTC))


def test_real_notice_identity_and_dates():
    notice = parse_notice(
        (FIXTURES / "registration.html").read_text(encoding="utf-8"),
        "https://www.cnsc.gov.co/node/65132",
    )
    findings = classify(notice)
    assert {f.event_type for f in findings} == {"PAYMENT_ANNOUNCED", "REGISTRATION_ANNOUNCED"}
    assert all(f.start == date(2026, 5, 25) and f.end == date(2026, 6, 12) for f in findings)
    assert all(f.confidence == "CONFIRMED" and f.population == "GENERAL" for f in findings)
    assert notice.published_at.hour == 15
    assert notice.documents[0]["url"].endswith(".pdf")


def test_payment_mention_is_not_payment_extension():
    notice = parse_notice(
        (FIXTURES / "extension.html").read_text(encoding="utf-8"),
        "https://www.cnsc.gov.co/node/65004",
    )
    findings = classify(notice)
    assert len(findings) == 1
    assert findings[0].kind == "REGISTRATION"
    assert findings[0].end == date(2026, 5, 21)


def test_real_index_pagination():
    urls, next_url = notice_index((FIXTURES / "notices.html").read_text(encoding="utf-8"))
    assert len(urls) == 8
    assert all("/node/" in url for url in urls)
    assert next_url == NOTICES + "?page=1"


@pytest.mark.parametrize(
    "body,title",
    [
        ("Modalidad Abierto General: inscripciones del 19 al 30 de septiembre.", "Inscripciones"),
        (
            "No se abrirán inscripciones del 19 al 30 de septiembre de 2026 en modalidad Abierto General.",
            "No se abrirán inscripciones",
        ),
        (
            "Modalidades abierto y ascenso: inscripciones del 19 al 30 de septiembre de 2026.",
            "Inscripciones",
        ),
        (
            "Modalidad abierto con reserva de discapacidad y abierto general: inscripciones del 19 al 30 de septiembre de 2026.",
            "Inscripciones",
        ),
    ],
)
def test_ambiguity_never_updates_deadlines(db, body, title):
    record_notice(db, document(body, title), "https://www.cnsc.gov.co/node/101")
    assert count(db, Stage) == 0
    assert not any(e.priority == "CRITICAL" for e in db.scalars(select(Event)))


def test_duplicate_sources_consolidate_evidence(db):
    body = "Modalidad Abierto General: recaudo e inscripciones del 19 al 30 de septiembre de 2026."
    record_notice(db, document(body), "https://www.cnsc.gov.co/node/101")
    record_notice(db, document(body, node=102), "https://www.cnsc.gov.co/node/102")
    assert count(db, Stage) == 2
    assert count(db, Event) == 2
    assert count(db, EventEvidence) == 4
    assert record_notice(db, document(body), "https://www.cnsc.gov.co/node/101")["unchanged"]
    assert count(db, Snapshot) == 2


def test_date_change_records_before_after_and_duplicate_amendment(db):
    body = "Modalidad Abierto General: inscripciones del 19 al 21 de septiembre de 2026."
    record_notice(db, document(body), "https://www.cnsc.gov.co/node/101")
    extension = "Modalidad Abierto General: el plazo de inscripciones se amplía hasta el 22 de septiembre de 2026."
    record_notice(
        db,
        document(extension, stamp="19/09/2026 - 06:00", node=102),
        "https://www.cnsc.gov.co/node/102",
    )
    assert db.scalar(select(Stage)).end_date == date(2026, 9, 22)
    change = db.scalar(select(Change))
    assert change.old_value["end_date"] == "2026-09-21"
    assert change.new_value["end_date"] == "2026-09-22"
    assert db.get(Event, change.event_id).event_type == "REGISTRATION_DATE_CHANGED"
    record_notice(
        db,
        document(extension, stamp="19/09/2026 - 06:01", node=103),
        "https://www.cnsc.gov.co/node/103",
    )
    assert count(db, Event) == 2
    assert count(db, Change) == 1


def test_older_publication_cannot_undo_new_date(db):
    newer = "Modalidad Abierto General: inscripciones del 19 al 22 de septiembre de 2026."
    older = newer.replace("22", "21")
    record_notice(
        db, document(newer, stamp="19/09/2026 - 06:00"), "https://www.cnsc.gov.co/node/101"
    )
    record_notice(db, document(older, node=102), "https://www.cnsc.gov.co/node/102")
    assert db.scalar(select(Stage)).end_date == date(2026, 9, 22)
    old_event = db.scalar(select(Event).where(Event.end_date == date(2026, 9, 21)))
    assert not old_event.notify_eligible


def test_modalities_stay_separate(db):
    body = [
        "Modalidad Abierto General: inscripciones del 19 al 22 de septiembre de 2026.",
        "Modalidad Ascenso General: inscripciones del 20 al 25 de septiembre de 2026.",
    ]
    record_notice(db, document(body), "https://www.cnsc.gov.co/node/101")
    assert count(db, Stage) == 2
    assert {s.modality for s in db.scalars(select(Stage))} == {"OPEN", "PROMOTION"}


def test_conflicting_same_scope_needs_review(db):
    body = [
        "Modalidad Abierto General: inscripciones del 19 al 22 de septiembre de 2026.",
        "Modalidad Abierto General: inscripciones del 19 al 25 de septiembre de 2026.",
    ]
    record_notice(db, document(body), "https://www.cnsc.gov.co/node/101")
    assert count(db, Stage) == 0
    assert db.scalar(select(Event)).confidence == "UNCONFIRMED"


def test_opening_and_close_reminder_once(db):
    body = "Modalidad Abierto General: recaudo e inscripciones del 19 al 22 de septiembre de 2026."
    record_notice(db, document(body), "https://www.cnsc.gov.co/node/101", baseline=True)
    assert not any(e.notify_eligible for e in db.scalars(select(Event)))
    assert due_events(db, date(2026, 9, 19)) == 2
    assert due_events(db, date(2026, 9, 19)) == 0
    assert due_events(db, date(2026, 9, 21)) == 2
    assert due_events(db, date(2026, 9, 21)) == 0
    assert due_events(db, date(2026, 9, 23)) == 0


def test_postponement_is_not_process_suspension_and_blocks_reminders(db):
    body = "Modalidad Abierto General: recaudo e inscripciones del 19 al 22 de septiembre de 2026."
    record_notice(db, document(body), "https://www.cnsc.gov.co/node/101")
    title = "Continuidad del proceso y aplazamiento de recaudo e inscripciones"
    record_notice(
        db,
        document("Se aplaza la etapa.", title, stamp="19/09/2026 - 06:00", node=102),
        "https://www.cnsc.gov.co/node/102",
    )
    kinds = {e.event_type for e in db.scalars(select(Event))}
    assert "REGISTRATION_POSTPONED" in kinds
    assert "PROCESS_SUSPENDED" not in kinds
    assert all(s.status == "REVIEW_REQUIRED" for s in db.scalars(select(Stage)))
    assert due_events(db, date(2026, 9, 19)) == 0


@pytest.mark.parametrize(
    "title,expected",
    [
        ("Suspensión del proceso de selección", "PROCESS_SUSPENDED"),
        ("Reanudación del proceso de selección", "PROCESS_RESUMED"),
        ("Publicación de citación a pruebas escritas", "EXAM_CITATION"),
        ("Continuidad del proceso", "NEWS_PUBLISHED"),
    ],
)
def test_explicit_state_and_citation(db, title, expected):
    record_notice(
        db, document("Consulte el aviso oficial.", title), "https://www.cnsc.gov.co/node/101"
    )
    assert db.scalar(select(Event)).event_type == expected


def test_cosmetic_changes_create_no_event(db):
    html = document("Modalidad Abierto General: inscripciones del 19 al 22 de septiembre de 2026.")
    record_notice(db, html, "https://www.cnsc.gov.co/node/101")
    result = record_notice(
        db,
        "<nav>Nuevo menú</nav>" + html.replace("<p>", '<p class="new-style">'),
        "https://www.cnsc.gov.co/node/101",
    )
    assert result["unchanged"]
    assert count(db, Event) == 1


def test_historical_notice_not_notifiable_and_api_trace(db):
    html = (FIXTURES / "registration.html").read_text(encoding="utf-8")
    record_notice(db, html, "https://www.cnsc.gov.co/node/65132")
    assert not any(e.notify_eligible for e in db.scalars(select(Event)))
    process = db.scalar(select(Process))
    event = db.scalar(select(Event))
    app.dependency_overrides[session] = lambda: db
    try:
        with TestClient(app) as client:
            stages = client.get(f"/api/v1/processes/{process.id}/stages").json()
            assert len(stages) == 2
            assert stages[0]["official_url"].endswith("65132")
            publications = client.get(f"/api/v1/processes/{process.id}/publications").json()
            assert len(publications) == 1
            assert publications[0]["documents"]
            assert client.get(f"/api/v1/events/{event.id}/evidence").json()[0]["excerpt"]
    finally:
        app.dependency_overrides.clear()


def test_identity_mismatch_and_empty_are_visible():
    with pytest.raises(ParseError):
        parse_notice(document("Texto"), "https://www.cnsc.gov.co/node/999")
    with pytest.raises(ParseError):
        notice_index("<main>Error</main>")


def test_unassigned_real_notice_is_preserved_without_invented_process(db):
    html = (FIXTURES / "unassigned.html").read_text(encoding="utf-8")
    result = record_notice(db, html, "https://www.cnsc.gov.co/node/67745")
    assert result["review_required"]
    assert count(db, Process) == 0
    assert count(db, Event) == 0
    assert db.scalar(select(Publication)).confidence == "UNCONFIRMED"
    assert count(db, Snapshot) == 1
