# Entrada de relevo — Mérito Radar

Leer [AGENTS.md](../AGENTS.md), [STATUS.md](STATUS.md), [NEXT_TASKS.md](NEXT_TASKS.md) y [DECISIONS.md](DECISIONS.md).

STATUS es el registro canónico. Conserva bloques históricos: para una misma funcionalidad prevalece la verificación más reciente. Este archivo no mantiene un segundo historial.

## Punto de partida al 2026-09-21

Android consulta CNSC directamente, conserva datos en Room y tiene detección local de avisos, fechas explícitas y notificaciones locales. El backend es tooling de referencia, no requisito de ejecución.

Migraciones Room instrumentadas (NEXT_TASKS 6) corregidas y verificadas: exportSchema activado con schemas JSON (1/2/3) en assets de test, MigrationTestHelper 2.6.1 con tres rutas de migración que conservan datos sembrados, y bug real detectado y corregido (MIGRATION_2_3 generaba slug nullable; ahora NOT NULL como la entidad). VERIFIED: build + assembleDebugAndroidTest + lint y 3/3 tests instrumentados en 8912c62d con la DB de prueba aislada (radar.db real intacta en v3). Ver STATUS.md para el detalle y lo NOT VERIFIED.

Sigue implementado el historial del micrositio con paginación acotada (una publicación fecha/fuente conservada sin alertas retroactivas), la cobertura de concursos seguidos por revalidación acotada (NEXT_TASKS 4) y los recordatorios de apertura/cierre por transcurso del tiempo (NEXT_TASKS 5); ver STATUS.md para la verificación y lo NOT VERIFIED, incluida la entrega física con pantalla bloqueada.

Hay remoto configurado en origin (https://github.com/MoneyCm/RevisaCNSC.git) y main está sincronizado con origin/main. Consultar git log -1 para identificar el commit y git status para detectar cambios posteriores. Respaldo en línea del terminal; publicar cambios solo con destino y autorización del usuario.

## Mensaje para comenzar con otro agente

> Continúa Mérito Radar en esta misma carpeta. Lee AGENTS.md, docs/PROJECT_STATUS.md, docs/STATUS.md, docs/NEXT_TASKS.md y docs/DECISIONS.md. Revisa git status, el historial si existe y el código antes de elegir la primera tarea pendiente. Conserva cambios existentes y arquitectura Android local-first. Ejecuta pruebas pertinentes, distingue implementación de verificación física y actualiza el registro al terminar.

## Mensaje para cerrar una sesión

> Finaliza el bloque actual sin iniciar otro. Revisa cambios, ejecuta comprobaciones pertinentes y registra comandos, resultados, limitaciones y siguiente tarea en STATUS y NEXT_TASKS. Registra decisiones nuevas en DECISIONS. Prepara un commit revisado sin secretos ni artefactos e indica si quedó creado o pendiente.

Detener al agente anterior antes de que el siguiente edite. Abrir la misma carpeta no transmite la conversación: estos archivos y los commits son el contexto compartido.
