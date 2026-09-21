# Modelo de datos

Implementado en primer corte: Process (identidad por URL canónica, nombre, categoría, estado UNKNOWN, detección y última revisión); Source (URL, HTTP, éxito/error, ETag/Last-Modified); Snapshot (JSON canónico/hash); Event (fingerprint único, prioridad, evidencia y detección). Snapshot sólo cambia por contenido relevante. La primera carga registra baseline, no notificaciones retrospectivas.

Previsto, no implementado: Organization, ProcessStage, Change por campo, Document, EventEvidence multifuente, Device, Subscription, Delivery/outbox y Reminder. Fechas: raw_text, parsed_date, timezone America/Bogota, confidence; separar published_at/event_start_at/event_end_at/detected_at. Modalidad y población deben integrar fingerprints para no fusionar ascenso y abierto. Nunca usar URL sola para deduplicar eventos semánticos.
