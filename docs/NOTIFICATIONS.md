# Notificaciones — pendiente

No hay entrega FCM implementada en este corte. Event.notification_sent permanece false. No confundir evento en base con notificación entregada.

Diseño: eventos confirmados → selección por preferencias globales/override → outbox con UNIQUE(event_id, device_id) → Firebase Admin SDK → canales CRITICAL/IMPORTANT/INFO → deep link meritoradar://process/{UUID}. Android deduplicará event_id persistentemente. Reintentos no garantizan exactamente una entrega; token inválido se desactiva, error transitorio conserva outbox.

Antes de activar: registro anónimo autenticado, rotación de token, POST_NOTIFICATIONS, app cerrada/pantalla bloqueada, navegación a evento correcto, recordatorios Bogotá y deduplicación entre fuentes. Nunca emitir críticas desde fechas ambiguas. Primera carga de catálogo se usa como baseline para no inundar.
