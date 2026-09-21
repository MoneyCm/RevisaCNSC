"""Conservative, evidence-bound classification. No keyword-only critical alerts."""

import re
import unicodedata
from dataclasses import dataclass
from datetime import date

from app.dates import MONTH, parse_dates
from app.publications import Notice


def fold(text):
    return " ".join(
        "".join(
            c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c)
        ).split()
    )


@dataclass(frozen=True)
class Finding:
    kind: str
    event_type: str
    modality: str
    population: str
    start: date | None
    end: date | None
    confidence: str
    excerpt: str
    postponed: bool = False


def scope(text):
    text = fold(text)
    modes = [
        mode
        for token, mode in [("abierto", "OPEN"), ("ascenso", "PROMOTION")]
        if re.search(rf"\b{token}\b", text)
    ]
    mode = modes[0] if len(modes) == 1 else "UNKNOWN"
    if "sin reserva" in text or "general" in text:
        population = "GENERAL"
    elif "reserva" in text and "discapacidad" in text:
        population = "DISABILITY_RESERVED"
    else:
        population = "UNKNOWN"
    if ("sin reserva" in text or "general" in text) and "con reserva" in text:
        population = "UNKNOWN"
    return mode, population


def window(text):
    text = re.sub(r"\bdel (\d{4})\b", r"de \1", fold(text))
    compact = re.search(
        rf"(?:del|entre el) \d{{1,2}} (?:al|y el) \d{{1,2}} de {MONTH} de \d{{4}}", text
    )
    if compact:
        parsed = parse_dates(compact.group())
        return parsed.start, parsed.end
    full = rf"\b\d{{1,2}} de {MONTH} de \d{{4}}\b|\b\d{{1,2}}[/\-]\d{{1,2}}[/\-]\d{{4}}\b"
    matches = list(re.finditer(full, text))
    if len(matches) == 2:
        between = text[matches[0].end() : matches[1].start()]
        if re.search(r"hasta|\bal\b|y el", between):
            start = parse_dates(matches[0].group()).start
            end = parse_dates(matches[1].group()).start
            if start and end and start <= end:
                return start, end
    if len(matches) == 1:
        value = parse_dates(matches[0].group()).start
        prefix = text[: matches[0].start()]
        if re.search(
            r"hasta(?: el)?\s*$|(?:cierre|terminacion|finalizacion|fin)[^.;]{0,40}$", prefix
        ):
            return None, value
        if re.search(r"(?:inicio|apertura|a partir|desde)[^.;]{0,40}$", prefix):
            return value, None
    return None, None


def classify(notice: Notice) -> list[Finding]:
    title = fold(notice.title)
    context = notice.title + " " + (notice.paragraphs[0] if notice.paragraphs else "")
    fallback_mode, fallback_population = scope(context)
    findings = []
    if re.search(r"(?:no se|no) (?:abr|inici|realiz)|posible|proyecto", title):
        return [
            Finding(
                "NEWS",
                "NEWS_PUBLISHED",
                "UNKNOWN",
                "UNKNOWN",
                None,
                None,
                "UNCONFIRMED",
                notice.title,
            )
        ]
    # Postponing a stage is not suspending the whole process; never announce it as open.
    if re.search(r"aplazamiento|se aplaza|se aplazan", title):
        for token, kind in [
            (r"recaudo|derechos de participacion", "PAYMENT"),
            (r"inscripcion", "REGISTRATION"),
        ]:
            if re.search(token, title):
                findings.append(
                    Finding(
                        kind,
                        kind + "_POSTPONED",
                        fallback_mode,
                        fallback_population,
                        None,
                        None,
                        "CONFIRMED" if notice.published_at else "UNCONFIRMED",
                        notice.title,
                        True,
                    )
                )
        return findings or [
            Finding(
                "NEWS",
                "NEWS_PUBLISHED",
                "UNKNOWN",
                "UNKNOWN",
                None,
                None,
                "UNCONFIRMED",
                notice.title,
            )
        ]
    for paragraph in notice.paragraphs:
        text = fold(paragraph)
        if re.search(r"(?:no se|no) (?:abr|inici|realiz)|podria|posible|proyecto|sujeto a", text):
            continue
        if re.search(r"aplaz|suspend", text):
            continue
        start, end = window(paragraph)
        if start is None and end is None:
            continue
        mode, population = scope(paragraph)
        # Never borrow scope from an introduction that explicitly lists both modalities.
        if mode == "UNKNOWN" and not ("abierto" in text and "ascenso" in text):
            mode = fallback_mode
        if population == "UNKNOWN" and not ("con reserva" in text and "sin reserva" in text):
            population = fallback_population
        confidence = (
            "CONFIRMED"
            if mode != "UNKNOWN" and population != "UNKNOWN" and notice.published_at
            else "UNCONFIRMED"
        )
        for token, kind in [
            (r"recaudo|derechos de participacion|venta de derechos", "PAYMENT"),
            (r"inscripcion", "REGISTRATION"),
        ]:
            if re.search(token, text):
                findings.append(
                    Finding(
                        kind,
                        kind + "_ANNOUNCED",
                        mode,
                        population,
                        start,
                        end,
                        confidence,
                        paragraph,
                    )
                )
    if findings:
        # Conflicting windows in the same scope need human review, not last-paragraph-wins.
        groups = {}
        for item in findings:
            groups.setdefault((item.kind, item.modality, item.population), []).append(item)
        result = []
        for group in groups.values():
            starts = {f.start for f in group if f.start}
            ends = {f.end for f in group if f.end}
            first = group[0]
            certain = (
                len(starts) <= 1
                and len(ends) <= 1
                and all(f.confidence == "CONFIRMED" for f in group)
            )
            start = next(iter(starts)) if len(starts) == 1 else None
            end = next(iter(ends)) if len(ends) == 1 else None
            certain = certain and not (start and end and start > end)
            result.append(
                Finding(
                    first.kind,
                    first.event_type,
                    first.modality,
                    first.population,
                    start,
                    end,
                    "CONFIRMED" if certain else "UNCONFIRMED",
                    "\n".join(f.excerpt for f in group),
                )
            )
        return result
    for pattern, kind in [
        (r"suspension (?:del|de los) proceso|se suspende el proceso", "PROCESS_SUSPENDED"),
        (r"reanudacion (?:del|de los) proceso|se reanuda el proceso", "PROCESS_RESUMED"),
        (r"citacion", "EXAM_CITATION"),
        (r"resultados", "RESULT_PUBLISHED"),
        (r"listas? de elegibles", "ELIGIBLE_LIST_PUBLISHED"),
    ]:
        if re.search(pattern, title) and not re.search(r"proyecto|posible|no se", title):
            if kind in {
                "RESULT_PUBLISHED",
                "ELIGIBLE_LIST_PUBLISHED",
                "EXAM_CITATION",
            } and re.search(r"se publicara|seran publicados", fold(" ".join(notice.paragraphs))):
                kind = "NEWS_PUBLISHED"
            return [
                Finding(
                    "NOTICE",
                    kind,
                    "UNKNOWN",
                    "UNKNOWN",
                    None,
                    None,
                    "CONFIRMED" if notice.published_at else "UNCONFIRMED",
                    notice.title,
                )
            ]
    return [
        Finding(
            "NEWS",
            "NEWS_PUBLISHED",
            "UNKNOWN",
            "UNKNOWN",
            None,
            None,
            "CONFIRMED" if notice.published_at else "UNCONFIRMED",
            notice.title,
        )
    ]
