# Decisiones

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

## DEC-014 — Identidad de notificación y pruebas aisladas
Usar eventId completo como tag de NotificationManager, con ID numérico constante, para que dos hashes iguales no reemplacen eventos distintos. Incluir eventId en la URI del PendingIntent, además de los extras; los extras por sí solos no distinguen PendingIntents.
La suite opcional notificationQa usa applicationId terminado en .qa y no inserta datos sintéticos en la app personal. Distinguir entrega del transporte, navegación del intent y representación del detalle; no convertir esas pruebas en afirmación de entrega de eventos reales bajo bloqueo.

## DEC-012 — Fechas conservadoras en Android
Proyectar ventanas desde publicaciones normalizadas conservadas en Room. Exigir inicio/fin, año, modalidad y población explícitos para CONFIRMED; no inferir horas. Fecha de publicación ordena fuentes y un conflicto simultáneo exige revisión. Aplazamiento invalida la etapa afectada, no anuncia suspensión de todo el proceso.
Comparar estructura por proceso/etapa/modalidad/población; eventos guardan antes/después y extracto. Alertas críticas solo por ventanas vigentes de publicaciones recientes y concurso seguido; primera carga silenciosa. Antes de entregar, revalidar fechas y fuente en el ciclo actual. Recordatorios temporales quedan pendientes hasta verificar cobertura/frescura de fuentes antiguas.
