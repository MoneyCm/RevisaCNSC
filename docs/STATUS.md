# Estado y registro de verificaciones

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
- Room migration tests: creados pero no ejecutados en dispositivo real.
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
