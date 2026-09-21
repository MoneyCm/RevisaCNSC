# Entrada de relevo — Mérito Radar

Leer [AGENTS.md](../AGENTS.md), [STATUS.md](STATUS.md), [NEXT_TASKS.md](NEXT_TASKS.md) y [DECISIONS.md](DECISIONS.md).

STATUS es el registro canónico. Conserva bloques históricos: para una misma funcionalidad prevalece la verificación más reciente. Este archivo no mantiene un segundo historial.

## Punto de partida al 2026-09-21

Android consulta CNSC directamente, conserva datos en Room y tiene detección local de avisos, fechas explícitas y notificaciones locales. El backend es tooling de referencia, no requisito de ejecución.

Implementado el historial del micrositio con paginación acotada: ProcessActivity.parseAll conserva hasta tres páginas del mismo micrositio oficial y varias publicaciones fechadas (título, resumen, fecha y fuente) en publications, ordenadas desc y sin alertas retroactivas. VERIFIED: build + 56 tests JVM + lint (0 errores) y verificación física en 8912c62d (DIAN 2676 conserva 15 publicaciones fechadas; la vista muestra el historial con nota informativa). Ver STATUS.md para el detalle y lo NOT VERIFIED.

La cobertura de concursos seguidos (NEXT_TASKS 4) quedó implementada (revalidación acotada por rotación) y los recordatorios de apertura/cierre (NEXT_TASKS 5) se implementaron por transcurso del tiempo con deduplicación y cancelación ante aplazamientos; ver STATUS.md para la verificación y lo NOT VERIFIED. Se verificó una notificación manual visible, una ejecución automática natural y tres pruebas instrumentadas en el paquete QA: repetición, colisiones y destino del PendingIntent. La representación del detalle real desde una alerta, la entrega con pantalla bloqueada y las migraciones instrumentadas siguen NOT VERIFIED. Exigir entrega real bajo pantalla bloqueada antes de declarar terminada la vigilancia completa.

Hay remoto configurado en origin (https://github.com/MoneyCm/RevisaCNSC.git) y main está sincronizado con origin/main. Consultar git log -1 para identificar el commit y git status para detectar cambios posteriores. Respaldo en línea del terminal; publicar cambios solo con destino y autorización del usuario.

## Mensaje para comenzar con otro agente

> Continúa Mérito Radar en esta misma carpeta. Lee AGENTS.md, docs/PROJECT_STATUS.md, docs/STATUS.md, docs/NEXT_TASKS.md y docs/DECISIONS.md. Revisa git status, el historial si existe y el código antes de elegir la primera tarea pendiente. Conserva cambios existentes y arquitectura Android local-first. Ejecuta pruebas pertinentes, distingue implementación de verificación física y actualiza el registro al terminar.

## Mensaje para cerrar una sesión

> Finaliza el bloque actual sin iniciar otro. Revisa cambios, ejecuta comprobaciones pertinentes y registra comandos, resultados, limitaciones y siguiente tarea en STATUS y NEXT_TASKS. Registra decisiones nuevas en DECISIONS. Prepara un commit revisado sin secretos ni artefactos e indica si quedó creado o pendiente.

Detener al agente anterior antes de que el siguiente edite. Abrir la misma carpeta no transmite la conversación: estos archivos y los commits son el contexto compartido.
