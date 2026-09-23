## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# HANDOFF — Relevo de OpenCode (orquestador temporal) a Codex

- **Fecha**: 2026-09-23 (cierre documental del bloque de entrega local robusta; la verificación física del transporte de prueba sigue siendo la del 2026-09-21 por el usuario).
- **Estado actual del proyecto**: Android local-first de vigilancia CNSC (Mérito Radar) funcional y verificado hasta el cierre de esta sesión. Consulta CNSC directamente (OkHttp + TLS corregido), persiste en Room (v3), detecta avisos locales, proyecta fechas conservadoramente y genera notificaciones locales. Backend FastAPI/PostgreSQL es tooling de referencia, no requisito de ejecución. Ahora incluye diagnóstico en la app y preferencias de frecuencia/pausa cumplibles por la arquitectura, con los hallazgos de la auditoría QA de Devin corregidos. Incluye entrega local robusta (DEC-023): outbox durable con reintento pre-red, reentrega al abrir la app y detalle de pendientes en el diagnóstico (93 tests JVM 0 fallos; backend 27 passed/26 skipped).
- **Último commit local**: ver `git log -1`; este cierre documental 2026-09-23 se commitea y pushea con autorización del usuario en esta sesión. El push completado mencionado en los bloques previos corresponde al cierre 2026-09-21.
- **Contexto**: Codex estuvo ausente; OpenCode actuó como orquestador temporal con protocolo de relevo (leer AGENTS/TASKS/HANDOFF/PROJECT_STATUS/DECISIONS, verificar git antes de tocar, bloques verificables, commit+push+sync tras cada bloque). origin/main es la fuente de verdad y quedó sincronizado.

## Resumen del bloque 2026-09-23 — entrega local robusta (DEC-023)

1. **Outbox durable**: nuevo `Outbox.kt` con `OutboxReview` puro (DELIVER/KEEP/DROP) y `deliverOutbox` compartido que persiste tras cada entrega.
2. **Reintento pre-red**: `NoticeMonitor.run()` reintenta el outbox con lo guardado antes de la red y usa el flush compartido al final.
3. **Reentrega al abrir**: `LocalRadarRepository.deliverPendingNotifications()` vía `RadarViewModel.deliverPending()` en `ON_RESUME` (sin red).
4. **Diagnóstico**: `pendingEvents` con proceso/título por pendiente y acción sugerida (canal bloqueado vs reintento automático).
5. **Verificación (sin dispositivo)**: `testDebugUnitTest + lintDebug + assembleDebug` BUILD SUCCESSFUL; 93 tests JVM 0 fallos (6 nuevos); lint 0 errores/19 advertencias preexistentes. NOT VERIFIED: entrega física real con app cerrada/pantalla bloqueada, deep link real a evento y Doze prolongado (TASKS 2). Detalle en STATUS.

## Resumen del bloque previo — Correcciones de la auditoría QA de Devin (ac8e6b4)

1. **WorkInfo vigente**: `observePeriodicWork()` ya no usa `firstOrNull()` sobre todos los trabajos históricos del nombre único. Cada `WorkInfo` se mapea a `PeriodicWorkCandidate` y `Diagnostics.selectActivePeriodic` selecciona determinísticamente el trabajo activo (ENQUEUED o RUNNING); históricos CANCELLED/SUCCEEDED/FAILED no generan un AT_RISK falso si existe un trabajo activo. Sin trabajo activo → null (ausencia de programación), interpretada como AT_RISK salvo pausa (PAUSED prevalece). Regresiones: CANCELLED+ENQUEUED, CANCELLED+RUNNING, solo CANCELLED, lista vacía y FAILED/SUCCEEDED sin activo.
2. **Refresco del entorno Android**: `_androidEnvironment` es un `MutableStateFlow` re-lecto por `repository.refreshAndroidEnvironment()`; sin polling continuo. Disparado por `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` (regreso a la app desde Ajustes del sistema) y por `LaunchedEffect(tab)` al entrar a la pestaña Ajustes. El `combine` usa el StateFlow.
3. **Pausa/reanudación**: `schedule` conserva `ExistingPeriodicWorkPolicy.KEEP` para el arranque normal (no recrea); request extraído a `periodicRequest(interval)`. Reanudar usa el nuevo `CnscMonitoringWorker.resume` con `ExistingPeriodicWorkPolicy.UPDATE` y el intervalo persistido: actualiza el pendiente si existe o re-encola tras una cancelación, dejando exactamente un trabajo periódico activo; no toca following ni Room. No se usa REPLACE (deprecado). Regresiones: reanudar tras pausa deriva programa activo y cambios sucesivos 15→30→60→15 conservan clamping y runsPerDay.
4. **Zona horaria**: auditado y documentado, NO es bug. `checkedAt` se genera siempre como `Instant.now().toString()` (ISO-8601 UTC `Z`) y se renderiza en Bogotá con `OffsetDateTime.parse`; Diagnostics parsea con `Instant.parse`. No se modificó lógica de zona horaria.
5. **nextRunAt a 7 días**: mejora opcional fuera del alcance de este bloque; no implementada.

## Verificación física — transporte de notificación de prueba (usuario, 2026-09-21)

- El usuario confirmó en producción que **TEST - Mérito Radar** apareció físicamente en la **pantalla bloqueada** y que al tocarla **abrió correctamente Mérito Radar**; los 3 concursos seguidos permanecieron intactos y no se generó ninguna alerta CNSC ficticia.
- **VERIFIED**: notificación de prueba → pantalla bloqueada → apertura de Mérito Radar.
- **Sigue NOT VERIFIED**: generación real de la notificación mientras el teléfono ya está bloqueado/app cerrada (la prueba se envió desde la UI abierta); recorrido worker → cambio CNSC real → outbox → notificación; deep link real hacia concurso/evento concreto; Doze/restricciones OEM. La entrega crítica real (TASKS 2) permanece PENDIENTE; esta prueba valida el transporte manual, no la vigilancia automática.

## Verificación del bloque actual (correcciones QA)

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` → **BUILD SUCCESSFUL**.
- Tests JVM: **77 tests, 0 fallos, 0 errores** (5 regresiones nuevas en `DiagnosticsTest`; suite completa).
- Lint: **0 errores, 19 advertencias** (todas preexistentes, ninguna nueva).
- En el momento del cierre de este bloque no se había instalado ni instrumentado en 8912c62d (restricción explícita del usuario); los triggers de refresco y el efecto de KEEP/UPDATE quedaron NOT VERIFIED y fueron **verificados físicamente después por el usuario** (ver "Verificación del bloque previo (diagnóstico y preferencias)" y STATUS).
- Producción/QA intactas; no se tocó radar.db ni following; no se ejecutó connectedDebugAndroidTest.

## Resumen del bloque actual — Diagnóstico y preferencias (NEXT_TASKS 7)

1. **Diagnóstico** en Ajustes, desde datos reales y sin duplicar lo existente (`Health`, `observeWorkManagerStatus`, `LocalNotifier.hasNotificationPermission/isChannelBlocked`, prefs `monitor_schedule`):
   - Nuevo `Diagnostics.kt` con lógica pura: `Diagnostics.build` recibe procesos, seguidos, `activity_check`, `notice_state`, trabajo periódico (`getWorkInfosForUniqueWorkFlow`), intervalo, pausa y entorno Android (permiso, canales, batería) y deriva un `DiagnosticsSnapshot` + líneas de UI.
   - Estado general con precedencia: PAUSED > AT_RISK (sin trabajo o estado no activo) > WARMING_UP (sin primera revisión, no emite alertas) > DEGRADED (fallo de micrositio en 24 h o permiso denegado) > OPERATIONAL.
   - Muestra: última revisión (avisos y micrositios), próxima ejecución programada, programación actual, cantidad y nombres de seguidos, fuentes vigiladas (catálogo/avisos/micrositios), errores recientes por micrositio, frecuencia configurada, estado de notificaciones (3 canales), optimización de batería y ejecuciones diarias estimadas. Sin valores inventados: la falta de dato se muestra explícita.
2. **Preferencias** que la arquitectura local-first cumple:
   - Frecuencia 15/30/60/120 min: `CnscMonitoringWorker.updateInterval` persiste `interval_minutes` y reemplaza el trabajo único (`ExistingPeriodicWorkPolicy.UPDATE`); `schedule()` y `RadarApp.onCreate` reutilizan la preferencia (intervalo y pausa sobreviven reinicios). Mínimo real de WorkManager (15 min) aplicado con `coerceAtLeast`.
   - Pausar/reanudar vigilancia: `monitoring_paused_v1`; pausar cancela el trabajo único, reanudar lo reprograma.
   - No modifica `following` desde Ajustes (botón navega a Concursos); sin toggle global de notificaciones (se delega a Android, DEC-008/DEC-015); sin backend.
## Verificación del bloque previo (diagnóstico y preferencias)

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` → **BUILD SUCCESSFUL** (72 tests en ese momento; 77 tras las correcciones de este bloque).
- Tests JVM: **72 tests, 0 fallos, 0 errores** (11 nuevos en `DiagnosticsTest`: precedencia de estado, ventana de 24 h para errores recientes, clamping del periodo, conteos/nombres/slugs, próxima ejecución, líneas de UI, pausa).
- Lint: **0 errores, 19 advertencias** (todas preexistentes, ninguna nueva).
- **VERIFIED físicamente por el usuario (2026-09-21)** en Mérito Radar producción: pantalla Ajustes → Diagnóstico con datos reales del teléfono, persistencia del intervalo y cambios 15→30→60→15, pausa y reanudación, refresco del diagnóstico al regresar de la configuración Android, conservación de la base y datos existentes, worker funcionando y un único trabajo periódico activo al finalizar. No se ejecutó connectedDebugAndroidTest; la verificación fue de UI en producción sin borrar datos.
- Producción/QA intactas; no se tocó radar.db ni following.

## Pruebas y resultados — resumen completo del turno anterior (grabado aquí)

- Test JVM previos del checkpoint a1d6b21: 56 tests, 0 fallos, 0 errores (9 suites); lint 0 errores/19 advertencias; MigrationTest 3/3 en 8912c62d.
- NO ejecutar `connectedDebugAndroidTest` en el dispositivo físico: desinstala/reinstala la app y vacía datos (reponer seguidos antes).

## Problemas encontrados en este bloque

- **`INSTALL_FAILED_USER_RESTRICTED` (Xiaomi)** ya conocido: se resolvió en el turno previo activando "Instalar vía USB"; para verificación instrumentada se usa `pm install -r` + `am instrument`, no el runner de Gradle.
- **Estado limpiado del dispositivo** (following/caché activity/notice_state vacíos desde la reinstalación del turno de migraciones; no pérdida en este bloque). Verificación 2026-09-21: el usuario ya repobló parcialmente (3 seguidos: DIAN 2676, Aerocivil Primera Fase, CAR) y el worker quedó activo; PGN 2407 y ESE2 fuera de seguimiento. El diagnóstico nuevo lee ese estado real desde Room/WorkManager, pero en esta sesión no se instaló la nueva versión en el teléfono.

## Problemas todavía pendientes (NOT VERIFIED)

- Entrega física de novedad crítica real con pantalla bloqueada/app cerrada (worker → outbox → bandeja → deep link). El transporte manual de la notificación de prueba ya está verificado (pantalla bloqueada + apertura), pero la generación real desde el worker con la app cerrada y el deep link a concurso/evento NO.
- Paginación real de un micrositio con más de 3 páginas (hoy DIAN 2676 tiene una página).
- Migración de una base histórica real con datos previos del usuario.
- Comportamiento prolongado bajo Doze/restricciones OEM.
- Recordatorio de apertura/cierre y cancelación por aplazamiento en producción real.
- Fallo físico de página posterior y cronometría física de la pausa (validados con unitarias, no en dispositivo).

## Decisiones técnicas tomadas y sus motivos

- **DEC-021 — Diagnóstico honesto y preferencias cumplibles** (registrada en DECISIONS.md): diagnóstico solo de fuentes reales (Room/WorkManager/Android); dato faltante se muestra como desconocido, nunca se inventa; estado general con precedencia (pausada > at-risk > sin primera revisión > degradada > operativa). Preferencias limitadas a lo que la arquitectura cumple: frecuencia 15/30/60/120 min (UPDATE del trabajo único, persistido) y pausar/reanudar; Ajustes no toca following (solo enlace), no hay toggle global de notificaciones (se delega a Android) y no se promete ejecución exacta a los 15 minutos.
- **DEC-020 — Historial robusto: compromiso total o nada** (registrada en DECISIONS.md): commit solo con recorrido completo exitoso; cancelación propaga; caché previa conservada; una descarga inicial; URL real por página; pausa de 2 s. Motivo: un historial parcial no debe sustituir a uno más completo guardado, y toda URL de evidencia debe corresponder a la página que la contenía.

## Cambios de arquitectura

- Ninguno estructural; sin cambios de esquema. En este bloque de correcciones: `Diagnostics.selectActivePeriodic` (`PeriodicWorkCandidate`) selecciona el trabajo activo; `_androidEnvironment` pasó a `MutableStateFlow` con `refreshAndroidEnvironment()`; `CnscMonitoringWorker` gana `resume` (UPDATE) y extrae `periodicRequest`; la Activity refresca el entorno con `LifecycleEventEffect(ON_RESUME)` y `LaunchedEffect(tab)`.

## Tareas iniciadas pero incompletas

- Ninguna dentro del alcance autorizado. NEXT_TASKS 7 (diagnóstico y preferencias) quedó DONE con cierre completo en este relevo.

## Riesgos o regresiones posibles

- **`updateInterval`/pausa no verificados físicamente**: se compilan las pruebas, pero la reprogramación real y la supervivencia del intervalo tras reinicio deben observarse en el teléfono (Ajustes → Diagnóstico) en el próximo relevo.
- **Lógica del canal "Info"**: `channelStatuses` reporta bloqueo según `IMPORTANCE_NONE`; canales creados antes de una actualización son coherentes con `createNotificationChannels`.
- **Los schemas históricos 1.json/2.json** son derivados del 3.json actual; revisar si una validación futura fallara.
- **Actualizar Room (2.6.1 → 2.8.x) o migrar kapt → KSP** cambia la ruta de generación de schemas; volver a verificar `schemaLocation` y los assets de test.
- **La política de fallos tardíos cambió** de "conservar lo recogido" a "propagar y conservar caché previa": cualquier código que asuma tolerancia parcial de `parseAll` quedará desactualizado.

## Siguiente tarea recomendada

La verificación física del diagnóstico y las preferencias quedó completada por el usuario en producción (pantalla Ajustes → Diagnóstico, frecuencia 15→30→60→15, pausa/reanudar, refresco al volver de configuración Android, worker y un único trabajo periódico activo), y el transporte de la notificación de prueba también (TEST en pantalla bloqueada → apertura de Mérito Radar). Continuar con los pendientes que esperan una novedad oficial real: ítem 2 (entrega crítica real con pantalla bloqueada/app cerrada desde el worker) y 5 (Doze prolongado). TASKS.md ordena el trabajo: 8 (estado del dispositivo) está actualizado y el diagnóstico permite observar el funcionamiento sin repoblar a ciegas. Cierre documental 2026-09-23: HANDOFF puesto al día con el bloque outbox; NOTIFICATIONS.md y README corregidos (el diseño FCM/backend queda como nota histórica superseded).

## Tareas que requieren especialmente revisión de Codex

1. Confirmar que `main == origin/main` tras este relevo y que el commit del bloque quedó pusheado.
2. La verificación física del diagnóstico/preferencias y la del transporte de la notificación de prueba (TEST en pantalla bloqueada → apertura) ya fueron realizadas por el usuario en producción (2026-09-21) y quedaron registradas en STATUS/TASKS; si se desea, reabrir la UI para observaciones puntuales sin cambiar following.
3. Decidir si versionar los JSON de esquema en una única carpeta (hoy duplicados en `schemas/` y `src/androidTest/assets/`).
4. Si se instaló la nueva versión, evaluar el ítem fallo-de-esquema de instalaciones previas (columna slug nullable en versiones v3 antiguas).
5. Revisar que `RealCatalogTest` no se ejecute en CI sin control (abre red real y muta following).

## Qué debe volver a auditar Devin sobre este bloque

1. Releer `Diagnostics.selectActivePeriodic` y `observePeriodicWork()`: la selección del trabajo activo (ENQUEUED/RUNNING) y la semántica de null como ausencia de programación frente a PAUSED.
2. Releer los triggers de refresco (`LifecycleEventEffect(ON_RESUME)` y `LaunchedEffect(tab)` en MainActivity, `refreshAndroidEnvironment` en repository/viewmodel): que sean por ciclo de vida y no polling, y que el `combine` consuma el StateFlow.
3. Releer `CnscMonitoringWorker.schedule/updateInterval/resume`: KEEP solo en arranque normal, UPDATE en cambio de intervalo y en reanudar; exactamente un trabajo periódico activo; ningún REPLACE.
4. Ejecutar las regresiones nuevas: `selectActivePeriodicIgnoresHistoricalFinishedState`, `selectActivePeriodicWithoutActiveWorkIsNull`, `environmentRefreshConvergesDiagnosticsWhenPlatformChanges`, `resumeAfterPauseDerivesActiveProgram`, `successiveIntervalChangesKeepClampingAndDailyRuns`.
5. La verificación física de este bloque (refresco de permiso/canales/batería al volver de los Ajustes del sistema, pausa/reanudar y cambio de intervalo sobre los jobs de WorkManager, pantalla con datos reales) fue completada por el usuario en producción y registrada en STATUS; revisar la coherencia de los registros o ampliarlos con observaciones puntuales si lo considera necesario.