## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# Estado y registro de verificaciones

## Verificación no destructiva de estado operativo — 2026-09-21 (orquestador temporal)
- Comprobado en 8912c62d sin reinstalar, sin borrar datos, sin cambiar following y sin connectedDebugAndroidTest (extracción binaria db+wal+shm vía run-as y lectura solo+PRAGMA).
- radar.db producción: integrity ok, user_version 3, identity_hash a2e692d177faf9fcc2640d282ee5fd06, 27 procesos.
- Seguidos activos: 3 (DIAN 2676 `dian-2676`, Aerocivil Primera Fase `aerocivil-primera-fase`, Corporaciones Autónomas Regionales CAR `corporaciones-autonomas-regionales-car`) con activity_check y activity: recientes en 21:00Z-21:01Z (38/15/5 publicaciones) y notice_state inicializado (9 avisos, 9 eventos, pending 0, firedReminders 0). PGN 2407 y ESE2 ya no están seguidos (decisión del usuario en la app; no se modificó following por ADB).
- WorkManager producción: jobs SystemJobService #u0a424/12-16 ejecutados con jobFinished en los últimos ~4 min y job periódico reprogramado a ~15 min (Minimum latency +14m59s, backoff 30 s); sin errores visibles.
- Bases independientes: producción (u0a424) y QA (u0a421) mantienen carpetas propias y archivos separados; mismo esquema (identity_hash idéntico) pero contenido distinto (solo producción tiene following). QA: user_version 3, integrity ok, 27 procesos, following 0.
- Conclusión: la doc previa (TASKS 8) que describía following/actividad "vacíos" quedó obsoleta; se actualizó TASKS.md 8. Estado listo para NEXT_TASKS 7 sin acción física previa de repoblamiento.
- NOT VERIFIED: que los seguidos actuales sean exactamente los deseados (PGN/ESE2 fuera), y verificación física de alertas (fuera de alcance de este bloque).

## Bloque 2026-09-21 — Correcciones de la auditoría QA de Devin sobre ac8e6b4 (orquestador temporal)
- Previo autorización explícita y verificación de HEAD == origin/main == ac8e6b4 con árbol limpio. Solo se corrigen hallazgos confirmados de la auditoría sobre el commit ac8e6b4; sin funcionalidades nuevas.
- Hallazgo 1 (WorkInfo vigente): `observePeriodicWork()` dejó de usar `firstOrNull()` sobre todos los trabajos del nombre único. Mapea cada `WorkInfo` a `PeriodicWorkCandidate` y `Diagnostics.selectActivePeriodic` selecciona determinísticamente el activo (ENQUEUED o RUNNING), ignorando históricos CANCELLED/SUCCEEDED/FAILED; un histórico terminado ya no produce un falso AT_RISK si hay otro trabajo activo. Sin trabajo activo → null (ausencia de programación) que `status()` interpreta como AT_RISK (con PAUSED prevaleciendo si la vigilancia está en pausa).
- Hallazgo 2 (refresco del entorno Android): `_androidEnvironment` dejó de emitir una única vez; ahora es un `MutableStateFlow` re-lecto con `repository.refreshAndroidEnvironment()`, sin polling continuo. Se dispara con `LifecycleEventEffect(ON_RESUME)` (regreso a la app desde Ajustes del sistema) y con `LaunchedEffect(tab)` al entrar a la pestaña Ajustes. `combine` usa el StateFlow.
- Hallazgo 3 (pausa/reanudación): `schedule` mantiene `KEEP` para el arranque normal (no recrea) y se extrajo `periodicRequest(interval)`. Nuevo `CnscMonitoringWorker.resume` usa `ExistingPeriodicWorkPolicy.UPDATE` con el intervalo persistido: actualiza el pendiente o re-encola tras una cancelación, dejando exactamente un trabajo periódico activo; no modifica following ni Room. `setMonitoringPaused(false)` ahora llama `resume` (ya no `schedule`). No se usa REPLACE (deprecado).
- Hallazgo 4 (zona horaria): verificado y documentado, NO es bug. `checkedAt` se genera siempre como `Instant.now().toString()` (ISO-8601 UTC con Z) en CnscMonitoringWorker/Data/NoticeMonitor, se persiste como String y se renderiza en MainActivity vía `OffsetDateTime.parse` → América/Bogotá; Diagnostics lo parsea con `Instant.parse`. No se modificó la lógica de zona horaria.
- Límite de `nextRunAtMillis` a 7 días: mejora opcional fuera del alcance del bloque; no se implementó.
- VERIFIED: `testDebugUnitTest + lintDebug + assembleDebug + assembleDebugAndroidTest` BUILD SUCCESSFUL; **77 tests JVM, 0 fallos** (5 regresiones nuevas en DiagnosticsTest: CANCELLED+ENQUEUED, CANCELLED+RUNNING, solo CANCELLED, lista vacía, además FAILED/SUCCEEDED históricos sin activo; refresco de entorno converge al cambiar permiso/canales; reanudar tras pausa deriva programa activo ENQUEUED; cambios sucesivos 15→30→60→15 conservan clamping y runsPerDay). Lint 0 errores / 19 advertencias preexistentes (sin nuevas).
- NOT VERIFIED (requiere verificación física en dispositivo): los triggers de ciclo de vida (`ON_RESUME` y entrada a Ajustes) refrescando permiso/canales/batería frente a cambios reales del sistema; el efecto real de KEEP/UPDATE sobre los jobs de WorkManager (reanudar tras cancelación y cambio de intervalo); `selectActivePeriodic` y regresiones están cubiertas por unitarias JVM.
- Deuda documental conservada: la capa de auditoría (cabecera de este archivo) describe "0 seguidos"; la verificación 2026-09-21 y TASKS 8 prevalecen por ser más recientes.

## Bloque 2026-09-21 — Diagnóstico y preferencias (NEXT_TASKS 7, orquestador temporal)
- Implementado por OpenCode como orquestador temporal (Codex ausente), previa autorización explícita; solo este bloque. Se verificó primero qué existía para no duplicar: `Health`/pod de `repository.refresh`, `observeWorkManagerStatus()` (WorkManager por TAG), `LocalNotifier.hasNotificationPermission/isChannelBlocked`, `SharedPreferences monitor_schedule` y prueba de notificaciones en Ajustes.
- **Diagnóstico** (pestaña Ajustes) con estado real de fuentes distintas:
  - `repo.diagnostics` = `combine(processes, following, activityChecks, noticeState, observePeriodicWork(), monitorInterval, monitoringPaused, androidEnvironment)` → `Diagnostics.build`, lógica pura en nuevo `Diagnostics.kt`.
  - Room: `notice_state` (última revisión de avisos, notices/events/pending, initialized) y `activity_check` (errores por micrositio). WorkManager: `getWorkInfosForUniqueWorkFlow` (estado, runAttempt, nextScheduleTimeMillis). Config Android: permiso + estado de los 3 canales (`LocalNotifier.channelStatuses()`), `PowerManager.isIgnoringBatteryOptimizations`.
  - Estado general con precedencia: PAUSED > AT_RISK (sin trabajo o en estado no activo) > WARMING_UP (sin primera revisión) > DEGRADED (fallo reciente de micrositio en 24 h o permiso de notificaciones denegado) > OPERATIONAL. Líneas de la UI incluyen última revisión, próxima ejecución, programación, seguidos (cantidad y nombres/slugs), fuentes vigiladas (catálogo/avisos/micrositios), errores recientes por micrositio, frecuencia configurada, estado de notificaciones, optimización de batería y ejecuciones diarias estimadas. Ningún dato se inventa: si falta, se muestra "sin dato"/explicación.
- **Preferencias** cumplibles por la arquitectura (sin backend, sin tocar `following` desde Ajustes):
  - Frecuencia 15/30/60/120 min: `CnscMonitoringWorker.updateInterval` persiste en `monitor_schedule` (`interval_minutes`) y reemplaza el trabajo único con `ExistingPeriodicWorkPolicy.UPDATE`; `schedule()` ahora lee la preferencia y `RadarApp` honra intervalo y pausa al arrancar. Sin prometer ejecución exacta: texto "WorkManager solicita ... Android puede retrasarla o agruparla. No es una cita exacta a los 15 minutos."
  - Pausar/reanudar vigilancia: `monitoring_paused_v1` en prefs; pausar cancela el trabajo único, reanudar lo reprograma. No modifica concursos seguidos (enlace "Elegir concursos" navega a la pestaña Concursos). No hay toggle global de notificaciones: se delega en Android (DEC-008/DEC-015) y el diagnóstico refleja el estado real.
- VERIFIED: `assembleDebug + testDebugUnitTest + lintDebug + assembleDebugAndroidTest` BUILD SUCCESSFUL; **72 tests JVM 0 fallos** (11 nuevos en `DiagnosticsTest`); lint 0 errores/19 advertencias preexistentes (sin nuevas). No se ejecutó ni instrumentó en dispositivo (prohibido reinstalar producción; las pruebas instrumentadas solo se compilaron).
- NOT VERIFIED: pantalla Ajustes con datos reales del teléfono, efecto de cambio de intervalo sobre los jobs de WorkManager en el dispositivo, y comportamiento de pausa/reanudar sobre el trabajo periódico real. La lógica de derivación de estado está cubierta por unitarias (precedencia, ventana de 24 h, clamping del periodo, conteos, próxima ejecución).
- Deuda documental: la capa de auditoría (arriba) aún describe el dispositivo con "0 seguidos"; la verificación 2026-09-21 y este bloque (TASKS 8) son la fuente que prevalece por ser más reciente.

## Último bloque — robustez del historial del micrositio, 2026-09-21
- Consulta manual (repository.detail) descarga la primera página una sola vez: nuevo `ProcessMicrositeReader` comparte la primera respuesta entre identidad y actividad, eliminando la doble descarga anterior (`fetch` de identidad + `fetch` de `parseAll`).
- `parseAll` ahora además recibe `firstPageHtml` (página ya descargada para reutilizar) y `pause` (por defecto `delay(2000)`); la pausa de 2 s separa las solicitudes sucesivas a CNSC. Este recorrido es el único lugar que solicita páginas posteriores, por lo que la pausa aplica también a la consulta manual.
- Fracaso de una página posterior ya no se traga: propaga (los "fallos tardíos tolerados" de la política anterior eliminados). `ActivityRefresh.run` mantiene su regla: ante Exception no-cancelación reporta error y NO guarda, conservando el historial guardado; el reader no persiste nada a medias por pares identidad+actividad.
- `CancellationException` se propaga tanto en `parseAll` como en `ActivityRefresh.run` (no se guarda ni se reporta éxito), evitando que una cancelación deje un resultado parcial.
- Cada publicación conserva la URL real de la página donde se obtuvo: `collect` guarda `sourceUrl` por publicación y `summarize` usa esas URLs en lugar de la inicial en todo `publications`.
- Regresión aprobada en `ProcessActivityTest` (15 tests): p1 OK + p2 fallida propaga; historial completo previo + consulta parcial conserva `stored` y reporta error; cancelación durante paginación sin guardar/reportar; URLs de evidencias por página (2 y 3); primera página descargada una vez en consulta manual con pausa de 2000 ms; recorrido de 3 páginas normales completo con pausas.
- VERIFIED: assembleDebug + testDebugUnitTest + lintDebug + assembleDebugAndroidTest BUILD SUCCESSFUL; 61 tests JVM 0 fallos (15 ProcessActivityTest); lint 0 errores/19 advertencias (sin nuevas); MigrationTest 3/3 en 8912c62d vía am instrument (sin desinstalar producción); radar.db real intacta (v3, 27 procesos, 0 seguidos, integrity ok).
- NOT VERIFIED: página posterior fallida en dispositivo real (simulada por unitarias), recorrido real >3 páginas, y efectos con red real (la pausa se validó con inyección, no con cronómetro físico).
- Deuda previa conservada: PROJECT_STATUS menciona "sin remoto configurado"; el remoto origin sí está configurado y main está sincronizado con origin/main, corresponde corregir la redacción documental.

## Último bloque — migraciones Room instrumentadas, 2026-09-21
- Corregido MigrationTest.kt (no compilaba contra Room 2.6.1: referencia a TEST_DATABASE inexistente y firma antigua de runMigrationsAndValidate(boolean)). Reescribió con MigrationTestHelper(InstrumentationRegistry, LegacyRadarDatabase::class) y la firma vigente runMigrationsAndValidate(name, versionDestino, validateDroppedTables, ...migraciones).
- Activado exportSchema = true y room.schemaLocation para generar android/app/schemas/co.meritoradar.app.LegacyRadarDatabase/3.json (fuente de verdad). Derivados 1.json (processes sin slug + following) y 2.json (+ content_cache) reconstruyendo createSql desde los fields del 3.json. Copiados a src/androidTest/assets/... para que createDatabase(name, version) los cargue.
- El primer pase en dispositivo encontró un error real de esquema: MIGRATION_2_3 agregaba slug nullable ("TEXT DEFAULT ''") mientras la entidad Process.slug (String) y el schema exportado la declaran NOT NULL; corregido a "TEXT NOT NULL DEFAULT ''". La validación de MigrationTestHelper compara TableInfo contra el JSON destino.
- Suite instrumentada: three tests (1→2, 2→3, 1→3 completa) siembran datos propios y verifican conservación (filas de processes/following/content_cache sobreviven; slug queda '') usando la DB aislada "migration-test", sin tocar radar.db.
- VERIFIED: assembleDebug, assembleDebugAndroidTest, testDebugUnitTest y lintDebug BUILD SUCCESSFUL; 3/3 tests instrumentados OK en 8912c62d; 56 tests JVM de regresión; lint 0 errores/19 advertencias. La suite usa solo la DB "migration-test"; radar.db quedó en v3 con 27 procesos reobtenidos por el catálogo.
- IMPORTANTE (estado del dispositivo): al iniciar este bloque la app principal co.meritoradar.app NO estaba instalada en 8912c62d (solo la variante QA). La reinstalación (que requirió activar "Instalar vía USB" en Xiaomi) creó una radar.db nueva: versión 3 y 27 procesos presentes, pero el historial de seguidos (following), la caché de actividad y notice_state quedaron vacíos/reseteados. La verificación de migraciones usa datos sembrados propios, no los datos previos del seguimiento. No se borraron datos deliberadamente para hacer pasar una migración; quedó el efecto de reinstalación.
- El bloqueo de instalación de Xiaomi (INSTALL_FAILED_USER_RESTRICTED) se resolvió activando "Instalar vía USB"; el runner connectedDebugAndroidTest desinstala la app antes de instalar la de pruebas, por lo que la ejecución se hizo con pm install -r + am instrument (evita desinstalar).
- NOT VERIFIED: validación en CI/automática (solo manual en dispositivo); migración de una base histórica real con datos previos del usuario (los tests siembran sus propios datos, no datos CNSC reales); paginación real >3 páginas y diagnóstico de fallo real por micrositio siguen pendientes de bloques anteriores.
- Deuda previa conservada: PROJECT_STATUS menciona "sin remoto configurado"; el remoto origin sí está configurado y main está sincronizado con origin/main, corresponde corregir la redacción documental.

## Último bloque — historial de publicaciones del micrositio, 2026-09-21
- Implementado `ProcessActivityParser.parseAll`: lee hasta tres páginas del micrositio oficial (paginador Drupal `main .pager__item--next a`, mismo host `cnsc.gov.co`, mismo path, filtro =64) y conserva todas las publicaciones fechadas (título, resumen, fecha Bogotá y URL de fuente) en `ProcessActivity.publications`, ordenadas desc y con el último aviso como campos principales. `parse()` conserva el contrato de una página. Un fallo de lectura de la primera página propaga (fallo visible); un fallo de página posterior conserva lo ya recogido.
- El historial es informativo y conservador: la UI del detalle muestra hasta 5 publicaciones con su fecha más la nota "no genera alertas nuevas". No se generan eventos de aviso desde publicaciones históricas ni alertas retroactivas; los recordatorios de ventanas siguen dependiendo de las fechas de etapas CONFIRMED/SCHEDULED revisadas en la corrida.
- Integración: NoticeMonitor (worker) y repository.detail (Actualizar detalle) usan parseAll sobre seguidos; la rotación acotada de ActivityRefresh y el límite de páginas evitan sobrecargar el micrositio.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug BUILD SUCCESSFUL; 56 tests JVM, 0 fallos/errores (4 nuevos en ProcessActivityTest: historial ordenado con fuente, nextPage restringida, seguimiento de páginas acotado con merge, tolerancia a fallo tardío de página); lint 0 errores/19 advertencias.
- VERIFIED en 8912c62d: install -r del APK nuevo conservando 27 procesos/4 seguidos; tras Actualizar detalle en DIAN 2676 y una corrida del worker, la caché activity:10260d7a conserva checked=15 publicaciones=15 con historial fechado ordenado y la vista muestra la primera publicación histórica con su fecha y la nota informativa; Aerocivil (38), Antioquia 3 (29) y PGN (12) quedaron reescritos con publications. No se inyectaron datos CNSC ficticios.
- NOT VERIFIED: paginación real de un micrositio con más de tres páginas (hoy DIAN 2676 tiene una sola página sin paginador; la ruta de paginación está cubierta por unitarias), y Visual >5 publicaciones (la UI limita a 5). La actualización completa del historial de seguidos depende de la rotación periódica.
- Deuda previa conservada: PROJECT_STATUS menciona "sin remoto configurado"; el remoto origin sí está configurado y main está sincronizado con origin/main, corresponde corregir la redacción documental.

## Último bloque — aislamiento de micrositios, 2026-09-21
- ActivityRefresh permite continuar cuando falla la lectura o el parser de un micrositio seguido; conserva su caché y no bloquea la consolidación de avisos del ciclo.
- Diagnóstico persistido en activity_check por proceso, con nombre y fecha del intento, visible para los seguidos. El éxito periódico o desde Actualizar detalle limpia el error. Health marca degraded cuando hay diagnósticos pendientes de seguidos.
- Rotación limitada a tres micrositios por ciclo y 30 minutos desde el último intento, incluidos fallos, para no acaparar el turno. Cancelación y errores de base de datos se propagan.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug BUILD SUCCESSFUL; 52 tests JVM, 0 fallos/errores, lint 0 errores/19 advertencias. Cinco tests nuevos cubren continuidad/caché, recuperación, cancelación, persistencia fallida y rotación.
- VERIFIED: install -r en 8912c62d y apertura; worker d49157d6-903c-494e-b663-0628d34bc90f SUCCESS a las 09:18:55. No se inyectaron datos ni fallos ficticios en producción.
- NOT VERIFIED: representación y recuperación de un fallo real por micrositio en dispositivo; lógica ejercitada con adaptadores de test. El índice principal y los fallos internos aún pueden detener el ciclo, intencionadamente.
- Historial: revisión de diseño completada; el lector de actividad sigue conservando el último aviso fechado de una página. Persistir más publicaciones y ampliar paginación es el siguiente bloque, no implementado aquí.

## Último bloque — cierre de actividad DIAN, 2026-09-21
- Conservados los avances existentes de outbox, revalidación y recordatorios; el lector ProcessActivity ya estaba integrado al retomar.
- Corregido el encabezado del detalle: si existe actividad del micrositio, muestra Último aviso en lugar de Sin etapa confirmada. Mantiene título, publicación, revisión y enlace, sin afirmar etapa general.
- Añadidas seis pruebas del lector: ejemplo sanitizado DIAN, distinción discapacidad/VRM, hitos, fechas futuras/imposibles, identidad, estructura y categoría.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug BUILD SUCCESSFUL; 47 tests JVM, cero fallos/errores; lint 0 errores/19 advertencias.
- VERIFIED: install -r en 8912c62d, catálogo conserva 27 procesos. Detalle DIAN 2676 muestra Último aviso: Reclamaciones sobre certificado de discapacidad y sigue marcado Siguiendo. Título/fecha oficial observados: listado de respuestas a reclamaciones, 1 junio 2026 12:42.
- Alcance: última publicación fechada de la página consultada; no prueba etapa vigente global ni cubre todo el historial. No se confirmó un anuncio general de inicio de VRM para DIAN. Ver SOURCES.
- Deuda: un fallo al leer un micrositio seguido puede interrumpir la consolidación de avisos del ciclo; aislar errores por fuente conservando caché y un diagnóstico visible.

## Último bloque — recordatorios de apertura/cierre (NEXT_TASKS 5), 2026-09-20
- Implementado NoticeReminder en NoticeEngine: candidatos por transcurso del tiempo, no por detección de fechas. Solo ventanas CONFIRMED/SCHEDULED con inicio y cierre; apertura se recuerda hasta 2 días antes del inicio, cierre hasta 2 días antes del fin y solo si la apertura ya empezó (openDays <= 0). La fecha cuenta en Bogotá.
- Lógica pura y testeada: candidates() y key() (hash estable por proceso/etapa/tipo/fechas). Aplazamiento o cambio de fechas produce una clave nueva, de modo que la ventana nueva puede recordarse y el recordatorio anterior no se reemite con datos viejos.
- Integración en NoticeMonitor.run tras merge: genera eventos *_OPEN / *_CLOSING_SOON (labels ya existentes en eventLabel) con prioridad IMPORTANT, evidencia con stage_id/start/end, solo para concursos seguidos, y solo cuando la fuente de la etapa fue revisada en esta misma corrida (freshNoticeUrls). firedReminders persistido en NoticeState evita reemitir la misma ventana; el descarte de la outbox por cambio de ventana o REVIEW_REQUIRED la cancela antes de entregar.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug BUILD SUCCESSFUL; 41 tests JVM, 0 fallos (+2 en NoticeTest: candidatos solo cerca de ventanas confirmadas/programadas y clave estable/distinta por ventana); lint 0 errores / 19 advertencias.
- VERIFIED en 8912c62d: adb install -r conservando 27 procesos/4 seguidos; arranque sin crash; worker periódico y manual SUCCESS; notice_state intacto (8 avisos, 8 eventos, pending=0) y firedReminders persistido como {}. Ninguno de los 4 seguidos tiene hoy una ventana confirmada a ≤2 días, por lo que no se generan recordatorios reales ni se fabrican.
- NOT VERIFIED: entrega física de un recordatorio de apertura/cierre con una ventana oficial real cerca del día (hoy no existe tal ventana en seguidos; la generación está probada por lógica y unitarias, no por dispositivo). Recordatorio desechado por aplazamiento real en producción y notificación real con pantalla bloqueada siguen NOT VERIFIED.
- Falta de NEXT_TASKS 5: recordatorios por compuertas temporales ya implementados; la deduplicación y cancelación ante cambios/aplazamientos queda cubierta por clave estable y revalidación de outbox, pendiente de verificación física.

## Último bloque — revalidación de avisos antiguos de seguidos (NEXT_TASKS 4), 2026-09-20
- Implementado en NoticeMonitor/NoticeEngine: revalidación acotada por rotación (6 avisos/run) de los avisos de concursos seguidos fuera de la ventana reciente recién recorrida. Usa conditional GET con los validators (ETag/Last-Modified) persistidos; 304 no toca contenido, 200 re-parsea y merge genera NOTICE_UPDATED con evidencia solo si cambia el semanticKey, y no fabrica elegibilidad para ventanas pasadas.
- NoticeState.guarda revalidatedAt (map url→ISO de última revalidación) para la rotación LRU por persistencia; merge preserva el campo y NoticeMonitor hace .copy(validators, revalidatedAt) tras consolidar. Un fallo de revalidación registra la hora y continúa sin corromper notice_state.
- Selector puro aislado en NoticeRevalidator.staleCandidates: solo seguidos, excluye avisos vistos en la corrida actual, ordena por revalidatedAt y limita a MAX_STALE_PER_RUN. La revalidación de avisos antiguos sigue compatible con la deduplicación por semanticKey de merge (no se generan duplicados ni eventos nuevos por URLs vistas de nuevo).
- VERIFIED: assembleDebug testDebugUnitTest lintDebug BUILD SUCCESSFUL; 39 tests JVM, 0 fallos (3 nuevos en NoticeTest: límite/selección, rotación LRU acotada, revalidación sin duplicados y NOTICE_UPDATED con evidencia); lint 0 errores / 19 advertencias.
- VERIFIED en 8912c62d: adb install -r del nuevo APK conservando 27 procesos y 4 seguidos; arranque sin crash; sync manual worker SUCCESS; notice_state regenerado con mismo ids y pendiente vacío; nuevo campo revalidatedAt persistido rel=0 entradas porque los 8 avisos del estado fueron re-fetcheados en la ventana reciente (comportamiento correcto: no había candidatos stale). Un RETRY del periódico (22:29Z) correspondió a un fetch lento (respuesta 14,7 s cerca de la instalación) y no perdió datos.
- NOT VERIFIED: firma física de la revalidación de un aviso antiguo real de un seguido que cambie fuera de la ventana reciente (hoy no existen avisos antiguos fuera de la ventana en el estado; el camino de candidato vacío es el único ejercitado en dispositivo). La notificación crítica física del recorrido worker→outbox→bandeja sigue sujeta a una publicación oficial real con ventana confirmada.
- Sigue pendiente de NEXT_TASKS 4 original: probar paginación del índice en una situación real de más de tres páginas nuevas (en la práctica la cobertura de tres páginas actúa como freno) y revisar ambigüedad de fechas que no son ventanas de inscripción (no es parte de este bloque).

## Último bloque — recuperación de red verificada en dispositivo, 2026-09-20
- NEXT_TASKS 3 (vigilancia prolongada y recuperación): verificada físicamente la recuperación de red en 8912c62d sin pérdida de datos y con reintentos conservando estado.
- Procedimiento: snapshot Room (radar.db + radar.db-wal) → modo avión ON → ejecución forzada del worker (#168) falló en ~28 ms sin tocar los 27 procesos ni los 4 seguidos → modo avión OFF → ejecución manual (apertura de la app) terminó SUCCESS con catálogo HTTP 200 y lastCheckedAt renovado a 03:01Z en los 27 procesos, notice_state regenerado y mismos ids de procesos/seguidos (sin duplicados ni borrados).
- Lección de extracción: cat databases/radar.db sin copiar -wal ni -shm muestra datos viejos; la comparación correcta requiere los tres archivos o un checkpoint. El snapshot inicial sin WAL ocultaba escrituras recientes.
- Backoff/constraints observados en el estado del worker: intervalo 15 minutos, backoff LINEAR inicial 30 s, constraint CONNECTED; el job periódico quedó reprogramado tras la ejecución forzada.
- NOT VERIFIED: Doze prolongado y retrasos OEM/ahorro de batería (solo se documentan tiempos; no se promete puntualidad de 15 minutos), entrega crítica real con pantalla bloqueada y conservación física end-to-end de la outbox con canal bloqueado a través del worker.
- Sin cambios de código en este bloque: verificación y registro. El fix de outbox sigue siendo el commit 5d0dd44.

## Último bloque — instalación con fix de outbox en dispositivo, 2026-09-20
- App actualizada con `adb install -r` (debug, 5d0dd44) en 8912c62d conservando 27 procesos y 4 seguidos (Empresas Sociales del Estado 2, DIAN 2676, Aerocivil Primera Fase, PGN 2407 de 2022).
- VERIFIED: apertura correcta y ejecución real del worker tras reinstalar: lastCheckedAt renovado en los 27 procesos (02:50Z) y notice_state regenerado; 27 procesos, 4 seguidos, base intacta.
- Estado real de alertas: pending=[]; 8 eventos NOTICE_PUBLISHED con notify_eligible=false. Ninguno de los 4 seguidos tiene etapa de inscripción/recaudo confirmada y vigente, por lo que no existe hoy una alerta crítica entregable real.
- NOT VERIFIED: recorrido crítico real (worker → outbox → bandeja → deep link) queda pendiente de una publicación oficial nueva con ventana confirmada de un concurso seguido. No se inyectan datos CNSC ficticios.
- La prueba QA del canal bloqueado (bloque anterior) valida transporte; la entrega física crítica sigue supeditada a un evento real calificable.

## Último bloque — outbox conserva eventos cuando el canal está bloqueado, 2026-09-20
- Corregida deuda de NEXT_TASKS 2: NoticeMonitor retiraba un evento de la cola tras showEventNotification sin comprobar el canal respectivo; un canal en IMPORTANCE_NONE traga notify() sin excepción y el evento se perdía como si hubiera llegado.
- LocalNotifier.showEventNotification ahora devuelve Boolean: true solo si el post real llegó al canal; false si falta permiso global, el canal está bloqueado o el sistema rechaza el post. isChannelBlocked() reporta canal inexistente o IMPORTANCE_NONE como bloqueado; channelForPriority() centraliza el mapeo prioridad→canal.
- NoticeMonitor solo elimina el id de pending cuando showEventNotification devuelve true; canal bloqueado, permiso ausente o camino crítico no revalidado conservan el evento en la cola durable. showTestNotification reutiliza isChannelBlocked para el canal general.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug: BUILD SUCCESSFUL, 36 tests JVM, 0 fallos; lint 0 errores / 19 advertencias.
- VERIFIED en 8912c62d (ADB streamed install del paquete QA): NotificationDeliveryTest OK (4 tests) incluyendo blockedChannelReportsDeliveryRefused, que verifica que un canal bloqueado reporta rechazo y no publica. Requirió desinstalar el paquete QA: Android no permite a la app restaurar la importancia de un canal puesto en NONE, por eso la prueba usa canales dedicados y no muta general_info.
- NOT VERIFIED: conservación física del outbox con canal bloqueado a través del worker completo y entrega oficial con pantalla bloqueada; la prueba QA valida el rechazo de transporte, no el recorrido end-to-end de NoticeMonitor.
- El trabajo VRM/actividad del agente anterior (ProcessActivity, rotación de micrositios seguidos) quedó sin commit previo preservado en el árbol, sin verificación de este bloque; no se mezcla con este cierre.

## Último bloque — pruebas aisladas de notificación, 2026-09-20
- Corregida colisión de identidad: NotificationManager usa eventId completo como tag y el PendingIntent incorpora eventId en su URI. Dos strings con el mismo hash ya no se reemplazan.
- Variante QA opcional (.qa), con suite propia en src/notificationTest: no inserta datos ficticios en Room. Las pruebas instrumentadas legacy de migración siguen pendientes y no se ejecutan en esta suite.
- VERIFIED: assembleQa y assembleQaAndroidTest. En 8912c62d, NotificationDeliveryTest: OK (3 tests): repetición, eventos Aa/BB con mismo hash e intents distintos, apertura de MainActivity con processId y eventId correctos.
- Primera ejecución: 2/3; el conteo incluía un aviso adicional. Ajustado para contar solo tags de eventos y excluir resúmenes de agrupación; segunda ejecución 3/3. No se modificó la lógica productiva para ocultar ese fallo de conteo.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug, 36 tests JVM aprobados, lint 0 errores/19 advertencias. App personal actualizada con install -r; UI conserva 27 procesos. QA detenida al terminar.
- NOT VERIFIED: representación del detalle de un concurso real desde una alerta, selección visual del evento, entrega de novedad crítica con pantalla bloqueada, Doze prolongado y recuperación offline. La prueba de PendingIntent valida destino/identificadores, no esas condiciones.
- Deuda detectada: NoticeMonitor comprueba permiso global, pero no bloqueo por canal antes de retirar un evento de la cola; revisar acuse de entrega y conservación de pendientes en ese caso.

## Último bloque — notificación de diagnóstico y periodo natural, 2026-09-20
- Ajustes permite enviar una notificación silenciosa explícitamente de prueba y abrir la configuración Android. Informa bloqueo global/canal general y no afirma entrega física solo porque notify retorne.
- VERIFIED: assembleDebug testDebugUnitTest lintDebug, BUILD SUCCESSFUL; 36 tests JVM, cero fallos/errores. APK instalada con install -r conservando 27 procesos.
- VERIFIED en 8912c62d: al pulsar el botón, la bandeja muestra TEST - Mérito Radar y su texto de prueba; NotificationRecord id=9999, canal general_info. Repetición conserva un solo aviso activo. Esto verifica transporte manual, no la outbox de eventos reales.
- VERIFIED parcial de segundo plano: sin abrir la app ni forzar jobs durante la observación, consulta automática 20:38:14–20:38:34 con HTTP 200 y worker 875ae58f-4bc1-4084-9ab0-4448640b8146 SUCCESS. Al comprobar se observó mWakefulness=Asleep. Job 154 volvió a programarse con demora de 15 minutos.
- NOT VERIFIED: nueva alerta de evento oficial recibida con pantalla bloqueada/app cerrada, deep link desde esa alerta, recuperación offline y comportamiento prolongado/Doze. No equiparar una ejecución observada con garantía de puntualidad.
- Compilación instrumental/migraciones sigue pendiente. No se añadieron datos CNSC ficticios ni se cambiaron favoritos.

Entrada de relevo: [PROJECT_STATUS.md](PROJECT_STATUS.md). Prioridades: [NEXT_TASKS.md](NEXT_TASKS.md).
Los bloques históricos conservan afirmaciones antiguas: para la misma funcionalidad prevalecen las verificaciones posteriores. Contrastar pendientes con código y bloques recientes.

# DONE (registro histórico)
- Bloque DIAN en curso: identidad de convocatoria bajo demanda desde micrositio oficial, separación entre número/año/inscripción y acceso al historial. DIAN 2676 de 2025 tuvo ingreso en 2026; ampliación al 7 febrero verificada en /node/59797. Build y dispositivo pendientes.
- Investigación inicial: catálogo, Territorial 12, noticias, calendario, avisos y próximos procesos HTTP 200; robots.txt verificado. Parsers adicionales siguen pendientes.
- AGENTS.md y siete skills: validador oficial aprobado en todas las skills incluyendo android-background-monitoring.
- Backend catálogo: migración Alembic aplicada a PostgreSQL 17 real; 27 procesos CNSC persistidos.
- Parser de fechas español con rangos, año explícito y rechazo de fechas inválidas; probado, todavía no integrado a publicaciones.
- Segunda ingesta real: 27 procesos, cero eventos nuevos (sin duplicados).
- API levantada en localhost:8000: HTTP real devuelve 27 procesos, health y detalle probado.
- 27 tests backend aprobados (unitarios sin PostgreSQL); 26 tests skipped por falta de TEST_DATABASE_URL; ruff aprobado.
- Arquitectura migrada a local-first: Android debe funcionar sin backend propio; backend conservado como reference tooling.
- Skill android-background-monitoring creada con reglas para WorkManager, notificaciones locales y detección semántica.
- Android local monitor básico implementado: CnscHttpClient, CatalogParser, DateParser, CnscMonitoringWorker con WorkManager.
- APK Android compila exitosamente con arquitectura local-first inicial.
- LocalNotifier implementado: canales de notificación, permisos POST_NOTIFICATIONS, deep links.
- APK funcional generado: android/app/build/outputs/apk/debug/app-debug.apk
- Room migración corregida: versión 3 con MIGRATION_2_3 para campo slug en Process.
- APK reinstalada exitosamente en dispositivo real sin error de integridad Room.
- WorkManager status observation: implementado observación de estado de WorkManager en repository.
- Feedback de sincronización: mejorado para mostrar estado real del worker.
- Pruebas de paridad Kotlin: CatalogParserTest creado con 4 tests aprobados (validación URL, parser fechas, normalización).

# DOING
- Migración de arquitectura a Android local-first: WorkManager, parsers Kotlin, Room completo, notificaciones locales.
- Port de lógica Python a Kotlin: HTTP directo, descubrimiento, parsers, normalización, fechas, eventos, deduplicación.
- Segunda fase backend: avisos oficiales, fechas por modalidad/población, evidencias y cambios. Implementación en curso, todavía NOT VERIFIED.
- Android WorkManager: Implementación básica funcionando con detección de nuevos procesos y notificaciones.
- Android local parsing: CatalogParser implementado, DateParser portado de Python.
- Android local notifications: Canales creados, permisos solicitados, deep links implementados.
- Room migraciones: MIGRATION_1_2 y MIGRATION_2_3 implementadas para evolución de esquema.
- UI feedback: Observación de WorkManager y estado de sincronización mejorado.
- Pruebas de paridad: Tests unitarios Kotlin creados y aprobados para parsers básicos.

# NEXT
- LOCAL CHANGE DETECTION: Port de classifier.py y event_engine.py a Kotlin, fingerprints semánticos.
- DETECTOR DE PUBLICACIONES: Port de notice_monitor.py a Android, fechas por modalidad/población, evidencias múltiples.
- Android: navegación completa, DataStore, onboarding, estado de vigilancia, diagnóstico, preferencias de frecuencia.
- Pruebas de paridad avanzadas: portar fixtures Python completos y comparar resultados detallados.
- WorkManager completo: unique periodic work, exclusión entre periódico y manual, prueba en emulador.
- Tests de migración Room: crear y ejecutar MigrationTest en dispositivo.
- UI mejorada: mostrar health detallado, progreso real, errores específicos.

# BLOCKERS
- LOCAL CHANGE DETECTION: NOT IMPLEMENTED - Lógica de detección de cambios aún en Python.
- BACKGROUND TEST: NOT VERIFIED - WorkManager no probado en emulador/dispositivo.
- DEVICE TEST: PARTIAL - APK instalada y abre correctamente, notificaciones no probadas.
- Firebase no configurado: push NOT VERIFIED, pero ya NO es requisito para arquitectura local-first.
- Docker daemon inactivo: docker compose config --quiet aprobado; arranque de servicios NOT VERIFIED. PostgreSQL local permitió validar integración.
- SDK Android localizado con permisos ampliados; no está ausente. Toolchain provisional API 34 para APK manual.
- No es todavía una app de vigilancia lista para uso real. No usarla como garantía de avisos de inscripción.

## Deuda conocida
- Android lint: 0 errores, 13 advertencias (dependencias, dataExtractionRules, carpeta de icono y migración kapt→KSP).
- Pytest: 2 advertencias de deprecación de Starlette/TestClient.
- No se verificaron todavía offline, TalkBack, modo oscuro, proceso muerto, notificaciones locales ni navegación desde deep link.
- Backend tests: 26 tests requieren TEST_DATABASE_URL (PostgreSQL real) para ejecutarse completamente.
- Android WorkManager: Funcionalidad básica implementada pero sin prueba real en dispositivo/emulador.
- Room migration tests: ejecutados y aprobados en dispositivo 2026-09-21 (3/3); ver bloque migraciones Room instrumentadas.
- WorkManager status observation: implementado pero no probado en UI real.
- Pruebas de paridad: tests básicos creados, falta comparación con fixtures Python completos.


## Bloque 2026-09-20 — Reparación de actualización local verificada
- Diagnóstico TLS: servidor www.cnsc.gov.co entrega solo certificado final, emisor GeoTrust TLS RSA CA G1; Android falla por cadena incompleta.
- Se incorpora intermedio desde https://cacerts.digicert.com/GeoTrustTLSRSACAG1.crt, sin añadir raíces: validación por TrustManager de Android y hostname por OkHttp.
- Actualizar espera resultado real y muestra error; enlaces relativos normalizados, IDs estables, primera carga sin avisos masivos, exclusión de ingesta y frecuencia corregidas.
- VERIFIED: gradlew assembleDebug testDebugUnitTest lintDebug: BUILD SUCCESSFUL, 10 tests aprobados, lint 0 errores / 19 advertencias.
- VERIFIED: APK instalado con adb install -r en 8912c62d (Android 16), sin borrar datos.
- VERIFIED: dos ejecuciones reales tras apertura/reinicio, HTTP 200 CNSC y WorkManager SUCCESS. Room contiene 27 filas y 27 URLs distintas; segunda revisión conserva firstDetectedAt y actualiza lastCheckedAt.
- VERIFIED: UI muestra Catálogo CNSC · 27 procesos guardados y fecha de revisión real; catálogo visible.
- NOT VERIFIED: UI de error sin conexión, regresión completa de accesibilidad, notificaciones reales, deep links y ejecución periódica natural con app cerrada. Las pruebas TLS negativas se ejecutaron en JVM.
- Deuda: migraciones instrumentadas existentes tienen errores de código/esquema y no se ejecutaron; no confundir tests unitarios con instrumentados.
- Deuda: la programación periódica ya existente conserva su intervalo anterior por KEEP; el cálculo corregido aplica a nuevas programaciones. Observación global de estado/health de fondo y cache condicional persistida siguen pendientes.
- Deuda: avisos oficiales, cambios de fechas y notificaciones de inscripciones todavía no implementados. Este bloque resuelve descarga del catálogo, no completa toda la vigilancia.


## Bloque avisos locales — verificación parcial completada
- Parser de índice/artículo con asociación oficial única, fecha estricta Bogotá, cuerpo normalizado y enlaces PDF sin descargar adjuntos.
- Monitor de página reciente como baseline silencioso; incrementales hasta frontera conocida, máximo tres páginas y fallo visible sin adelantar estado si no alcanza cobertura.
- Eventos de publicación/cambio de contenido, evidencia anterior/nueva, deduplicación por contenido y proceso, cola durable para concursos seguidos.
- Cobertura limitada a avisos recientes indexados; revisión de modificaciones de avisos antiguos fuera de esa ventana y clasificación de fechas todavía pendientes.
- Bloque anterior verificado: compilación/lint aprobados, 16 tests pasaron; APK instalado. Dispositivo guardó 8 avisos, 8 eventos históricos, 0 pendientes y detalle para 7 concursos; dos workers SUCCESS. Entrega física de notificaciones y periódico natural aún NOT VERIFIED.

## Bloque fechas locales — compilado, probado e instalado
- StageDetector extrae intervalos explícitos de inscripción/recaudo, conservando modalidad, población y fuente. Desconocidos quedan UNCONFIRMED.
- Detalle proyecta fechas desde avisos Room existentes; no cambia esquema ni requiere backend.
- Compara fechas anteriores/nuevas y añade eventos deduplicados. Solo ventanas confirmadas, no vencidas y publicación reciente de concurso seguido pueden producir alerta crítica.
- Aplazamientos/suspensiones invalidan fechas para revisión; conflictos de igual fecha de publicación quedan UNCONFIRMED.
- Revalida estado y vigencia antes de entregar una alerta en cola; no entrega críticas de fuentes no revisadas en esa ejecución.
- VERIFIED: assembleDebug, testDebugUnitTest y lintDebug aprobados; 25 tests, 0 fallos; lint 0 errores / 19 advertencias. Regresión real Territorial 12 extrae recaudo/inscripción del 25 mayo al 12 junio de 2026 sin tratar esas fechas pasadas como vigentes.
- APK actualizado generado en android/app/build/outputs/apk/debug/app-debug.apk.
- VERIFIED: al reconectar 8912c62d, adb install -r terminó Success. App abierta sin borrar datos, catálogo y avisos HTTP 200, worker 4eea14b6-229f-4f32-b0c2-d5c4ad73c917 SUCCESS. UI muestra 27 procesos guardados.
- NOT VERIFIED: representación física de una tarjeta con fechas extraídas y entrega real de alerta crítica. Extracción de fechas comprobada en tests con fixture oficial, no equivale a notificación física.
- Recordatorios de apertura/cierre, entrega física con pantalla bloqueada y cobertura de avisos antiguos siguen pendientes.

## Bloque resumen de etapas en UI — compilado e instalado
- El catálogo y el detalle ya no muestran "etapa por confirmar" de forma fija.
- Los resúmenes se calculan desde las etapas guardadas por `StageDetector`: etapas publicadas confirmadas, fechas en revisión, etapas detectadas con datos incompletos o sin etapa confirmada.
- El catálogo usa las cachés locales `detail:*`; el detalle mantiene fechas, evidencia y enlace oficial.
- No se declara que una inscripción esté abierta sólo porque exista una ventana publicada.
- VERIFIED: `assembleDebug` exitoso e instalación `adb install -r` exitosa en 8912c62d, conservando datos.
- NOT VERIFIED: suite `testDebugUnitTest` no entregó resultado final en esta sesión por bloqueo de Gradle antes de ejecutar los tests; entrega física de notificaciones y comprobación visual de cada estado siguen pendientes.

## Bloque actividad oficial en resumen — compilado e instalado
- Diagnóstico en dispositivo: 27 detalles locales tenían `stages=0`, pero `notice_state` conservaba 8 avisos oficiales recientes.
- Los avisos reales incluían citación médica, resultados preliminares, listas de elegibles, continuidad y aplazamiento; no todos contienen ventanas de inscripción/recaudo.
- El resumen ahora usa también eventos oficiales clasificados: citación, resultados, lista de elegibles, suspensión y reanudación. No confunde una publicación con inscripción abierta.
- VERIFIED: `assembleDebug` exitoso, instalación `adb install -r` exitosa y apertura en 8912c62d conservando datos.
- Respaldo añadido: cualquier `NOTICE_PUBLISHED` no reconocido muestra "Aviso oficial publicado; etapa específica por confirmar", evitando confundir aviso existente con ausencia de información.

## Bloque estado de inscripción — compilado, probado e instalado
- Las ventanas oficiales confirmadas ahora se interpretan con fecha de Bogotá: próximas, abiertas hasta su fecha de cierre o cerradas.
- Aplazamientos y suspensiones muestran "Inscripciones aplazadas o en revisión".
- VERIFIED: `StageSummaryTest` 6/6, `assembleDebug` exitoso e instalación `adb install -r` exitosa en 8912c62d conservando datos.
- El estado sólo se afirma cuando existen inicio y cierre oficiales, modalidad/población confirmadas y no hay revisión pendiente.

## Verificación completa — 2026-09-20
- assembleDebug testDebugUnitTest lintDebug: BUILD SUCCESSFUL.
- Suite Android JVM: 36 tests, 0 fallos (CatalogParser 4, NetworkRegression 4, Links 2, Notice 6, ProcessIdentity 5, StageDetector 9, StageSummary 6).
- Lint: 0 errores, 19 advertencias.
- APK instalado con adb install -r en 8912c62d, conservando datos.
- Dispositivo obtuvo HTTP 200 para catálogo y avisos oficiales; WorkManager terminó SUCCESS.
- Pendiente físico: notificación visible con pantalla bloqueada/app cerrada y ejecución natural del periodo de 15 minutos. No se declara DONE para esas partes.

## Relevo entre agentes — 2026-09-20
- Añadidos PROJECT_STATUS como entrada y NEXT_TASKS como prioridades; STATUS sigue siendo el registro canónico.
- AGENTS incorpora trabajo secuencial, preservación de cambios y cierre verificable.
- Reportes JVM existentes: 36 tests, 0 fallos, 0 errores. Sin nueva compilación ni prueba física en este bloque documental.
- Git inspeccionado: main sin commits, archivos sin seguimiento y sin remotos. Punto de control inicial y respaldo pendientes; no se creó ni publicó un commit.

## Punto de control inicial — 2026-09-20
- Preparado código, fixtures, configuración de ejemplo y documentación para el primer commit local; identificar el resultado mediante git log -1.
- Lista de archivos revisada; .env, configuración local del SDK, compilaciones y cachés excluidas. Búsqueda de patrones de claves privadas/tokens conocidos sin coincidencias en los archivos revisados; no constituye auditoría exhaustiva de secretos.
- Sin cambios funcionales ni nueva ejecución de pruebas: se conserva la evidencia y las limitaciones del bloque anterior.
- Respaldo remoto pendiente. El commit conserva el trabajo, no convierte las verificaciones pendientes en DONE.
