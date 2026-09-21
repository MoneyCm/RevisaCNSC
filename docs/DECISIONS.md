# Decisiones

## DEC-017 — 2026-09-20 — Recordatorios por transcurso del tiempo, no por detección
Los recordatorios de apertura/cierre no son eventos de publicación: los genera NoticeReminder según la proximidad de la ventana confirmada (apertura 2 días antes del inicio; cierre 2 días antes del fin y solo tras comenzar, fechas en Bogotá). Solo etapas CONFIRMED/SCHEDULED de concursos seguidos y solo cuando la fuente de la etapa fue revisada en la corrida actual. La clave (hash de proceso|etapa|tipo|fechas) deduplica por ventana y cambia ante aplazamiento o cambio de fechas, de modo que lo viejo no se reemite y lo nuevo puede recordarse; el descarte de la outbox por ventana distinta o REVIEW_REQUIRED cancela un pendiente antes de entregarlo.

## DEC-016 — 2026-09-20 — Revalidación de avisos antiguos por rotación acotada
La ventana reciente del índice no puede descargarse indefinidamente. En lugar de cubrir todo el historial por página, los avisos de concursos seguidos fuera de la ventana se revalidan por rotación (hasta 6 por ejecución, orden LRU por revalidatedAt persistido) mediante conditional GET con los validators ya almacenados (ETag/Last-Modified). Un 304 conserva contenido; un 200 re-parsea y merge produce NOTICE_UPDATED solo si cambia el semanticKey, sin fabricar elegibilidad para ventanas pasadas. Un fallo de revalidación marca la hora y continúa sin corromper notice_state. Objetivo: detectar modificaciones fuera de la ventana con costo acotado y evidencia real, respetando caché/robots.

## DEC-001 — 2026-09-19 — Desarrollo por corte verificable
Problema: catálogo no prueba etapa fina. Opciones: inferir o conservar UNKNOWN. Decisión: estado UNKNOWN y categoría oficial separada IN_DEVELOPMENT. Motivo: evitar aparentar inscripciones abiertas. Consecuencia: ampliar parsers antes de alertas por fechas.

## DEC-002 — 2026-09-19 — Robots y PDF
No descargar rutas de adjuntos prohibidas por robots.txt. Conservar metadatos. PDF/OCR queda pendiente hasta fuente permitida; no evadir restricciones.

## DEC-003 — 2026-09-19 — Operación (anterior)
FastAPI + PostgreSQL + worker único, sin proveedor contratado. Advisory lock transaccional PostgreSQL evita carreras en ingesta. Primera carga es baseline sin avisos masivos; descubrimientos posteriores crean eventos. Sin cambios de esquema fuera de Alembic.

## DEC-004 — 2026-09-19 — Toolchain Android provisional
AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, SDK/target 34, Gradle 8.7 y JDK 17 por disponibilidad local. Se genera APK manual; antes de Play revisar y actualizar target y dependencias. No afirmar compatibilidad con requisitos actuales de Play.

## DEC-005 — 2026-09-19 — TLS y pruebas locales
httpx usa ssl.create_default_context para confiar en certificados del sistema operativo, conservando validación TLS. PostgreSQL de verificación aislado en loopback:55432, sin tocar datos preexistentes. API localhost:8000. No despliegue público.

## DEC-006 — 2026-09-20 — Migración a arquitectura local-first
Problema: Dependencia de backend propio crea complejidad operativa y costos. Decisión: Android como arquitectura principal de producción, directamente conectado a CNSC. Motivo: aplicación personal, cero hosting, independencia, menor complejidad operacional, privacidad. Consecuencia: backend conservado como tooling/reference, Android usa WorkManager + Room + notificaciones locales.

## DEC-007 — 2026-09-20 — WorkManager para vigilancia
Problema: Mantener proceso permanentemente despierto no es sostenible en Android. Decisión: WorkManager con PeriodicWorkRequest (mínimo 15 minutos). Motivo: respetar restricciones de Android (Doze, ahorro de batería), evitar abusos de AlarmManager. Consecuencia: aceptar que Android puede retrasar ejecución, no prometer exactitud de 15 minutos.

## DEC-008 — 2026-09-20 — Notificaciones locales en lugar de FCM
Problema: FCM requiere infraestructura externa y configuración compleja. Decisión: NotificationManager/NotificationCompat con canales locales. Motivo: autonomía completa, cero dependencias externas, menor complejidad. Consecuencia: permisos POST_NOTIFICATIONS en Android 13+, deep links a procesos específicos.

## DEC-009 — 2026-09-20 — HTTP directo con OkHttp
Problema: Dependencia de API backend agrega latencia y puntos de fallo. Decisión: OkHttp directo a CNSC con timeouts, retry, backoff. Motivo: simplicidad, menor latencia, autonomía. Consecuencia: respetar robots.txt, headers de identificación, cache condicional.


## DEC-010 — 2026-09-20 — Completar cadena TLS CNSC
El endpoint inspeccionado entrega solo el certificado final. Añadir GeoTrust TLS RSA CA G1 como intermedio auxiliar, nunca como raíz confiable. Android debe validar la cadena completa contra sus raíces; OkHttp conserva verificación de hostname. No usar trust-all, HTTP ni hostname verifier permisivo.
Origen HTTPS: https://cacerts.digicert.com/GeoTrustTLSRSACAG1.crt.
SHA256: C06E307F7CFC1D32FA72A4C033C87B90019AF216F0775D64978A2ECA6C8A230E. Vence 2027-11-02; revisar cuando CNSC renueve emisor.


## DEC-011 — Avisos locales con alcance explícito
Los avisos se asocian solo por enlace oficial único a un proceso del catálogo. El baseline es la primera página reciente, sin alertas retroactivas. Las revisiones siguientes recorren hasta una URL conocida (máximo tres páginas); si no alcanzan esa frontera fallan sin consolidar avance. Los avisos antiguos fuera de la ventana no tienen aún revalidación sistemática.
Se conserva cuerpo normalizado y evidencia, no HTML bruto. Eventos de publicación/actualización son informativos y no equivalen a una etapa abierta. Clasificación de fechas críticas sigue pendiente.
Room content_cache guarda estado, eventos y outbox en una transacción sin cambio de esquema. Entrega local usa eventId estable y onlyAlertOnce; no se promete entrega exactamente una vez. Se conserva la cola mientras falte permiso; se descarta si se deja de seguir o pasan siete días.

## DEC-013 — 2026-09-20 — Relevo secuencial y estado canónico
Los agentes trabajan sucesivamente en la misma carpeta. STATUS conserva el registro de verificaciones, PROJECT_STATUS sirve de entrada y NEXT_TASKS ordena pendientes. No mantener dos historiales independientes. Un relevo informa pruebas, limitaciones y existencia real del commit; compartir carpeta no equivale a tener respaldo Git. La publicación remota requiere autorización y destino.

## DEC-015 — Outbox conserva eventos por canal bloqueado
El permiso global no basta: Android traga notify() sin excepción en un canal con IMPORTANCE_NONE. showEventNotification devuelve true solo cuando el post real llegó; isChannelBlocked considera bloqueados canal inexistente o IMPORTANCE_NONE y channelForPriority centraliza prioridad→canal. NoticeMonitor elimina un evento de pending únicamente con entrega confirmada.
Android no permite a la app restaurar por API la importancia de un canal puesto en NONE; las pruebas instrumentadas deben usar canales dedicados y no mutar canales usados por otras pruebas ni esperar que el finally recupere el estado.

## DEC-014 — Identidad de notificación y pruebas aisladas

Usar eventId completo como tag de NotificationManager, con ID numérico constante, para que dos hashes iguales no reemplacen eventos distintos. Incluir eventId en la URI del PendingIntent, además de los extras; los extras por sí solos no distinguen PendingIntents.
La suite opcional notificationQa usa applicationId terminado en .qa y no inserta datos sintéticos en la app personal. Distinguir entrega del transporte, navegación del intent y representación del detalle; no convertir esas pruebas en afirmación de entrega de eventos reales bajo bloqueo.

## Aislamiento de micrositios — 2026-09-21
La lectura de cada micrositio es independiente: un error externo conserva su última actividad y no impide consolidar los avisos de las otras fuentes. Registrar diagnóstico por proceso en content_cache con fecha de intento; el siguiente éxito lo limpia. La rotación usa último intento, exitoso o fallido, con espera de 30 minutos y máximo tres por ciclo. Cancelaciones y errores de persistencia no se convierten en errores externos ni en éxito.

## DEC-012 — Fechas conservadoras en Android
Proyectar ventanas desde publicaciones normalizadas conservadas en Room. Exigir inicio/fin, año, modalidad y población explícitos para CONFIRMED; no inferir horas. Fecha de publicación ordena fuentes y un conflicto simultáneo exige revisión. Aplazamiento invalida la etapa afectada, no anuncia suspensión de todo el proceso.
Comparar estructura por proceso/etapa/modalidad/población; eventos guardan antes/después y extracto. Alertas críticas solo por ventanas vigentes de publicaciones recientes y concurso seguido; primera carga silenciosa. Antes de entregar, revalidar fechas y fuente en el ciclo actual. Recordatorios temporales quedan pendientes hasta verificar cobertura/frescura de fuentes antiguas.
