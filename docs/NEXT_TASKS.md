# Próximas tareas

Orden de relevo al 2026-09-20. Contrastar con código y [STATUS.md](STATUS.md) antes de implementar.

1. **Comprobar punto de control y preparar respaldo.** Consultar git log -1 y git status antes de comenzar. El punto inicial conserva también las limitaciones documentadas, no implica vigilancia completamente verificada. No hay remoto; publicar un respaldo solo con destino y autorización del usuario.
2. **Notificaciones de eventos y enlaces.** Transporte manual, tres pruebas QA de repetición, colisiones, destino del PendingIntent y rechazo por canal bloqueado verificados: showEventNotification devuelve entrega real y NoticeMonitor conserva el evento en la outbox si el canal está bloqueado o no hay permiso. Falta entrega oficial con pantalla bloqueada y representación del detalle/evento correcto. Pendiente también la conservación física end-to-end de la outbox con canal bloqueado a través del worker. No inyectar avisos ficticios en producción.
3. **Vigilancia prolongada y recuperación.** Una ejecución natural y su reprogramación a 15 minutos están verificadas, y la recuperación de red quedó verificada en dispositivo: fallo offline sin pérdida de datos (27 procesos/4 seguidos intactos) y recol conexión con worker SUCCESS y lastCheckedAt renovado (03:01Z). Falta solo comportamiento prolongado bajo restricciones/Doze y entrega crítica real con pantalla bloqueada. Documentar tiempos; no prometer intervalos exactos.
4. **Cobertura de concursos seguidos.** Revalidación acotada de avisos de seguidos fuera de la ventana reciente implementada (rotación 6/run, conditional GET con validators persistidos, revalidatedAt para rotación LRU, NOTICE_UPDATED con evidencia solo por cambio real, fallo sin perder estado). VERIFIED: build + 39 tests JVM + lint (0 errores); instalación conservando datos en 8912c62d y worker SUCCESS con revalidatedAt persistido (vacío, sin candidatos stale). NOT VERIFIED: cambio real físico de un aviso antiguo de seguido fuera de la ventana y paginación real de más de tres páginas nuevas.
5. **Recordatorios de apertura/cierre.** Implementados por transcurso del tiempo (NoticeReminder): ventanas CONFIRMED/SCHEDULED de seguidos, apertura ≤2 días antes del inicio y cierre ≤2 días antes del fin (solo tras abrir), claves estables por ventana que cancelan ante aplazamiento/cambio, solo cuando la fuente de la etapa se revisó en la corrida. VERIFIED: build + 41 tests JVM + lint (0 errores); instalación conservando datos con firedReminders persistido. NOT VERIFIED: entrega física de un recordatorio con ventana real cercana y cancelación por aplazamiento real en producción.
6. **Migraciones Room.** Corregir errores de tests instrumentados registrados y ejecutar migraciones con datos conservados. No desinstalar ni borrar datos para hacer pasar una migración.
7. **Diagnóstico y preferencias.** Revisar qué existe; completar última revisión, errores, alcance de fuentes, frecuencia y preferencias con estado real.

## Validación Android

Desde la raíz ejecutar: .\android\gradlew.bat -p android assembleDebug testDebugUnitTest lintDebug

Añadir pruebas instrumentadas y físicas cuando corresponda. Tests JVM no verifican entrega física ni periodo natural. Registrar lo no comprobado como NOT VERIFIED. Consultar [MANUAL_TESTING.md](MANUAL_TESTING.md) y la skill aplicable.
