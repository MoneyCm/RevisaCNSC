import os

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session


@pytest.fixture
def db():
    url = os.environ.get("TEST_DATABASE_URL")
    if not url:
        pytest.skip("TEST_DATABASE_URL required: real PostgreSQL")
    engine = create_engine(url)
    with engine.connect() as conn:
        transaction = conn.begin()
        with Session(bind=conn) as db:
            yield db
        transaction.rollback()
    engine.dispose()
