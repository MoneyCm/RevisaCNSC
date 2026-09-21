# Arquitectura

## Arquitectura de Producción (Actual)
CNSC → HTTP limitado → Android (OkHttp) → parser local → estructura canónica → Room (snapshot, proceso, cambio, evento) → detector de cambios local → notificaciones locales (NotificationManager) → usuario.

## Arquitectura Anterior (Deprecated - Reference Only)
CNSC → HTTP limitado → parser por fuente → estructura canónica → PostgreSQL (snapshot, proceso, cambio, evento) → outbox por dispositivo → FCM → Android → detalle → fuente oficial.

## Motivo del Cambio
- Aplicación personal: no requiere hosting ni servidores propios
- Independencia: funciona sin backend, sin PostgreSQL, sin Firebase, sin FCM
- Menor complejidad operacional: cero infraestructura
- Privacidad: datos permanecen en el dispositivo
- Funcionamiento autónomo: el teléfono vigila CNSC directamente

## Implementación Android Local
- **HTTP directo**: OkHttp con timeouts, retry, backoff, cache condicional
- **WorkManager**: PeriodicWorkRequest (15 min mínimo) con NetworkType.CONNECTED
- **Room**: Procesos, etapas, publicaciones, documentos, snapshots, eventos, cambios, ejecuciones de monitor
- **Detector de cambios**: Comparación semántica de estructuras canónicas, no HTML bruto
- **Notificaciones locales**: NotificationManager/NotificationCompat, canales urgente/importante/general
- **Preferencias**: Frecuencia configurable, tipos de alertas, recordatorios de fechas

## Limitaciones
- Android no garantiza ejecución periódica exacta (Doze, ahorro de batería, restricciones OEM)
- No usar AlarmManager para scraping periódico
- No usar Foreground Service como arquitectura principal
- Aceptar retrasos en revisiones por optimización de batería

## Backend Existente (Reference Tooling)
El backend Python/FastAPI/PostgreSQL se conserva como:
- Especificación de comportamiento (parsers, normalización, detección semántica)
- Corpus de pruebas y fixtures
- Herramienta de desarrollo y comparación
- Posible modo futuro opcional

NO es requisito de ejecución para el APK de producción.

## Fechas
Parser robusto para español con rangos, año explícito y rechazo de fechas inválidas. CONFIRMED describe fecha sintácticamente inequívoca; pipeline debe comprobar origen oficial, modalidad, vigencia y evidencia antes de alertar. Timezone: America/Bogota para eventos CNSC, independiente de zona horaria del teléfono.
