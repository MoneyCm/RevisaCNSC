# Checklist manual

## Historial de publicaciones — 2026-09-21
- VERIFIED: 56 tests JVM, build y lint aprobados (19 advertencias); install -r y worker en 8912c62d sin borrar datos.
- Abrir un seguido de micrositio (ej. DIAN 2676), pulsar Actualizar detalle y comprobar que el detalle muestra varias publicaciones fechadas (hasta 5) con su fecha y la nota "Historial informativo conservado del micrositio; no genera alertas nuevas".
- Comprobar en content_cache que activity:<id> ahora incluye publications con título, resumen, fecha Bogotá y fuente; DIAN 2676 conserva 15 publicaciones reales históricas.
- El historial no debe crear avisos ni eventos nuevos: solo información; la consolidación de avisos del worker sigue usando el índice y los recordatorios requieren ventanas CONFIRMED revisadas.
- Paginación real de un micrositio con más de tres páginas: NOT VERIFIED (DIAN hoy tiene una sola página sin paginador).

## Aislamiento de micrositios — 2026-09-21
- VERIFIED: 52 tests JVM, build y lint aprobados; install -r y worker SUCCESS en 8912c62d a las 09:18:55.
- Suite ActivityRefreshTest: fuente que falla conserva dato previo y no impide guardar la siguiente; recuperación limpia diagnóstico; cancelación y fallo de persistencia se propagan; intentos recientes no acaparan la rotación.
- Comprobar en dispositivo que los fallos guardados de concursos seguidos muestran nombre y fecha de intento, con caché disponible.
- No generar avisos falsos ni alterar el sitio real para provocar fallos: los escenarios de fallo se ejercitan con adaptadores de prueba.
- Persistencia/representación de un fallo real y recuperación automática: NOT VERIFIED hasta observarlos en dispositivo.

## Actividad DIAN — 2026-09-21
- VERIFIED: 47 tests JVM, build/lint correctos (19 advertencias); install -r y detalle DIAN observado con Último aviso: Reclamaciones sobre certificado de discapacidad, 27 procesos conservados.
- Abrir DIAN 2676 y comprobar título, fecha de publicación, revisión y enlace oficial del micrositio.
- El aviso observado corresponde a reclamaciones del certificado de discapacidad: no debe etiquetarse como VRM general ni como inscripciones abiertas.
- El encabezado del detalle debe mostrar la actividad encontrada cuando existe, sin repetir Sin etapa confirmada como resumen principal.
- Pruebas JVM del lector cubren alcance discapacidad/VRM, hitos VRM, fechas futuras/imposibles, identidad incorrecta, estructura desconocida y filtro de contenido incorrecto.

## 2026-09-20 — Recordatorios de apertura/cierre
- [x] Build y unitarias: assembleDebug testDebugUnitTest lintDebug; 41 tests JVM, 0 fallos; lint 0 errores / 19 advertencias.
- [x] `adb install -r` en 8912c62d conservando 27 procesos / 4 seguidos; arranque sin crash; worker periódico y manual SUCCESS; notice_state intacto y firedReminders persistido como {}.
- [ ] Notificación física de un recordatorio con ventana oficial real a ≤2 días de apertura/cierre de un seguido: pendiente; hoy ningún seguido tiene ventana confirmada cercana. No inyectar fechas ficticias.
- [ ] Aplazar o cambiar una ventana ya recordada y comprobar que el pendiente se descarta antes de entregar (revalidación de outbox): pendiente de evento real.
- [ ] Pantalla bloqueada con recordatorio real y deep link al concurso: sigue NOT VERIFIED.

## 2026-09-20 — Revalidación de avisos antiguos de seguidos
- [x] Build y unitarias: assembleDebug testDebugUnitTest lintDebug; 39 tests JVM, 0 fallos; lint 0 errores / 19 advertencias.
- [x] `adb install -r` en 8912c62d conservando 27 procesos / 4 seguidos; arranque sin crash; sync manual worker SUCCESS y notice_state regenerado; persistido `revalidatedAt` vacío (los 8 avisos del estado se re-fetchearon en la ventana reciente).
- [x] Un RETRY del periódico durante la instalación no perdió datos (respuesta lenta de 14,7 s; backoff diseñado).
- [ ] Modificar un aviso antiguo real de un seguido fuera de la ventana reciente y comprobar que la rotación emite NOTICE_UPDATED con evidencia y sin duplicados: pendiente; hoy no hay candidatos stale en el estado.
- [ ] Estado con más de 3 páginas nuevas en el índice (freno de cobertura actual): pendiente. No inyectar avisos ficticios en producción.
- [ ] Entrega crítica real del recorrido worker→outbox→bandeja con pantalla bloqueada: sigue sujeta a una publicación oficial con ventana confirmada de un seguido.

## 2026-09-20 — Recuperación de red (offline → recol conexión)
- [x] Snapshot Room previo (radar.db + wal + shm): 27 procesos, 4 seguidos, lastCheckedAt 02:50Z.
- [x] Modo avión ON y ejecución forzada del worker (#168): falló rápido (~28 ms) y NO se perdieron datos (27 procesos / 4 seguidos conservados, mismos ids).
- [x] Modo avión OFF: ejecución del worker SUCCESS con catálogo HTTP 200; lastCheckedAt renovado a 03:01Z en los 27 procesos y notice_state regenerado; sin duplicados ni procesos eliminados.
- [x] Programación observada en sistema: intervalo 15 minutos, backoff LINEAR 30 s, constraint CONNECTED; el job periódico se reprograma tras una ejecución.
- [ ] Doze prolongado y restricciones OEM/ahorro de batería: pendiente. Documentar tiempos, no prometer intervalos exactos.
- [ ] Entrega crítica real con pantalla bloqueada y conservación end-to-end de la outbox con canal bloqueado a través del worker: pendiente.
- Nota: extraer Room con cat databases/radar.db requiere además -wal y -shm (o checkpoint) para leer escrituras recientes.

## 2026-09-20 — Instalación con fix de outbox y estado real de alertas
- [x] `adb install -r` de app-debug.apk (commit 5d0dd44) en 8912c62d conservando datos.
- [x] Apertura correcta; Room conserva 27 procesos y 4 seguidos (Empresas Sociales del Estado 2, DIAN 2676, Aerocivil Primera Fase, PGN 2407 de 2022).
- [x] Worker real ejecutado tras la instalación: lastCheckedAt actualizado en los 27 procesos (02:50Z) y notice_state regenerado; pending=[].
- [ ] Sin verificación posible: ninguna alerta crítica real entregable hoy. Los 8 eventos del estado son NOTICE_PUBLISHED con notify_eligible=false y ninguno de los 4 seguidos tiene etapa de inscripción/recaudo confirmada y vigente. No se inyectan datos CNSC ficticios.
- Solo una publicación oficial nueva con ventana confirmada para un concurso seguido permite comprobar el recorrido crítico real (worker → outbox → bandeja → deep link al concurso). Queda pendiente hasta que exista ese evento.

## Suite aislada de notificaciones (opcional)
Resultado 2026-09-20, teléfono 8912c62d: OK (3 tests). Se excluyen resúmenes automáticos del conteo para comprobar avisos de evento. App normal compilada, 36 tests JVM y lint aprobados; actualización conservando 27 procesos. QA detenida al finalizar.
Compilar desde raíz con .\android\gradlew.bat -p android -PnotificationQa=true assembleQa assembleQaAndroidTest.
Instalar app-qa.apk y app-qa-androidTest.apk desde sus carpetas en android/app/build/outputs/apk. La aplicación es co.meritoradar.app.qa (Mérito Radar QA), distinta de la app personal.
Ejecutar adb shell am instrument -w -e class co.meritoradar.app.NotificationDeliveryTest co.meritoradar.app.qa.test/androidx.test.runner.AndroidJUnitRunner.

La suite comprueba repetición, colisiones de hash y el PendingIntent hacia MainActivity con processId/eventId. No inserta concursos ficticios ni demuestra representación del detalle, entrega crítica bajo bloqueo o toda la outbox. Limpia sus avisos al terminar.
La propiedad notificationQa selecciona src/notificationTest/java en lugar de la suite legacy androidTest, cuyos errores de migración siguen pendientes; no reportar la suite legacy como aprobada.
Al terminar, detener la copia QA para evitar revisiones CNSC adicionales: adb shell am force-stop co.meritoradar.app.qa.

## 2026-09-20 — Diagnóstico de notificaciones y periodo natural
- [x] Ajustes > Enviar notificación de prueba: aviso silencioso visible en bandeja, texto TEST - Mérito Radar; permiso concedido.
- [x] Repetir envío: un solo NotificationRecord id=9999.
- [x] Instalación conservando datos; 36 tests JVM aprobados, build y lint aprobados.
- [x] Observada revisión automática sin forzar job ni pulsar actualizar: 20:38:14–20:38:34, SUCCESS; pantalla Asleep al comprobar; siguiente demora 15 minutos.
- [ ] Bloquear notificaciones/canal general y verificar mensaje, después restaurar la preferencia del usuario.
- [ ] Nueva alerta de evento oficial con pantalla bloqueada y app cerrada; tocar y abrir concurso correcto.
- [ ] (2026-09-20) Sin alerta crítica real entregable hoy: seguidos sin ventana confirmada vigente y pending vacío. Comprobar cuando CNSC publique una ventana real de un concurso seguido.
- [ ] Recuperación offline, Doze prolongado, accesibilidad y fuente grande del bloque de prueba.
- La prueba manual no crea eventos CNSC y no demuestra funcionamiento de outbox ni de alertas críticas.

## Fechas locales (compilación, unitarias e instalación aprobadas)
- assembleDebug / testDebugUnitTest / lintDebug: BUILD SUCCESSFUL. 25 tests aprobados; 0 errores lint, 19 advertencias.
- Tras reconexión: instalación conservando datos Success en 8912c62d; catálogo/avisos HTTP 200 y worker SUCCESS, UI 27 procesos. Tarjeta de fechas y notificación física crítica siguen NOT VERIFIED.
- Abrir un concurso con aviso que contenga inicio/fin, año, modalidad y población explícitos: deben verse en Fechas publicadas y enlazar la evidencia CNSC.
- En el catálogo, un concurso con etapas guardadas debe mostrar "Etapas publicadas: Inscripción", "Etapas publicadas: Recaudo" o ambas; sin evidencia debe mostrar "Sin etapa confirmada".
- En el detalle, un aplazamiento o suspensión debe mostrar "Fechas en revisión" en el resumen superior.
- Fechas ambiguas muestran Por confirmar; ventanas pasadas indican que transcurrieron sin declarar que terminó todo el proceso.
- Una ventana confirmada debe mostrar "Inscripciones próximas", "Inscripciones abiertas hasta" o "Inscripciones cerradas" según la fecha de Bogotá.
- Una ventana aplazada o suspendida debe mostrar "Inscripciones aplazadas o en revisión".
- Una publicación de aplazamiento debe poner la etapa afectada en revisión.
- Primera carga y repetición sin cambios no notifican fechas. Cambios confirmados conservan antes/después.
- Las pruebas físicas de notificación con pantalla bloqueada, revocación del permiso y entrega de alertas críticas siguen NOT VERIFIED.

Sólo marcar lo ejecutado. Tests unitarios no sustituyen comprobación física.

- [x] HTTP real del catálogo CNSC con validación TLS.
- [x] Migración PostgreSQL local, ingesta y consulta API real (27 procesos en esta ejecución).
- [x] Repetición, descubrimiento sintético en test, vacío estructural, ruido HTML y fallo HTTP conservan invariantes.
- [ ] Docker Compose arranca desde cero.
- [ ] Instalar APK en teléfono.
- [ ] Cargar datos reales desde API.
- [ ] Buscar, seguir y dejar de seguir.
- [ ] Cerrar y reiniciar; conservar favoritos.
- [ ] Offline: conservar catálogo y mostrar error/fecha.
- [ ] Rotación, fuente grande, TalkBack y modo oscuro.
- [ ] Abrir fuente CNSC.
- [ ] Deep link al ID correcto.
- [ ] Configurar Firebase y conceder permiso.
- [ ] Recibir push con app cerrada y pantalla bloqueada.
- [ ] Tocar push y abrir concurso/evento.
- [ ] Cambio de fecha, suspensión, reanudación y recordatorio sin duplicados.


## 2026-09-20 — Catálogo local / cadena TLS
- Compilación: gradlew.bat -p android assembleDebug testDebugUnitTest lintDebug: PASS.
- Unitarias: 10/10, incluidas enlaces relativos/duplicados, URLs inseguras, certificado autofirmado rechazado e intermedio no agregado a raíces.
- Lint: 0 errores, 19 advertencias.
- Dispositivo 8912c62d / Android 16: instalación conservando datos, apertura, petición oficial HTTP 200 y worker SUCCESS.
- Room: 27 procesos / 27 URLs. Reinicio de proceso y segunda consulta: HTTP 200, SUCCESS, mismas 27 URLs, primera detección conservada y última revisión actualizada.
- UI: catálogo visible; encabezado confirma 27 procesos y última revisión. Sin notificaciones masivas de baseline.
- Alcance pendiente: migraciones instrumentadas, modo offline, fallo visible en UI, notificaciones/deep links y periódico natural en segundo plano (NOT VERIFIED).
