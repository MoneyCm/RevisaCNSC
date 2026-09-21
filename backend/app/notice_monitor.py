"""Bounded paginated notice monitoring, with explicit catch-up gaps."""

import logging
from datetime import UTC

from sqlalchemy import select

from app.config import settings
from app.db import Session
from app.event_engine import due_events, record_notice
from app.http import OfficialClient
from app.models import Snapshot, Source, now
from app.parser import digest
from app.publications import NOTICES, notice_index, parse_notice

log = logging.getLogger("merito.notices")


class CoverageGap(RuntimeError):
    pass


def mark_failure(url, exc):
    with Session.begin() as db:
        source = db.scalar(select(Source).where(Source.url == url))
        if source is None:
            source = Source(url=url)
            db.add(source)
        source.last_checked_at = now()
        source.error = type(exc).__name__
        source.http_status = getattr(getattr(exc, "response", None), "status_code", None)


def run_notices():
    client = OfficialClient()
    try:
        with Session() as db:
            feed = db.scalar(select(Source).where(Source.url == NOTICES))
            previous = (
                db.scalar(
                    select(Snapshot)
                    .where(Snapshot.source_id == feed.id)
                    .order_by(Snapshot.captured_at.desc())
                    .limit(1)
                )
                if feed
                else None
            )
            known = set(previous.normalized_content) if previous else set()
        baseline = not known
        urls, page_url = [], NOTICES
        complete = False
        for page in range(settings.notice_max_pages):
            _, _, html = client.fetch(page_url)
            links, next_page = notice_index(html, page_url)
            urls.extend(links)
            if not next_page or (page >= 1 and (baseline or known.intersection(links))):
                complete = True
                break
            page_url = next_page
        if not complete:
            raise CoverageGap("No se alcanzó el límite conocido; aumentar NOTICE_MAX_PAGES")
        payloads, failures, cached = [], [], 0
        for url in dict.fromkeys(urls):
            try:
                headers = {}
                with Session() as db:
                    source = db.scalar(select(Source).where(Source.url == url))
                    if (
                        source
                        and source.last_success_at
                        and source.last_success_at.astimezone(UTC).date() == now().date()
                    ):
                        if source.etag:
                            headers["If-None-Match"] = source.etag
                        elif source.last_modified:
                            headers["If-Modified-Since"] = source.last_modified
                status, response_headers, html = client.fetch(url, headers)
                if status == 304:
                    with Session.begin() as db:
                        source = db.scalar(select(Source).where(Source.url == url))
                        if not source or not source.content_hash:
                            raise ValueError("304 sin publicación previa")
                        source.last_success_at = source.last_checked_at = now()
                        source.http_status, source.error = 304, None
                    cached += 1
                else:
                    notice = parse_notice(html, url)
                    payloads.append((notice.published_at, url, html, response_headers))
            except Exception as exc:
                failures.append(url)
                mark_failure(url, exc)
                log.warning("notice_check_failed %s %s", url, type(exc).__name__)
        created = 0
        for _, url, html, headers in sorted(
            payloads, key=lambda p: p[0].timestamp() if p[0] else 0
        ):
            try:
                with Session.begin() as db:
                    result = record_notice(db, html, url, baseline=baseline)
                    source = db.scalar(select(Source).where(Source.url == url))
                    source.etag = headers.get("etag")
                    source.last_modified = headers.get("last-modified")
                    created += result["events_created"]
            except Exception as exc:
                failures.append(url)
                mark_failure(url, exc)
        if failures:
            raise CoverageGap(f"{len(failures)} avisos fallaron; punto de recuperación sin avanzar")
        with Session.begin() as db:
            feed = db.scalar(select(Source).where(Source.url == NOTICES))
            if feed is None:
                feed = Source(url=NOTICES)
                db.add(feed)
                db.flush()
            ordered_urls = list(dict.fromkeys(urls))
            content_hash = digest(ordered_urls)
            if feed.content_hash != content_hash:
                db.add(
                    Snapshot(
                        source_id=feed.id,
                        normalized_content=ordered_urls,
                        content_hash=content_hash,
                    )
                )
            feed.content_hash = content_hash
            feed.last_checked_at = feed.last_success_at = now()
            feed.http_status, feed.error = 200, None
            reminders = due_events(db)
        result = {
            "notices_checked": len(set(urls)),
            "cached": cached,
            "events_created": created,
            "reminders_created": reminders,
            "baseline": baseline,
        }
        log.info("notice_check_success %s", result)
        return result
    except Exception as exc:
        mark_failure(NOTICES, exc)
        raise
    finally:
        client.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    run_notices()
