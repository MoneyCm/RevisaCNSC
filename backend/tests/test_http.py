import httpx
import pytest

from app.http import OfficialClient
from app.parser import CATALOG


def test_robots_disallow(monkeypatch):
    client = OfficialClient()
    monkeypatch.setattr(
        client, "request", lambda *a: (200, {}, "User-agent: *\nDisallow: /convocatorias/")
    )
    try:
        with pytest.raises(ValueError, match="robots"):
            client.fetch(CATALOG)
    finally:
        client.close()


def test_redirect_not_followed(monkeypatch):
    client = OfficialClient()
    client.client.close()
    client.client = httpx.Client(
        transport=httpx.MockTransport(
            lambda request: httpx.Response(302, headers={"location": "https://evil.test"})
        )
    )
    try:
        with pytest.raises(ValueError, match="Redirección"):
            client.request(CATALOG)
    finally:
        client.close()
