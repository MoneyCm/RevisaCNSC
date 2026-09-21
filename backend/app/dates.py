"""Strict Spanish calendar dates; no inferred year and no invented time of day."""

import re
import unicodedata
from dataclasses import dataclass
from datetime import date

MONTHS = {
    name: i + 1
    for i, name in enumerate(
        (
            "enero",
            "febrero",
            "marzo",
            "abril",
            "mayo",
            "junio",
            "julio",
            "agosto",
            "septiembre",
            "octubre",
            "noviembre",
            "diciembre",
        )
    )
}
MONTHS["setiembre"] = 9
MONTH = "(?:" + "|".join(MONTHS) + ")"


@dataclass(frozen=True)
class DateEvidence:
    raw_text: str
    start: date | None
    end: date | None
    confidence: str
    timezone: str = "America/Bogota"


def parse_dates(raw_text: str) -> DateEvidence:
    text = " ".join(unicodedata.normalize("NFKC", raw_text).lower().split())
    unknown = DateEvidence(raw_text, None, None, "UNCONFIRMED")
    # One bounded date or range only. Multiple periods need a publication-specific parser.
    pattern = rf"(?:del|entre el) (\d{{1,2}}) (?:al|y el) (\d{{1,2}}) de ({MONTH}) de (\d{{4}})"
    match = re.fullmatch(pattern, text)
    try:
        if match:
            first, last, month, year = match.groups()
            start = date(int(year), MONTHS[month], int(first))
            end = date(int(year), MONTHS[month], int(last))
            return DateEvidence(raw_text, start, end, "CONFIRMED") if start <= end else unknown
        match = re.fullmatch(rf"(?:a partir del )?(\d{{1,2}}) de ({MONTH}) de (\d{{4}})", text)
        if match:
            day, month, year = match.groups()
            return DateEvidence(
                raw_text, date(int(year), MONTHS[month], int(day)), None, "CONFIRMED"
            )
        match = re.fullmatch(r"(\d{1,2})([/\-])(\d{1,2})\2(\d{4})", text)
        if match:
            day, _, month, year = match.groups()
            return DateEvidence(raw_text, date(int(year), int(month), int(day)), None, "CONFIRMED")
    except ValueError:
        return unknown
    return unknown
