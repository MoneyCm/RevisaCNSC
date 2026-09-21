import ssl
import time
from urllib.robotparser import RobotFileParser

import httpx

from app.config import settings
from app.parser import official_url


class OfficialClient:
    def __init__(self):
        self.client = httpx.Client(
            verify=ssl.create_default_context(),
            timeout=25,
            follow_redirects=False,
            headers={"User-Agent": settings.user_agent},
        )
        self.last_request = 0.0
        self.robots = RobotFileParser()
        self.robots_loaded = False

    def close(self):
        self.client.close()

    def request(self, url, headers=None):
        official_url(url)
        for attempt in range(3):
            time.sleep(max(0, 2 - (time.monotonic() - self.last_request)))
            self.last_request = time.monotonic()
            try:
                with self.client.stream("GET", url, headers=headers) as response:
                    if response.is_redirect and response.status_code != 304:
                        raise ValueError("Redirección requiere revisión de fuente")
                    if response.status_code == 304:
                        return 304, response.headers, ""
                    response.raise_for_status()
                    chunks, size = [], 0
                    for chunk in response.iter_bytes():
                        size += len(chunk)
                        if size > 2_000_000:
                            raise ValueError("Respuesta supera límite de 2 MB")
                        chunks.append(chunk)
                    return response.status_code, response.headers, b"".join(chunks).decode("utf-8")
            except (httpx.TransportError, httpx.HTTPStatusError) as exc:
                if isinstance(exc, httpx.HTTPStatusError):
                    code = exc.response.status_code
                    if code != 429 and code < 500:
                        raise
                if attempt == 2:
                    raise
                time.sleep(2 ** (attempt + 1))
        raise RuntimeError("Reintentos agotados")

    def fetch(self, url, headers=None):
        if not self.robots_loaded:
            _, _, robots = self.request("https://www.cnsc.gov.co/robots.txt")
            self.robots.parse(robots.splitlines())
            self.robots_loaded = True
        if not self.robots.can_fetch(settings.user_agent, url):
            raise ValueError("Ruta bloqueada por robots.txt")
        return self.request(url, headers)
