## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# HANDOFF — Relevo de OpenCode (orquestador temporal) a Codex

- **Fecha**: 2026-09-21 (última actualización al cierre del bloque de diagnóstico y preferencias).
- **Estado actual del proyecto**: Android local-first de vigilancia CNSC (Mérito Radar) funcional y verificado hasta el cierre de esta sesión. Consulta CNSC directamente (OkHttp + TLS corregido), persiste en Room (v3), detecta avisos locales, proyecta fechas conservadoramente y genera notificaciones locales. Backend FastAPI/PostgreSQL es tooling de referencia, no requisito de ejecución. Ahora incluye diagnóstico en la app y preferencias de frecuencia/pausa cumplibles por la arquitectura.
- **Último commit local**: ver `git log -1`; main sincronizado con `origin/main` (push completado en este cierre).
- **Contexto**: Codex estuvo ausente; OpenCode actuó como orquestador temporal con protocolo de relevo (leer AGENTS/TASKS/HANDOFF/PROJECT_STATUS/DECISIONS, verificar git antes de tocar, bloques verificables, commit+push+sync tras cada bloque). origin/main es la fuente de verdad y quedó sincronizado.

## Resumen del bloque actual — Diagnóstico y preferencias (NEXT_TASKS 7)

1. **Diagnóstico** en Ajustes, desde datos reales y sin duplicar lo existente (`Health`, `observeWorkManagerStatus`, `LocalNotifier.hasNotificationPermission/isChannelBlocked`, prefs `monitor_schedule`):
   - Nuevo `Diagnostics.kt` con lógica pura: `Diagnostics.build` recibe procesos, seguidos, `activity_check`, `notice_state`, trabajo periódico (`getWorkInfosForUniqueWorkFlow`), intervalo, pausa y entorno Android (permiso, canales, batería) y deriva un `DiagnosticsSnapshot` + líneas de UI.
   - Estado general con precedencia: PAUSED > AT_RISK (sin trabajo o estado no activo) > WARMING_UP (sin primera revisión, no emite alertas) > DEGRADED (fallo de micrositio en 24 h o permiso denegado) > OPERATIONAL.
   - Muestra: última revisión (avisos y micrositios), próxima ejecución programada, programación actual, cantidad y nombres de seguidos, fuentes vigiladas (catálogo/avisos/micrositios), errores recientes por micrositio, frecuencia configurada, estado de notificaciones (3 canales), optimización de batería y ejecuciones diarias estimadas. Sin valores inventados: la falta de dato se muestra explícita.
2. **Preferencias** que la arquitectura local-first cumple:
   - Frecuencia 15/30/60/120 min: `CnscMonitoringWorker.updateInterval` persiste `interval_minutes` y reemplaza el trabajo único (`ExistingPeriodicWorkPolicy.UPDATE`); `schedule()` y `RadarApp.onCreate` reutilizan la preferencia (intervalo y pausa sobreviven reinicios). Mínimo real de WorkManager (15 min) aplicado con `coerceAtLeast`.
   - Pausar/reanudar vigilancia: `monitoring_paused_v1`; pausar cancela el trabajo único, reanudar lo reprograma.
   - No modifica `following` desde Ajustes (botón navega a Concursos); sin toggle global de notificaciones (se delega a Android, DEC-008/DEC-015); sin backend.

## Verificación del bloque actual

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` → **BUILD SUCCESSFUL**.
- Tests JVM: **72 tests, 0 fallos, 0 errores** (11 nuevos en `DiagnosticsTest`: precedencia de estado, ventana de 24 h para errores recientes, clamping del periodo, conteos/nombres/slugs, próxima ejecución, líneas de UI, pausa).
- Lint: **0 errores, 19 advertencias** (todas preexistentes, ninguna nueva).
- **No se instaló ni instrumentó en 8912c62d** (restricción explícita del usuario: no reinstalar producción). La pantalla con datos reales del teléfono y el efecto de cambiar intervalo/pausar sobre los jobs reales son NOT VERIFIED y deben comprobarse en el siguiente relevo abriendo Ajustes → Diagnóstico en el propio teléfono.
- Producción/QA intactas; no se tocó radar.db ni following; no se ejecutó connectedDebugAndroidTest.

## Pruebas y resultados — resumen completo del turno anterior (grabado aquí)

- Test JVM previos del checkpoint a1d6b21: 56 tests, 0 fallos, 0 errores (9 suites); lint 0 errores/19 advertencias; MigrationTest 3/3 en 8912c62d.
- NO ejecutar `connectedDebugAndroidTest` en el dispositivo físico: desinstala/reinstala la app y vacía datos (reponer seguidos antes).

## Problemas encontrados en este bloque

- **`INSTALL_FAILED_USER_RESTRICTED` (Xiaomi)** ya conocido: se resolvió en el turno previo activando "Instalar vía USB"; para verificación instrumentada se usa `pm install -r` + `am instrument`, no el runner de Gradle.
- **Estado limpiado del dispositivo** (following/caché activity/notice_state vacíos desde la reinstalación del turno de migraciones; no pérdida en este bloque). Verificación 2026-09-21: el usuario ya repobló parcialmente (3 seguidos: DIAN 2676, Aerocivil Primera Fase, CAR) y el worker quedó activo; PGN 2407 y ESE2 fuera de seguimiento. El diagnóstico nuevo lee ese estado real desde Room/WorkManager, pero en esta sesión no se instaló la nueva versión en el teléfono.

## Problemas todavía pendientes (NOT VERIFIED)

- Entrega física de novedad crítica real con pantalla bloqueada/app cerrada (worker → outbox → bandeja → deep link).
- Paginación real de un micrositio con más de 3 páginas (hoy DIAN 2676 tiene una página).
- Pantalla Ajustes → Diagnóstico con los datos reales del teléfono (lógica pura verificada en JVM; sin instalar en esta sesión por restricción).
- Efecto real del cambio de frecuencia y de pausar/reanudar sobre los jobs de WorkManager del dispositivo.
- Migración de una base histórica real con datos previos del usuario.
- Comportamiento prolongado bajo Doze/restricciones OEM.
- Recordatorio de apertura/cierre y cancelación por aplazamiento en producción real.
- Fallo físico de página posterior y cronometría física de la pausa (validados con unitarias, no en dispositivo).

## Decisiones técnicas tomadas y sus motivos

- **DEC-021 — Diagnóstico honesto y preferencias cumplibles** (registrada en DECISIONS.md): diagnóstico solo de fuentes reales (Room/WorkManager/Android); dato faltante se muestra como desconocido, nunca se inventa; estado general con precedencia (pausada > at-risk > sin primera revisión > degradada > operativa). Preferencias limitadas a lo que la arquitectura cumple: frecuencia 15/30/60/120 min (UPDATE del trabajo único, persistido) y pausar/reanudar; Ajustes no toca following (solo enlace), no hay toggle global de notificaciones (se delega a Android) y no se promete ejecución exacta a los 15 minutos.
- **DEC-020 — Historial robusto: compromiso total o nada** (registrada en DECISIONS.md): commit solo con recorrido completo exitoso; cancelación propaga; caché previa conservada; una descarga inicial; URL real por página; pausa de 2 s. Motivo: un historial parcial no debe sustituir a uno más completo guardado, y toda URL de evidencia debe corresponder a la página que la contenía.

## Cambios de arquitectura

- Ninguno estructural; se añade diagnóstico y preferencias sin cambios de esquema. `Diagnostics.kt` es lógica pura nueva; `CnscMonitoringWorker` gana `updateInterval` (UPDATE del trabajo único); `LocalNotifier` expone `channelStatuses()` (solo lectura). Preferencias en la SharedPreferences `monitor_schedule` ya existente (claves `interval_minutes` y `monitoring_paused_v1`).

## Tareas iniciadas pero incompletas

- Ninguna dentro del alcance autorizado. NEXT_TASKS 7 (diagnóstico y preferencias) quedó DONE con cierre completo en este relevo.

## Riesgos o regresiones posibles

- **`updateInterval`/pausa no verificados físicamente**: se compilan las pruebas, pero la reprogramación real y la supervivencia del intervalo tras reinicio deben observarse en el teléfono (Ajustes → Diagnóstico) en el próximo relevo.
- **Lógica del canal "Info"**: `channelStatuses` reporta bloqueo según `IMPORTANCE_NONE`; canales creados antes de una actualización son coherentes con `createNotificationChannels`.
- **Los schemas históricos 1.json/2.json** son derivados del 3.json actual; revisar si una validación futura fallara.
- **Actualizar Room (2.6.1 → 2.8.x) o migrar kapt → KSP** cambia la ruta de generación de schemas; volver a verificar `schemaLocation` y los assets de test.
- **La política de fallos tardíos cambió** de "conservar lo recogido" a "propagar y conservar caché previa": cualquier código que asuma tolerancia parcial de `parseAll` quedará desactualizado.

## Siguiente tarea recomendada

Verificación física sobre el bloque cerrado y los pendientes de alertas: abrir en 8912c62d Ajustes → Diagnóstico (nueva versión instalable sin desinstalar, `pm install -r`) y confirmar estado general, frecuencia y pausa/reanudar con datos reales; luego continuar con el ítem 2 (entrega física con pantalla bloqueada) y 5 (Doze prolongado), que necesitan novedades reales. TASKS.md ordena el trabajo: 8 (estado del dispositivo) ya está actualizado; el diagnóstico permite observar el funcionamiento sin repoblar a ciegas.

## Tareas que requieren especialmente revisión de Codex

1. Confirmar que `main == origin/main` tras este relevo y que el commit del bloque quedó pusheado.
2. Verificar físicamente el diagnóstico y las preferencias en el teléfono (pantalla, cambio de frecuencia, pausa/reanudar) y registrar en STATUS lo observado.
3. Decidir si versionar los JSON de esquema en una única carpeta (hoy duplicados en `schemas/` y `src/androidTest/assets/`).
4. Si se instaló la nueva versión, evaluar el ítem fallo-de-esquema de instalaciones previas (columna slug nullable en versiones v3 antiguas).
5. Revisar que `RealCatalogTest` no se ejecute en CI sin control (abre red real y muta following).