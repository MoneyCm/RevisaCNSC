# Reglas permanentes de Mérito Radar

## Relevo entre agentes
Codex, Cline, Devin u otros agentes deben trabajar sucesivamente: solo uno modifica esta carpeta a la vez.

Antes de cambiar archivos, leer docs/PROJECT_STATUS.md, docs/STATUS.md, docs/NEXT_TASKS.md y docs/DECISIONS.md; revisar git status e historial si existe. Comprobar el código antes de asumir que falta una funcionalidad. Preservar los cambios de otros agentes.

STATUS es el registro canónico; PROJECT_STATUS es la entrada de relevo y NEXT_TASKS ordena el trabajo. Para la misma funcionalidad prevalece la verificación más reciente sobre notas históricas.

Al cerrar un bloque, ejecutar comprobaciones pertinentes, actualizar STATUS y NEXT_TASKS y registrar decisiones nuevas en DECISIONS. Distinguir pruebas anteriores de nuevas. En cambios solo documentales, comprobar enlaces y coherencia.

Antes del relevo, preparar un punto de control Git revisando los archivos que entrarán en el commit. No usar git add . a ciegas, modificar secretos/.env ni incluir datos del teléfono o artefactos. No publicar sin autorización. Indicar si el commit quedó creado o pendiente. No iniciar otro bloque durante el cierre.

## Regla principal de arquitectura
La arquitectura de producción de Mérito Radar es local-first y debe funcionar sin backend propio. El backend existente (FastAPI/PostgreSQL) es tooling/reference y no puede convertirse nuevamente en requisito de ejecución sin decisión explícita del usuario. Android debe consultar CNSC directamente, persistir en Room, detectar cambios localmente y generar notificaciones locales.

Antes de implementar, leer docs/STATUS.md, la skill aplicable y código existente. Actualizar STATUS después de cada bloque. Mantener cambios verificables y documentar deuda en STATUS y decisiones en DECISIONS.

Nunca inventar datos, fechas o estados CNSC. Nunca presentar inferencias como oficiales. Conservar URL, evidencia y detección. Marcar ambigüedad UNCONFIRMED; no emitir alertas críticas desde inferencias débiles. No generar novedades por hash HTML bruto ni convertir una caída de fuente en fin del proceso.

Ejecutar pruebas pertinentes. DONE requiere implementación, compilación, pruebas aprobadas, ejecución razonable y documentación. Lo no comprobado es NOT VERIFIED. No mocks fuera de tests, botones vacíos ni TODO silenciosos.

No secretos en Git, credenciales SIMO, CAPTCHA ni logs de tokens completos. Validar entradas y URLs, usar variables de entorno. Respetar robots.txt, límites, cache, backoff y tiempos de espera.
