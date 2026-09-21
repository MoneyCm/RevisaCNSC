from datetime import date

import pytest

from app.dates import parse_dates


@pytest.mark.parametrize(
    "raw",
    ["4 de noviembre de 2026", "04/11/2026", "04-11-2026", "a partir del 4 de noviembre de 2026"],
)
def test_exact_date(raw):
    evidence = parse_dates(raw)
    assert evidence.start == date(2026, 11, 4)
    assert evidence.raw_text == raw
    assert evidence.confidence == "CONFIRMED"


@pytest.mark.parametrize(
    "raw", ["del 4 al 18 de noviembre de 2026", "entre el 4 y el 18 de noviembre de 2026"]
)
def test_range(raw):
    evidence = parse_dates(raw)
    assert evidence.start == date(2026, 11, 4)
    assert evidence.end == date(2026, 11, 18)


@pytest.mark.parametrize(
    "raw",
    [
        "del 4 al 18 de noviembre",
        "a partir del 4 de noviembre",
        "31/02/2026",
        "del 18 al 4 de noviembre de 2026",
        "04/11-2026",
    ],
)
def test_ambiguous_invalid_or_missing_year(raw):
    evidence = parse_dates(raw)
    assert evidence.confidence == "UNCONFIRMED"
    assert evidence.start is None
