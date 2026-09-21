from contextlib import contextmanager

import httpx
import pytest
from sqlalchemy import func, select

from app import notice_monitor
from app.models import Event, Publication, Snapshot, Source
from app.publications import NOTICES


def article(node):
    return f'''<main><h1>Inscripciones modalidad Abierto General</h1>
    <article data-history-node-id="{node}"><div class="field--name-created">18/09/2026 - 10:00</div>
    <div class="field--name-body"><p>Modalidad Abierto General: inscripciones del 4 al 18 de noviembre de 2026.</p></div>
    <div class="field--name-field-convocatoria-asociada"><a href="/convocatorias/test-feed">Concurso sólo test</a></div>
    </article></main>'''


def page(node, next_page=None):
    next_link = (
        f'<nav class="pager"><li class="pager__item--next"><a href="?page={next_page}">Siguiente</a></li></nav>'
        if next_page
        else ""
    )
    return f'<main><h1>CNSC Avisos informativos</h1><div class="view-content"><h2><a href="/node/{node}">Aviso</a></h2></div>{next_link}</main>'


@pytest.fixture
def feed(db, monkeypatch):
    @contextmanager
    def context():
        yield db

    class Sessions:
        def __call__(self):
            return context()

        def begin(self):
            return context()

    class Client:
        replies = {
            NOTICES: page(101, 1),
            NOTICES + "?page=1": page(102),
            "https://www.cnsc.gov.co/node/101": article(101),
            "https://www.cnsc.gov.co/node/102": article(102),
        }

        def fetch(self, url, headers=None):
            value = self.replies[url]
            if isinstance(value, Exception):
                raise value
            return 200, {}, value

        def close(self):
            pass

    monkeypatch.setattr(notice_monitor, "Session", Sessions())
    monkeypatch.setattr(notice_monitor, "OfficialClient", Client)
    return Client.replies


def test_feed_baseline_and_repeat(db, feed):
    first = notice_monitor.run_notices()
    assert first["baseline"]
    assert first["notices_checked"] == 2
    assert first["events_created"] == 1
    assert not db.scalar(select(Event)).notify_eligible
    assert db.scalar(select(func.count()).select_from(Publication)) == 2
    second = notice_monitor.run_notices()
    assert not second["baseline"]
    assert second["events_created"] == 0
    assert db.scalar(select(func.count()).select_from(Snapshot)) == 3


def test_failed_notice_does_not_advance_recovery_point(db, feed):
    notice_monitor.run_notices()
    source = db.scalar(select(Source).where(Source.url == NOTICES))
    before = source.content_hash
    url = "https://www.cnsc.gov.co/node/101"
    feed[url] = httpx.ConnectError("offline")
    with pytest.raises(notice_monitor.CoverageGap):
        notice_monitor.run_notices()
    assert source.content_hash == before
    assert source.error == "CoverageGap"
    assert db.scalar(select(func.count()).select_from(Publication)) == 2
    feed[url] = article(101)
    assert notice_monitor.run_notices()["events_created"] == 0
    assert source.error is None


def test_pagination_gap_is_not_reported_as_success(db, feed, monkeypatch):
    notice_monitor.run_notices()
    source = db.scalar(select(Source).where(Source.url == NOTICES))
    before = source.content_hash
    feed[NOTICES] = page(201, 1)
    feed[NOTICES + "?page=1"] = page(202, 2)
    monkeypatch.setattr(notice_monitor.settings, "notice_max_pages", 2)
    with pytest.raises(notice_monitor.CoverageGap):
        notice_monitor.run_notices()
    assert source.content_hash == before
    assert source.error == "CoverageGap"
