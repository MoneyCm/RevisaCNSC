# Mérito Radar · 0.1.0

Seguimiento independiente de información pública CNSC. **Aplicación Android local-first que funciona sin servidor propio.** Ver [estado verificable](docs/STATUS.md) y [requisitos íntegros](docs/REQUIREMENTS.md).

## Arquitectura Local-First

Mérito Radar funciona directamente en el teléfono Android sin necesidad de backend propio:

- **FUENTES PÚBLICAS CNSC** → **TELÉFONO ANDROID** → **WorkManager** → **Monitor CNSC local** → **Parsers locales** → **Normalización** → **Room** → **Detector de cambios** → **Motor de eventos** → **Preferencias del usuario** → **Notificación local Android**

La aplicación puede:
- Instalarse en el teléfono
- Consultar directamente fuentes públicas oficiales CNSC
- Descubrir procesos automáticamente
- Guardar información localmente
- Revisar cambios periódicamente (WorkManager)
- Detectar inscripciones, recaudos, cambios de fecha, citaciones, resultados
- Generar notificaciones LOCALMENTE
- Funcionar sin servidor propio, PostgreSQL, FastAPI, Firebase, FCM o hosting de pago

## Android

Abrir `android` en Android Studio. JDK 17, Android SDK 34 y Build Tools 34.0.0. Aplicación `co.meritoradar.app`, min SDK 26.

```sh
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug
# Windows: gradlew.bat
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

### Instalación y Uso

1. Instalar el APK en el teléfono
2. Abrir la aplicación
3. Permitir notificaciones (solicitado automáticamente en Android 13+)
4. Sincronizar manualmente ("¿Cómo van los concursos?")
5. Seleccionar concursos a seguir
6. Dejar la vigilancia automática activa

La aplicación consultará CNSC periódicamente mediante WorkManager (configurable: 15 min, 30 min, 1 h, 2 h) y generará notificaciones locales cuando detecte cambios importantes.

### Funcionalidades implementadas

- ✅ Consulta directa a CNSC (sin backend)
- ✅ Parser de catálogo de procesos
- ✅ WorkManager para vigilancia periódica (frecuencia 15/30/60/120 min + pausa, sin prometer exactitud)
- ✅ Detección de nuevos procesos y parser de avisos oficiales
- ✅ Detección de cambios en inscripciones/recaudos y deduplicación semántica
- ✅ Notificaciones locales con canales (urgente, importante, general), outbox durable con reintento y conservación ante canal bloqueado
- ✅ Deep links a procesos específicos
- ✅ Permisos POST_NOTIFICATIONS para Android 13+
- ✅ Persistencia local en Room (v3, con migraciones probadas)
- ✅ Sincronización manual
- ✅ Diagnóstico en Ajustes con datos reales y recordatorios de apertura/cierre

### Funcionalidades pendientes (verificación física)

- ⏳ Entrega crítica real con app cerrada / pantalla bloqueada y deep link a evento (requiere novedad oficial real)
- ⏳ Comportamiento prolongado bajo Doze / restricciones OEM
- ⏳ Paginación real de micrositio con más de 3 páginas
- ⏳ Migración de base histórica real con datos previos

Lo no comprobado en dispositivo figura como NOT VERIFIED en [estado verificable](docs/STATUS.md) y [tareas](docs/TASKS.md).

## Backend (Reference Tooling)

El backend Python/FastAPI/PostgreSQL se conserva como herramienta de desarrollo y especificación de comportamiento, pero **NO es requisito para el funcionamiento de la APK**.

```sh
docker compose up --build
```

Backend opcional para:
- Desarrollo y pruebas
- Comparación de resultados entre Python y Kotlin
- Corpus de pruebas y fixtures
- Herramientas de análisis

Variables activas: DATABASE_URL, MONITOR_INTERVAL_MINUTES, USER_AGENT. No incluir secretos ni tokens en Git.

## API disponible (Backend Reference)

- GET /health
- GET /api/v1/processes?q=&limit=50&offset=0
- GET /api/v1/processes/{id}
- GET /api/v1/processes/{id}/events
- GET /api/v1/alerts

## Pruebas

Crear base separada, por ejemplo `merito_test`; nunca ejecutar pruebas de integración contra producción. Desde backend:

```sh
# DATABASE_URL y TEST_DATABASE_URL apuntan a merito_test
alembic upgrade head
ruff check .
pytest -q
alembic check
```

Sin TEST_DATABASE_URL las pruebas PostgreSQL se omiten: eso no verifica integración. Tests usan transacción revertida y fixture HTML real.

## Troubleshooting

- Error TLS: instalar la CA de confianza en sistema; nunca `verify=False`.
- WorkManager no ejecuta: verificar restricciones de batería y optimización del sistema.
- Notificaciones no llegan: verificar permisos POST_NOTIFICATIONS y canales.
- Parser falla: verificar que CNSC no haya cambiado la estructura HTML.
- robots bloquea PDF: no evadir; la descarga automática no está habilitada.

## Limitaciones

- Android no garantiza ejecución periódica exacta (Doze, ahorro de batería, restricciones OEM)
- No usar como garantía de avisos de inscripción para procesos críticos
- Funcionalidad básica implementada, en desarrollo activo
