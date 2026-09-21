import json
import logging
import time
from datetime import timezone

from sqlalchemy import select

from app.config import settings
from app.db import Session
from app.http import OfficialClient
from app.ingest import ingest
from app.models import Source, now
from app.parser import CATALOG

log = logging.getLogger("merito.monitor")


def run():
    client = OfficialClient()
    try:
        conditional = {}
        with Session() as db:
            previous = db.scalar(select(Source).where(Source.url == CATALOG))
            # Force a full response every UTC day to detect invalid server validators.
            if (
                previous
                and previous.last_success_at
                and previous.last_success_at.astimezone(timezone.utc).date() == now().date()
            ):
                if previous.etag:
                    conditional["If-None-Match"] = previous.etag
                elif previous.last_modified:
                    conditional["If-Modified-Since"] = previous.last_modified
        code, headers, html = client.fetch(CATALOG, conditional)
        with Session.begin() as db:
            if code == 304:
                source = db.scalar(select(Source).where(Source.url == CATALOG))
                if not source or not source.content_hash:
                    raise ValueError("304 sin snapshot previo")
                source.last_checked_at = source.last_success_at = now()
                source.http_status = 304
                source.error = None
                result = {"cached": True, "events_created": 0}
            else:
                result = ingest(db, html)
            source = db.scalar(select(Source).where(Source.url == CATALOG))
            source.etag = headers.get("etag", source.etag)
            source.last_modified = headers.get("last-modified", source.last_modified)
        log.info(json.dumps({"event": "source_check_success", **result}))
        return result
    except Exception as exc:
        with Session.begin() as db:
            source = db.scalar(select(Source).where(Source.url == CATALOG))
            if source is None:
                source = Source(url=CATALOG)
                db.add(source)
            source.last_checked_at = now()
            source.error = type(exc).__name__
            source.http_status = getattr(getattr(exc, "response", None), "status_code", None)
        log.error(json.dumps({"event": "source_check_failed", "error": type(exc).__name__}))
        raise
    finally:
        client.close()


def main():
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO)
    while True:
        try:
            run()
            from app.notice_monitor import run_notices

            run_notices()
        except Exception:
            if args.once:
                raise
        if args.once:
            return
        time.sleep(settings.monitor_interval_minutes * 60)


if __name__ == "__main__":
    main()
