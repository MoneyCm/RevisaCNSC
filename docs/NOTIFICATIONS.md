# Notificaciones — entrega local (actualizado 2026-09-23)

Arquitectura vigente (local-first, DEC-006/DEC-008/DEC-023): eventos confirmados → outbox durable en Room → NotificationManager local (canales CRITICAL/IMPORTANT/INFO) → deep link a proceso/evento. Android deduplica por eventId; no se promete entrega exactamente una vez ni puntualidad de 15 min (Doze/OEM mandan).

- Decisión por evento pura y testeada (`OutboxReview` DELIVER/KEEP/DROP): descarta evento inexistente, concurso no seguido, vencido >7 días o ilegible, y ventana que ya no coincide; conserva la alerta con etapa sin revisión fresca; entrega el resto.
- Reintento: al inicio de cada corrida (aunque luego falle la red), al abrir la app (sin red) y flush compartido al final; el estado persiste tras cada entrega.
- Permiso y canales: sin permiso global o con canal bloqueado (IMPORTANCE_NONE) el evento se conserva en `pending`; el diagnóstico muestra los pendientes y la acción sugerida.
- Reglas conservadas: nunca emitir críticas desde fechas ambiguas; primera carga del catálogo como baseline (sin avisos masivos); deduplicación por contenido entre fuentes; un aplazamiento o cambio de ventana cancela el pendiente antes de entregarlo.

Verificado: transporte manual de prueba (TEST en pantalla bloqueada → apertura, usuario 2026-09-21) y suites (93 tests JVM, NotificationDeliveryTest en QA). NOT VERIFIED: generación real con app cerrada/teléfono bloqueado, recorrido worker → cambio CNSC real → outbox → notificación, deep link real a evento y Doze prolongado. Detalle en STATUS.md.

Nota histórica: el diseño backend + Firebase Admin SDK con `outbox UNIQUE(event_id, device_id)` y `Event.notification_sent`, descrito en versiones anteriores de este archivo, corresponde a la arquitectura cliente-servidor original y quedó superseded por la arquitectura local-first; se conserva solo como referencia.
