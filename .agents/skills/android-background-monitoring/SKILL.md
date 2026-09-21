---
name: android-background-monitoring
description: Implementar WorkManager para vigilancia CNSC local en Android
---

## WorkManager Configuration
Usar PeriodicWorkRequest con intervalo mínimo de 15 minutos. Registrar con enqueueUniquePeriodicWork y política KEEP para evitar duplicados. Constraint: NetworkType.CONNECTED. Usar BackoffPolicy.LINEAR con backoff inicial 30 segundos y máximo 10 minutos.

## Architecture
Worker único: CnscMonitoringWorker. No usar Foreground Service como arquitectura principal. No usar AlarmManager para scraping periódico. Aceptar que Android puede retrasar ejecución por Doze, ahorro de batería o restricciones OEM.

## Idempotency
Ejecutar ingesta dos veces sin cambios debe generar 0 eventos nuevos y 0 notificaciones nuevas. Usar fingerprints deterministas para deduplicación. Persistir eventId y notificationId para evitar notificaciones duplicadas.

## Sincronización Manual
OneTimeWorkRequest para "¿Cómo van los concursos?". Usar ExistingWorkPolicy.REPLACE para exclusión con monitor periódico. No permitir ingestas simultáneas de las mismas fuentes.

## Room Persistence
Modelos mínimos: Process, ProcessStage, Publication, Document, Source, Snapshot, Event, Change, Subscription, MonitorRun. Implementar migraciones sin fallbackToDestructiveMigration. Snshots: guardar normalized_content, content_hash, relevant_fields, captured_at. Limpiar snapshots antiguos.

## Detección Semántica
NO generar eventos por cambios de HTML bruto. Pipeline: descarga → parse → eliminar navegación → eliminar ruido → extraer contenido relevante → normalizar → comparar estructura → detectar cambio significativo → crear Event. Cambios de CSS, menú, footer, espacios NO generan alertas.

## Notificaciones Locales
Usar NotificationManager/NotificationCompat. Canales: Alertas urgentes (IMPORTANCE_HIGH), Actualizaciones importantes (IMPORTANCE_DEFAULT), Información general (IMPORTANCE_LOW). Manejar POST_NOTIFICATIONS en Android 13+. No depender de FCM.

## Deep Links
Notificación debe abrir /process/{processId} con eventId si existe. Usar NavDeepLinkBuilder o PendingIntent. NO abrir simplemente MainActivity.

## Testing
Probar: worker único registrado, no se duplica, respeta constraint de red, retry funciona, periodic y manual no chocan. Pruebas de paridad con backend: mismos eventos lógicos para mismo fixture. Pruebas de notificaciones: CRITICAL permitido → notificación, INFO deshabilitado → no notificación, deep link correcto.

## Preferencias
Frecuencia configurable: 15 min, 30 min, 1 h, 2 h. Avisar concursos nuevos: ON/OFF. Alertas críticas: ON/OFF. Actualizaciones importantes: ON/OFF. Información general: ON/OFF. Recordar cierres: ON/OFF.

## Battery Optimization
Detectar restricciones cuando sea posible. NO obligar al usuario a desactivar protecciones. Mostrar mensaje informativo: "La optimización de batería puede retrasar algunas revisiones." con acceso voluntario a configuración.

## Health Local
Estado local con: status, last_run, sources_checked, sources_succeeded, sources_failed, processes_checked, events_created, notifications_created, error_summary. Mostrar en UI: 🟢 Operativo / 🟠 Vigilancia con problemas.

## Offline
Sin internet: NO borrar datos. Mostrar información de Room con fecha de última revisión exitosa. WorkManager reintentará posteriormente.

## Timezone
Interpretar eventos CNSC usando America/Bogota. NO depender de zona horaria del teléfono para fechas oficiales.

## Network Security
HTTPS obligatorio. No permitir cleartext globalmente. Validar URLs antes de abrirlas. Timeouts: connect 15s, read 30s. Retry prudente con backoff.
