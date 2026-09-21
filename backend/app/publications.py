"""Drupal notice extraction: official association, body and publication timestamp."""

import re
from dataclasses import dataclass
from datetime import datetime
from urllib.parse import parse_qs, urljoin, urlsplit
from zoneinfo import ZoneInfo

from bs4 import BeautifulSoup

from app.parser import ParseError, official_url

NOTICES = "https://www.cnsc.gov.co/avisos-informativos"


def clean(value):
    return " ".join(value.split())


@dataclass(frozen=True)
class Notice:
    url: str
    process_url: str | None
    process_name: str | None
    title: str
    published_at: datetime | None
    paragraphs: list[str]
    documents: list[dict]
    associations: list[dict]

    def canonical(self):
        return {
            "process_url": self.process_url,
            "associations": self.associations,
            "title": self.title,
            "published_at": self.published_at.isoformat() if self.published_at else None,
            "paragraphs": self.paragraphs,
            "documents": self.documents,
        }


def notice_index(html: str, page_url: str = NOTICES):
    soup = BeautifulSoup(html, "html.parser")
    heading = soup.select_one("main h1")
    if not heading or "avisos informativos" not in clean(heading.get_text()).lower():
        raise ParseError("Índice de avisos sin encabezado esperado")
    links = [official_url(a["href"]) for a in soup.select("main .view-content h2 a[href]")]
    if not links or any(not re.fullmatch(r"/node/\d+", urlsplit(u).path) for u in links):
        raise ParseError("Índice de avisos vacío o estructura inesperada")
    next_link = soup.select_one("main .pager__item--next a[href]")
    next_url = None
    if next_link:
        candidate = urljoin(page_url, next_link["href"])
        official_url(candidate)
        parts = urlsplit(candidate)
        query = parse_qs(parts.query)
        if parts.path != "/avisos-informativos" or not query.get("page", [""])[0].isdigit():
            raise ParseError("Paginación de avisos inesperada")
        next_url = NOTICES + "?page=" + query["page"][0]
    return list(dict.fromkeys(links)), next_url


def parse_notice(html: str, url: str) -> Notice:
    url = official_url(url)
    soup = BeautifulSoup(html, "html.parser")
    article = soup.select_one("main article[data-history-node-id]")
    title = soup.select_one("main h1")
    if article is None or title is None:
        raise ParseError("Falta artículo o título oficial")
    if urlsplit(url).path != "/node/" + article["data-history-node-id"]:
        raise ParseError("Identidad del artículo no coincide con la URL")
    associations = article.select(".field--name-field-convocatoria-asociada a[href]")
    candidates = []
    for association in associations:
        link = official_url(association["href"])
        if not urlsplit(link).path.startswith("/convocatorias/"):
            raise ParseError("Asociación no corresponde a una convocatoria")
        candidates.append({"url": link, "name": clean(association.get_text(" ", strip=True))})
    process_url = candidates[0]["url"] if len(candidates) == 1 else None
    process_name = candidates[0]["name"] if len(candidates) == 1 else None
    body = article.select_one(".field--name-body")
    if body is None or not body.get_text(strip=True):
        raise ParseError("Aviso sin cuerpo")
    for tag in body.select("script,style,nav"):
        tag.decompose()
    # Drupal sometimes contains invalid nested empty <p>; preserve the meaningful parent.
    blocks = body.select("p,li,tr")
    paragraphs = list(
        dict.fromkeys(
            clean(b.get_text(" ", strip=True))
            for b in blocks
            if not b.find_parent(["p", "li", "tr"])
        )
    )
    paragraphs = [p for p in paragraphs if p]
    if not paragraphs:
        paragraphs = [clean(body.get_text(" ", strip=True))]
    published_at = None
    created = article.select_one(".field--name-created")
    match = re.search(
        r"(\d{2}/\d{2}/\d{4})\s*-\s*(\d{2}:\d{2})", created.get_text() if created else ""
    )
    if match:
        try:
            published_at = datetime.strptime(" ".join(match.groups()), "%d/%m/%Y %H:%M").replace(
                tzinfo=ZoneInfo("America/Bogota")
            )
        except ValueError:
            pass
    documents = {}
    for anchor in body.select("a[href]"):
        if urlsplit(anchor["href"]).path.lower().endswith(".pdf"):
            try:
                link = official_url(anchor["href"])
            except ValueError:
                continue
            documents[link] = {
                "url": link,
                "title": clean(anchor.get_text(" ", strip=True)) or "Documento oficial",
            }
    return Notice(
        url,
        process_url,
        process_name,
        clean(title.get_text(" ", strip=True)),
        published_at,
        paragraphs,
        sorted(documents.values(), key=lambda d: d["url"]),
        candidates,
    )
