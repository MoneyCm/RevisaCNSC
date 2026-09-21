---
name: app-qa
description: Verificar una funcionalidad antes de declararla terminada.
---

Leer STATUS y requisitos afectados. Ejecutar tests pertinentes y documentar comandos/resultados. Ingesta: fixture real, vacío, HTTP fallido, duplicado, cosmético, fecha ambigua y reinicio. Android: compilación/lint/tests, vacío, error, offline, reinicio, rotación, modo oscuro, permisos, push y deep links. Probar PostgreSQL real para concurrencia y migraciones. Marcar NOT VERIFIED para verificaciones no ejecutadas. Actualizar docs/MANUAL_TESTING.md y STATUS; nunca usar mocks de producción para ocultar bloqueos.
