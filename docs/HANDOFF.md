## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# HANDOFF — Relevo de OpenCode a Codex

- **Fecha**: 2026-09-21 (última actualización al cierre del bloque de robustez del historial).
- **Estado actual del proyecto**: Android local-first de vigilancia CNSC (Mérito Radar) funcional y verificado hasta el cierre de esta sesión. Consulta CNSC directamente (OkHttp + TLS corregido), persiste en Room (v3), detecta avisos locales, proyecta fechas conservadoramente y genera notificaciones locales. Backend FastAPI/PostgreSQL es tooling de referencia, no requisito de ejecución.
- **Último commit local**: ver `git log -1`; main sincronizado con `origin/main` (push completado en este bloque).
- **Prioridad autorizada cumplida**: (1) checkpoint y push de coordinación (a1d6b21); (2) robustez del historial con regresiones (este bloque); (3) pruebas completas y migraciones aisladas (verificadas); (4) documentación, commit y push (este cierre).

## Resumen del bloque actual — robustez del historial del micrositio

Cinco defectos del "Historial PARCIAL" detectados por la auditoría se corrigieron con regresiones:

1. **Fallo en página posterior ya no sustituye datos completos por parciales**: `parseAll` dejó de absorber fallos tardíos; `ActivityRefresh.run` ya los trataba (reporta error, no guarda, conserva caché) y ahora ese camino se ejerce al propagarse la excepción.
2. **Cancelación se propaga**: `parseAll` y `ActivityRefresh.run` no convierten `CancellationException` en éxito parcial; no se guarda ni se reporta.
3. **URL por página**: `collect`/`summarize` guardan la URL real de cada página en `ActivityPublication.sourceUrl` (antes todas apuntaban a la página inicial).
4. **Una sola descarga de la primera página en consulta manual**: nuevo `ProcessMicrositeReader.read` comparte la primera respuesta entre identidad y actividad (`firstPageHtml` en `parseAll`), en vez de los dos `fetch` previos.
5. **Pausa prudente**: `parseAll` acepta `pause` (default `delay(2000)`) entre páginas; aplica también a la consulta manual, único sitio que solicita páginas posteriores.

Cambios de archivos: `ProcessActivity.kt`, `Data.kt`, nuevo `ProcessMicrositeReader.kt`, `ProcessActivityTest.kt` (15 tests). Regresión documental: DEC-018 toleraba fallos tardíos; ahora "commit solo con recorrido completo exitoso" (DEC-020).

## Verificación del bloque actual

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` → **BUILD SUCCESSFUL**.
- Tests JVM: **61 tests, 0 fallos, 0 errores** (ProcessActivityTest 15).
- Lint: **0 errores, 19 advertencias** (todas preexistentes).
- Instrumentado en 8912c62d vía `am instrument` (sin desinstalar producción): **MigrationTest 3/3 OK**.
- `radar.db` real verificada intacta tras las pruebas: v3, 27 procesos, 0 seguidos, integrity ok. No se tocó following ni notice_state; no se ejecutó RealCatalogTest.

## Pruebas y resultados — resumen completo del turno anterior (grabado aquí)

- Test JVM previos del checkpoint a1d6b21: 56 tests, 0 fallos, 0 errores (9 suites); lint 0 errores/19 advertencias; MigrationTest 3/3 en 8912c62d.
- NO ejecutar `connectedDebugAndroidTest` en el dispositivo físico: desinstala/reinstala la app y vacía datos (reponer seguidos antes).

## Problemas encontrados en este bloque

- **`INSTALL_FAILED_USER_RESTRICTED` (Xiaomi)** ya conocido: se resolvió en el turno previo activando "Instalar vía USB"; para verificación instrumentada se usa `pm install -r` + `am instrument`, no el runner de Gradle.
- **Estado limpiado del dispositivo** (following/caché activity/notice_state vacíos desde la reinstalación del turno de migraciones; no pérdida en este bloque; ver TASKS.md 8 para repoblar). Verificación 2026-09-21: el usuario ya repobló parcialmente (3 seguidos: DIAN 2676, Aerocivil Primera Fase, CAR) y el worker quedó activo; PGN 2407 y ESE2 fuera de seguimiento.

## Problemas todavía pendientes (NOT VERIFIED)

- Entrega física de novedad crítica real con pantalla bloqueada/app cerrada (worker → outbox → bandeja → deep link).
- Paginación real de un micrositio con más de 3 páginas (hoy DIAN 2676 tiene una página).
- Diagnóstico de fallo real por micrositio observado en el teléfono.
- Migración de una base histórica real con datos previos del usuario.
- Comportamiento prolongado bajo Doze/restricciones OEM.
- Recordatorio de apertura/cierre y cancelación por aplazamiento en producción real.
- Fallo físico de página posterior y cronometría física de la pausa (validados con unitarias, no en dispositivo).

## Decisiones técnicas tomadas y sus motivos

- **DEC-020 — Historial robusto: compromiso total o nada** (registrada en DECISIONS.md): commit solo con recorrido completo exitoso; cancelación propaga; caché previa conservada; una descarga inicial; URL real por página; pausa de 2 s. Motivo: un historial parcial no debe sustituir a uno más completo guardado, y toda URL de evidencia debe corresponder a la página que la contenía.

## Cambios de arquitectura

- Ninguno. Se mantiene la arquitectura Android local-first. `ProcessMicrositeReader` es un nuevo objeto auxiliar de lectura, sin cambios de esquema.

## Tareas iniciadas pero incompletas

- Ninguna dentro del alcance autorizado. La robustez del historial quedó DONE. Diagnóstico/preferencias (NEXT_TASKS 7) no se inició.

## Riesgos o regresiones posibles

- **Datos del dispositivo reseteados** (following/actividad vacíos): repoblar seguidos antes de depender de vigilancia real (TASKS.md 8).
- Los schemas históricos 1.json/2.json son **derivados** del 3.json actual; no existía exportación previa. Si una validación futura fallara, revisar esos JSON.
- Actualizar Room (2.6.1 → 2.8.x) o migrar kapt → KSP cambia la ruta de generación de schemas; volver a verificar `schemaLocation` y los assets de test.
- La política de fallos tardíos cambió de "conservar lo recogido" a "propagar y conservar caché previa": cualquier código que asuma tolerancia parcial de `parseAll` quedará desactualizado.

## Siguiente tarea recomendada

NEXT_TASKS 7 — **Diagnóstico y preferencias**: revisar qué existe (repository ya expone estado del worker/health; ajustes tienen notificación de prueba), luego completar con estado real (última revisión, errores, alcance de fuentes, frecuencia y preferencias). Recomendable antes: repoblar seguidos en 8912c62d (TASKS.md 8) para que el diagnóstico muestre datos reales.

## Tareas que requieren especialmente revisión de Codex

1. Confirmar el push a origin/main del estado local y que `main == origin/main`.
2. Decidir si versionar también los JSON de esquema en una única carpeta (hoy están duplicados en `schemas/` y `src/androidTest/assets/`); son idénticos por diseño.
3. Repoblar seguidos/actividad en el dispositivo antes de la siguiente verificación física.
4. Evaluar el ítem fallo-de-esquema: confirmar que la corrección de `slug NOT NULL` no rompe instalaciones previas (las instalaciones anteriores ya crearon la columna nullable; verificar tolerancia al abrir la app en un teléfono que haya instalado la v3 antigua).
5. Revisar que `RealCatalogTest` no se ejecute en CI sin control (abre red real y muta following).