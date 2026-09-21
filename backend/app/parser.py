import hashlib
import json
from urllib.parse import urljoin, urlsplit, urlunsplit

from bs4 import BeautifulSoup

CATALOG = "https://www.cnsc.gov.co/convocatorias/en-desarrollo"


class ParseError(ValueError):
    pass


def official_url(value: str, base: str = CATALOG) -> str:
    url = urlsplit(urljoin(base, value))
    if (
        url.scheme != "https"
        or url.hostname not in {"www.cnsc.gov.co", "cnsc.gov.co"}
        or url.username
        or url.password
        or url.port not in {None, 443}
    ):
        raise ValueError("URL oficial no permitida")
    return urlunsplit(("https", "www.cnsc.gov.co", url.path.rstrip("/"), "", ""))


def digest(value) -> str:
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
    ).hexdigest()


def parse_catalog(html: str) -> list[dict]:
    soup = BeautifulSoup(html, "html.parser")
    heading = soup.select_one("main h1")
    if not heading or "desarrollo" not in heading.get_text().lower():
        raise ParseError("Falta encabezado de catálogo en desarrollo")
    rows = {}
    for anchor in soup.select("main .views-field-name a[href]"):
        url = official_url(anchor["href"])
        if not urlsplit(url).path.startswith("/convocatorias/"):
            raise ParseError("Enlace de catálogo inesperado")
        name = " ".join(anchor.get_text(" ", strip=True).split())
        if not name:
            raise ParseError("Proceso sin nombre")
        rows[url] = {"name": name, "official_url": url, "slug": url.rsplit("/", 1)[-1]}
    if not rows:
        raise ParseError("Catálogo vacío o estructura modificada")
    if soup.select_one("main .pager__item--next a"):
        raise ParseError("Catálogo paginado: ampliar recolector")
    return sorted(rows.values(), key=lambda row: row["official_url"])
