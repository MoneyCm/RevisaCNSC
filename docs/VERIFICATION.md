# Evidencia de validación — 2026-09-19 America/Bogota

- PostgreSQL 17 local aislado, puerto 55432. Alembic upgrade head y alembic check aprobados.
- python -m app.monitor --once: HTTP real 200, 27 procesos, baseline true, cero eventos.
- Segunda ejecución: HTTP real 200, 27 procesos, baseline false, cero eventos nuevos.
- GET http://127.0.0.1:8000/api/v1/processes: 200, 27 registros. Health reporta fuente reciente y Firebase not_configured.
- pytest -q: 28 passed. Usa PostgreSQL real; 2 advertencias deprecación dependencias.
- ruff check: aprobado.
- Seis skills: quick_validate.py aprobado en todas.
- docker compose config --quiet: aprobado. Docker daemon no disponible, no se ejecutó Compose.
- Gradle 8.7: lintDebug testDebugUnitTest assembleDebug → BUILD SUCCESSFUL.
- Android JUnit: 2 tests, cero fallos. Lint: cero errores, 13 advertencias.
- APK debug: android/app/build/outputs/apk/debug/app-debug.apk.
- assembleDebugAndroidTest: BUILD SUCCESSFUL. Resultado de ejecución se registra en STATUS.

No hay datos ficticios persistidos ni envío push real. La evidencia no certifica el producto completo ni vigilancia de fechas.

La primera instalación instrumentada no pudo comenzar: adb informó "device is still booting". No se cuenta como prueba aprobada; no hubo instalación en teléfono personal.
