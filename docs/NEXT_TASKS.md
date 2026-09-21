# Próximas tareas

Orden de relevo al 2026-09-20. Contrastar con código y [STATUS.md](STATUS.md) antes de implementar.

1. **Comprobar punto de control y preparar respaldo.** Consultar git log -1 y git status antes de comenzar. El punto inicial conserva también las limitaciones documentadas, no implica vigilancia completamente verificada. No hay remoto; publicar un respaldo solo con destino y autorización del usuario.
2. **Notificaciones de eventos y enlaces.** Transporte manual, tres pruebas QA de repetición, colisiones, destino del PendingIntent y rechazo por canal bloqueado verificados: showEventNotification devuelve entrega real y NoticeMonitor conserva el evento en la outbox si el canal está bloqueado o no hay permiso. Falta entrega oficial con pantalla bloqueada y representación del detalle/evento correcto. Pendiente también la conservación física end-to-end de la outbox con canal bloqueado a través del worker. No inyectar avisos ficticios en producción.
3. **Vigilancia prolongada y recuperación.** Una ejecución natural y su reprogramación a 15 minutos están verificadas. Falta recuperación de red y comportamiento prolongado bajo restricciones/Doze. Documentar tiempos; no prometer intervalos exactos.
4. **Cobertura de concursos seguidos.** Revisar avisos anteriores y modificaciones fuera de la ventana reciente, con límites, caché, evidencia y respeto a robots.txt. Probar paginación, duplicados, ambigüedad y fallos sin perder estado.
5. **Recordatorios de apertura/cierre.** Usar ventanas oficiales confirmadas y fuentes actualizadas; deduplicar y cancelar ante cambios/aplazamientos. Distinguir detección de fechas nuevas de recordatorios por transcurso del tiempo.
6. **Migraciones Room.** Corregir errores de tests instrumentados registrados y ejecutar migraciones con datos conservados. No desinstalar ni borrar datos para hacer pasar una migración.
7. **Diagnóstico y preferencias.** Revisar qué existe; completar última revisión, errores, alcance de fuentes, frecuencia y preferencias con estado real.

## Validación Android

Desde la raíz ejecutar: .\android\gradlew.bat -p android assembleDebug testDebugUnitTest lintDebug

Añadir pruebas instrumentadas y físicas cuando corresponda. Tests JVM no verifican entrega física ni periodo natural. Registrar lo no comprobado como NOT VERIFIED. Consultar [MANUAL_TESTING.md](MANUAL_TESTING.md) y la skill aplicable.
