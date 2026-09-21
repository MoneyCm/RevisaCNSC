from pathlib import Path

import pytest

from app.parser import ParseError, digest, official_url, parse_catalog

HTML = Path(__file__).with_name("fixtures").joinpath("catalog.html").read_text(encoding="utf-8")


def test_real_catalog():
    rows = parse_catalog(HTML)
    assert any(r["name"] == "Territorial 12" for r in rows)
    assert len(rows) > 5
    assert all("?" not in r["official_url"] for r in rows)


def test_cosmetic_noise():
    assert digest(parse_catalog(HTML)) == digest(
        parse_catalog("<nav>Menú diferente</nav>" + HTML + "<footer>Otro footer</footer>")
    )


@pytest.mark.parametrize(
    "url",
    [
        "http://www.cnsc.gov.co/a",
        "https://evil.test/a",
        "https://cnsc.gov.co.evil.test/a",
        "https://user@cnsc.gov.co/a",
        "https://127.0.0.1/a",
    ],
)
def test_reject_untrusted_urls(url):
    with pytest.raises(ValueError):
        official_url(url)


@pytest.mark.parametrize(
    "html", ["", "<h1>Bloqueado</h1>", "<main><h1>Procesos en desarrollo</h1></main>"]
)
def test_structure_failure(html):
    with pytest.raises(ParseError):
        parse_catalog(html)
