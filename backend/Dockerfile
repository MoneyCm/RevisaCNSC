FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml .
COPY app app
RUN pip install --no-cache-dir . && useradd --create-home radar
COPY alembic.ini .
COPY migrations migrations
USER radar
CMD ["uvicorn", "app.api:app", "--host", "0.0.0.0", "--port", "8000"]
