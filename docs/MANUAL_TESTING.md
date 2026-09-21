# Checklist manual

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
