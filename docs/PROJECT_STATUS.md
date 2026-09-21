# Entrada de relevo — Mérito Radar

Leer [AGENTS.md](../AGENTS.md), [STATUS.md](STATUS.md), [NEXT_TASKS.md](NEXT_TASKS.md) y [DECISIONS.md](DECISIONS.md).

STATUS es el registro canónico. Conserva bloques históricos: para una misma funcionalidad prevalece la verificación más reciente. Este archivo no mantiene un segundo historial.

## Punto de partida al 2026-09-20

Android consulta CNSC directamente, conserva datos en Room y tiene detección local de avisos, fechas explícitas y notificaciones locales. El backend es tooling de referencia, no requisito de ejecución.

La última verificación registrada incluye compilación, lint, 39 tests JVM aprobados e instalación conservando datos. La cobertura de concursos seguidos (NEXT_TASKS 4) quedó implementada en este bloque: revalidación acotada de avisos antiguos por rotación con conditional GET y NOTICE_UPDATED con evidencia; ver STATUS.md para la verificación y lo NOT VERIFIED.

Se verificó una notificación manual visible, una ejecución automática natural y tres pruebas instrumentadas en el paquete QA: repetición, colisiones y destino del PendingIntent. Ver los bloques recientes de STATUS. La representación del detalle real desde una alerta, la entrega con pantalla bloqueada y las migraciones instrumentadas siguen NOT VERIFIED. La revalidación de avisos antiguos de seguidos quedó cubierta por rotación acotada (no confundir con recordatorios temporales, que siguen pendientes). No declarar terminada la vigilancia completa.

El punto de control inicial reúne código, pruebas y documentación revisados. Consultar git log -1 para identificar el commit y git status para detectar cambios posteriores. No hay remoto configurado: este punto de recuperación es local y no protege frente a pérdida del equipo.

## Mensaje para comenzar con otro agente

> Continúa Mérito Radar en esta misma carpeta. Lee AGENTS.md, docs/PROJECT_STATUS.md, docs/STATUS.md, docs/NEXT_TASKS.md y docs/DECISIONS.md. Revisa git status, el historial si existe y el código antes de elegir la primera tarea pendiente. Conserva cambios existentes y arquitectura Android local-first. Ejecuta pruebas pertinentes, distingue implementación de verificación física y actualiza el registro al terminar.

## Mensaje para cerrar una sesión

> Finaliza el bloque actual sin iniciar otro. Revisa cambios, ejecuta comprobaciones pertinentes y registra comandos, resultados, limitaciones y siguiente tarea en STATUS y NEXT_TASKS. Registra decisiones nuevas en DECISIONS. Prepara un commit revisado sin secretos ni artefactos e indica si quedó creado o pendiente.

Detener al agente anterior antes de que el siguiente edite. Abrir la misma carpeta no transmite la conversación: estos archivos y los commits son el contexto compartido.
