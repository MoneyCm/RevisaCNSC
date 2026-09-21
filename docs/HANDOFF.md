## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# HANDOFF — Relevo de OpenCode a Codex

- **Fecha**: 2026-09-21 (última actualización al cierre de la sesión OpenCode).
- **Estado actual del proyecto**: Android local-first de vigilancia CNSC (Mérito Radar) funcional y verificado hasta el cierre de esta sesión. Consulta CNSC directamente (OkHttp + TLS corregido), persiste en Room (v3), detecta avisos locales, proyecta fechas conservadoramente y genera notificaciones locales. Backend FastAPI/PostgreSQL es tooling de referencia, no requisito de ejecución.
- **Último commit local**: `ee96b33` "Precisar estado de la base del dispositivo y nota de reinstalacion". Al cierre, `main` = `ee96b33` (2 commits adelante de `origin/main`); ver sección push.

## Resumen de la sesión (Tarea 2: migraciones Room instrumentadas)

Se completó la NEXT_TASKS 6 registrada como deuda ("tests instrumentados existentes tienen errores de código/esquema"):

1. **Diagnóstico y reparación de `MigrationTest.kt`**: no compilaba contra Room 2.6.1 (`MigrationTestHelper.TEST_DATABASE` inexistente, firma antigua `runMigrationsAndValidate(TEST_DB, true, ...)`; reintento de asigne un `val`). Se reescribió la clase con `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LegacyRadarDatabase::class.java)` y la firma vigente `runMigrationsAndValidate(name, versionDestino, validateDroppedTables, migrations...)`.
2. **Activación de exportación de esquemas**: `exportSchema = true` en `@Database` (Data.kt) y bloque `kapt { arg("room.schemaLocation", "$projectDir/schemas") ... }` en build.gradle.kts. Generado `schemas/co.meritoradar.app.LegacyRadarDatabase/3.json` (fuente de verdad).
3. **Esquemas históricos derivados**: `1.json` (processes sin slug + following) y `2.json` (v1 + content_cache) reconstruyendo `createSql` desde los fields/affinity del 3.json (script de una sola vez en `%TEMP%\opencode\gen_schemas.py`, fuera del repo). Copiados a `src/androidTest/assets/co.meritoradar.app.LegacyRadarDatabase/` para que `createDatabase(name, version)` los cargue.
4. **Bug real encontrado y corregido**: el primer pase en dispositivo detectó que `MIGRATION_2_3` agregaba `slug` nullable (`TEXT DEFAULT ''`) mientras la entidad `Process.slug` (String) y el schema exportado la declaran `NOT NULL`. Corregido a `ALTER TABLE processes ADD COLUMN slug TEXT NOT NULL DEFAULT ''`.
5. **Verificación instrumentada en 8912c62d**: 3/3 tests OK (migrate1To2PreservesData, migrate2To3PreservesDataAndAddsSlug, migrateAllPreservesData) sobre la DB aislada `migration-test`. radar.db quedó en v3 con 27 procesos.
6. **Nota operativa importante**: el runner `connectedDebugAndroidTest` desinstala la app antes de instalar el APK de pruebas. En este dispositivo Xiaomi causó `INSTALL_FAILED_USER_RESTRICTED` y forzó la reinstalación. La ejecución final se hizo con `pm install -r` + `am instrument` (conservando la app). Requisito del teléfono: "Instalar vía USB" activo en opciones de desarrollador.

## Archivos principales modificados (esta sesión)

- `android/app/src/androidTest/java/co/meritoradar/app/MigrationTest.kt` — reescrito (API Room 2.6.1, 3 tests con datos sembrados).
- `android/app/src/main/java/co/meritoradar/app/Data.kt` — `exportSchema = true`; `MIGRATION_2_3` con slug `NOT NULL`.
- `android/app/build.gradle.kts` — bloque kapt `room.schemaLocation`/`room.incremental`/`room.expandProjection`.
- `android/app/schemas/co.meritoradar.app.LegacyRadarDatabase/{1,2,3}.json` — esquemas históricos y actual (nuevos, versionados).
- `android/app/src/androidTest/assets/co.meritoradar.app.LegacyRadarDatabase/{1,2,3}.json` — copia para carga en tests (nuevos, versionados).
- `docs/STATUS.md`, `docs/NEXT_TASKS.md`, `docs/DECISIONS.md` (DEC-019), `docs/MANUAL_TESTING.md`, `docs/PROJECT_STATUS.md` — registros del bloque.
- Nuevos en este handoff: `docs/TASKS.md`, `docs/HANDOFF.md`.

## Funcionalidades nuevas o corregidas

- **Corregido**: suite de migraciones Room que no compilaba (deuda documentada desde bloques anteriores).
- **Corregido**: `MIGRATION_2_3` producía columna `slug` nullable incoherente con la entidad; ahora `NOT NULL DEFAULT ''` (bug latente de futuro, detectado por la propia suite).
- **No se inició** ninguna funcionalidad nueva: la sesión fue 100 % cierre de deuda de tests y verificación.

## Pruebas ejecutadas y resultados

- `.\android\gradlew.bat -p android assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` → **BUILD SUCCESSFUL**.
- Tests JVM: **56 tests, 0 fallos, 0 errores** (9 suites: ActivityRefresh 5, CatalogParser 4, CnscNetworkRegression 4, Links 2, Notice 11, ProcessActivity 10, ProcessIdentity 5, StageDetector 9, StageSummary 6).
- Lint: **0 errores, 19 advertencias** (todas preexistentes: dependencias antiguas, CustomX509TrustManager, DataExtractionRules, ObsoleteSdkInt, KaptUsageInsteedOfKsp).
- Instrumentado en dispositivo 8912c62d (AM instrument): **MigrationTest 3/3 OK**.
- No se ejecutó `RealCatalogTest` (requiere red real, catálogo "Territorial 12", muta following).

## Problemas encontrados

1. **`INSTALL_FAILED_USER_RESTRICTED` en Xiaomi** mientras se reintentaba `connectedDebugAndroidTest`: resuelto activando "Instalar vía USB". El runner de Gradle desinstala la app antes de instalar la de prueba; se evitó usando `pm install -r` + `am instrument`.
2. **La app principal estaba desinstalada al iniciar la sesión** (solo variante QA presente). La reinstalación recreó `radar.db` de cero: 27 procesos reobtenidos por el catálogo, pero **following, caché activity:* y notice_state quedaron vacíos**. Se documenta en STATUS/TASKS; no fue destrucción deliberada de datos para pasar una migración.

## Problemas todavía pendientes (NOT VERIFIED)

- Entrega física de novedad crítica real con pantalla bloqueada/app cerrada (worker → outbox → bandeja → deep link).
- Paginación real de un micrositio con más de 3 páginas (hoy DIAN 2676 tiene una página).
- Diagnóstico de fallo real por micrositio observado en el teléfono.
- Migración de una base histórica real con datos previos del usuario.
- Comportamiento prolongado bajo Doze/restricciones OEM.
- Recordatorio de apertura/cierre y cancelación por aplazamiento en producción real.

## Decisiones técnicas tomadas y sus motivos

- **DEC-019 — Migraciones Room instrumentadas como verificación real** (registrada en DECISIONS.md): la suite usa DB aislada `migration-test` y esquemas exportados como fuente de verdad; detectó y obligó a corregir la incoherencia NOT NULL de `slug`. Motivo: una migración que produce un esquema distinto del declarado es un bug silencioso a futuro.

## Cambios de arquitectura

- Ninguno. Se mantiene la arquitectura Android local-first (ver docs/ARCHITECTURE.md). El cambio de `exportSchema = false → true` no altera el esquema, solo lo hace versionable.

## Tareas iniciadas pero incompletas

- Ninguna funcionalidad quedó a medias. La Tarea 2 (migraciones) está DONE. El siguiente bloque (NEXT_TASKS 7: diagnóstico y preferencias) no se inició por instrucción de no comenzar funcionalidad durante el handoff.

## Riesgos o regresiones posibles

- **Datos del dispositivo reseteados** en esta sesión (following/actividad vacíos): repoblar seguidos antes de depender de vigilancia real (ver TASKS.md 8).
- Los schemas históricos 1.json/2.json son **derivados** del 3.json actual reconstruyendo createSql; no existía exportación previa. Si la validación futura fallara, revisar esos JSON (no regenerar a ciegas).
- Actualizar Room (2.6.1 → 2.8.x) o migrar kapt → KSP cambia la ruta de generación de schemas; volver a verificar `schemaLocation` y los assets de test.
- No ejecutar `connectedDebugAndroidTest` en el dispositivo físico sin antes reponer seguidos: desinstala/reinstala la app y vuelve a vaciar datos.

## Siguiente tarea recomendada

NEXT_TASKS 7 — **Diagnóstico y preferencias**: revisar qué existe (repository ya expone estado del worker/health; ajustes tienen notificación de prueba), luego completar con estado real (última revisión, errores, alcance de fuentes, frecuencia y preferencias). Recomendable antes: repoblar seguidos en 8912c62d (TASKS.md 8) para que el diagnóstico muestre datos reales.

## Tareas que requieren especialmente revisión de Codex

1. Confirmar el push a origin/main del estado local (2 commits: a4b8553, ee96b33) y que `main == origin/main`.
2. Decidir si versionar también los JSON de esquema en una única carpeta (hoy están duplicados en `schemas/` y `src/androidTest/assets/`); son idénticos por diseño.
3. Repoblar seguidos/actividad en el dispositivo antes de la siguiente verificación física.
4. Evaluar el ítem fallo-de-esquema: confirmar que la corrección de `slug NOT NULL` no rompe instalaciones previas (las instalaciones anteriores ya crearon la columna nullable; verificar tolerancia al abrir la app en un teléfono que haya instalado la v3 antigua).
5. Revisar que `RealCatalogTest` no se ejecute en CI sin control (abre red real y muta following).