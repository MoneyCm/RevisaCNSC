## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# Entrada de relevo — Mérito Radar

Leer [AGENTS.md](../AGENTS.md), [STATUS.md](STATUS.md), [NEXT_TASKS.md](NEXT_TASKS.md) y [DECISIONS.md](DECISIONS.md).

STATUS es el registro canónico. Conserva bloques históricos: para una misma funcionalidad prevalece la verificación más reciente. Este archivo no mantiene un segundo historial.

## Punto de partida al 2026-09-21

Android consulta CNSC directamente, conserva datos en Room y tiene detección local de avisos, fechas explícitas y notificaciones locales. El backend es tooling de referencia, no requisito de ejecución.

Robustez del historial (NEXT_TASKS 2 de la prioridad autorizada) completada y verificada: la lectura paginada se compromete solo con un recorrido completo exitoso (fallo tardío y cancelación se propagan y conservan la caché previa), la consulta manual descarga la primera página una sola vez (`ProcessMicrositeReader` comparte la respuesta entre identidad y actividad), una pausa de 2 s separa las solicitudes sucesivas, y cada publicación conserva la URL real de su página. VERIFIED: assembleDebug + testDebugUnitTest + lintDebug + assembleDebugAndroidTest BUILD SUCCESSFUL; 61 tests JVM 0 fallos (15 en ProcessActivityTest); lint 0 errores/19 advertencias; MigrationTest 3/3 en 8912c62d vía am instrument sin desinstalar producción; radar.db real intacta (v3, 27 procesos). Ver STATUS.md para detalle y NOT VERIFIED.

Migraciones Room instrumentadas (NEXT_TASKS 6) corregidas y verificadas: exportSchema activado con schemas JSON (1/2/3) en assets de test, MigrationTestHelper 2.6.1 con tres rutas de migración que conservan datos sembrados, y bug real detectado y corregido (MIGRATION_2_3 generaba slug nullable; ahora NOT NULL como la entidad). VERIFIED: build + assembleDebugAndroidTest + lint y 3/3 tests instrumentados en 8912c62d con la DB de prueba aislada (radar.db real intacta en v3). Ver STATUS.md para el detalle y lo NOT VERIFIED.

Sigue implementado el historial del micrositio con paginación acotada (una publicación fecha/fuente conservada sin alertas retroactivas), la cobertura de concursos seguidos por revalidación acotada (NEXT_TASKS 4) y los recordatorios de apertura/cierre por transcurso del tiempo (NEXT_TASKS 5); ver STATUS.md para la verificación y lo NOT VERIFIED, incluida la entrega física con pantalla bloqueada.

Hay remoto configurado en origin (https://github.com/MoneyCm/RevisaCNSC.git) y main está sincronizado con origin/main. Consultar git log -1 para identificar el commit y git status para detectar cambios posteriores. Respaldo en línea del terminal; publicar cambios solo con destino y autorización del usuario.

## Mensaje para comenzar con otro agente

> Continúa Mérito Radar en esta misma carpeta. Lee AGENTS.md, docs/PROJECT_STATUS.md, docs/STATUS.md, docs/NEXT_TASKS.md y docs/DECISIONS.md. Revisa git status, el historial si existe y el código antes de elegir la primera tarea pendiente. Conserva cambios existentes y arquitectura Android local-first. Ejecuta pruebas pertinentes, distingue implementación de verificación física y actualiza el registro al terminar.

## Mensaje para cerrar una sesión

> Finaliza el bloque actual sin iniciar otro. Revisa cambios, ejecuta comprobaciones pertinentes y registra comandos, resultados, limitaciones y siguiente tarea en STATUS y NEXT_TASKS. Registra decisiones nuevas en DECISIONS. Prepara un commit revisado sin secretos ni artefactos e indica si quedó creado o pendiente.

Detener al agente anterior antes de que el siguiente edite. Abrir la misma carpeta no transmite la conversación: estos archivos y los commits son el contexto compartido.
