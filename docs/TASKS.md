## Auditoría Codex y alcance autorizado — 2026-09-21

Esta actualización prevalece sobre las notas históricas inferiores. Se revisaron a4b8553 y ee96b33: corrección de slug NOT NULL, esquemas y pruebas de migración, más documentación. Auditoría: 56/56 unitarias, 3/3 migraciones aisladas en 8912c62d, build correcto y lint 0 errores/19 advertencias. Los esquemas v1/v2 son reconstruidos; una base histórica real sigue NOT VERIFIED.

Estado leído del teléfono: radar.db v3 íntegra, 27 procesos, 0 seguidos y 0 entradas activity. No se atribuye la pérdida previa a una causa demostrada. Están instaladas producción y QA separadas. No reinstalar producción, borrar datos, seleccionar concursos ni probar notificaciones en este bloque.

Prioridad autorizada: (1) checkpoint y push de coordinación; (2) robustez del historial con regresiones; (3) pruebas completas y migraciones aisladas; (4) documentación, commit y push. No iniciar diagnóstico/preferencias ni funcionalidades nuevas. La validación real de alertas y Doze queda para otra tarea.

Historial PARCIAL: fallos en páginas posteriores pueden sustituir datos completos por parciales; se absorbe cancelación; URLs posteriores apuntan a página inicial; el detalle descarga dos veces la primera página y carece de pausa en este recorrido. Corregir exclusivamente estos problemas. Criterios: conservar caché ante fallo, propagar cancelación, URL por página, una descarga inicial y pausa prudente, con regresiones y suite completa aprobadas.

Git al iniciar esta fase: main dos commits delante del remoto; TASKS y HANDOFF sin seguimiento. Este checkpoint incorpora la coordinación; verificar sincronización con git fetch, git status y git rev-list, sin asumirla por una nota histórica.

# Tareas — Mérito Radar

Orden de relevo al 2026-09-21. Contrastar con código, [STATUS.md](STATUS.md) y [DECISIONS.md](DECISIONS.md) antes de implementar. No marcar DONE sin verificación.

Convención de estado:
- **DONE (verificado)**: implementado, compilado, probado y documentado.
- **PARCIAL (verificación física pendiente)**: implementado y probado en JVM/dispositivo, con restricciones físicas NOT VERIFIED documentadas.
- **PARCIAL**: implementado en parte; falta completar o verificar.
- **PENDIENTE**: no iniciado o sin avance.

## PENDIENTE (prioridad más alta)

1. **[NEXT_TASKS 7] Diagnóstico y preferencias — PENDIENTE**
   Revisar qué existe ya (repository expone estado/health; ajustes tienen notificación de prueba). Completar con estado real: última revisión, errores, alcance de fuentes, frecuencia y preferencias. Requiere definir qué se muestra y desde dónde se lee (Room + WorkManager). Depende de nada pendiente; puede arrancar directamente.
   Preferible antes: repoblar seguidos en el dispositivo (ver 8), para que el diagnóstico muestre estados reales.
   NOT VERIFIED pendiente acumulado: diagnóstico de fallo real por micrositio en el teléfono.

2. **[Entrega física con pantalla bloqueada] — PENDIENTE**
   Verificar una novedad crítica real del recorrido worker → outbox → bandeja (deep link) con pantalla bloqueada y app cerrada. Exigir antes de declarar la vigilancia completa. No inyectar avisos CNSC ficticios.

3. **[Paginación real >3 páginas del historial/micrositio] — PENDIENTE (físico)**
   La paginación Drupal acotada a 3 páginas está implementada y probada con unitarias. Falta un micrositio real con más de tres páginas de publicación para observar físicamente. Hoy DIAN 2676 tiene una sola página.

4. **[Migración con base histórica real] — PENDIENTE (físico)**
   Los tests instrumentados de migración (1→2, 2→3, 1→3) siembran datos propios. Falta una migración real de una base con datos previos del usuario. Hoy la DB real está en v3 y sin historial previo (ver 8).

5. **[Ejecución durante Doze prolongado] — PENDIENTE (físico)**
   Todo lo de segundo plano está sujeto a restricciones OEM/Doze; documentar tiempos, no prometer puntualidad de 15 minutos.

## PARCIAL

6. **[NEXT_TASKS 4] Revalidación de avisos antiguos de seguidos — PARCIAL (verificación física pendiente)**
   Implementada: rotación 6/run, conditional GET con validators persistidos, revalidatedAt para LRU, NOTICE_UPDATED solo por cambio real. Verificado en JVM y dispositivo (instalación + worker SUCCESS). NOT VERIFIED: cambio real físico de un aviso antiguo de seguido fuera de la ventana.

7. **[NEXT_TASKS 5] Recordatorios de apertura/cierre — PARCIAL (verificación física pendiente)**
   Implementados por transcurso del tiempo (NoticeReminder): ventanas CONFIRMED/SCHEDULED de seguidos, apertura ≤2 días antes del inicio, cierre ≤2 días antes del fin, claves estables que cancelan ante aplazamiento. Verificado en JVM y dispositivo (firedReminders persistido). NOT VERIFIED: entrega física de un recordatorio con ventana real cercana y cancelación por aplazamiento real.

8. **[Repoblar seguidos/actividad en el dispositivo] — PARCIAL (verificación 2026-09-21, sin seguimiento activo propio)**
   Al iniciar el bloque de migraciones (2026-09-21), la app principal `co.meritoradar.app` NO estaba instalada en 8912c62d (solo la variante QA). La reinstalación recreó radar.db y following/caché de actividad quedaron vacíos. **Verificación no destructiva 2026-09-21 (orquestador):** el dispositivo ya fue repoblado parcialmente por el usuario: 3 seguidos activos (DIAN 2676 `dian-2676`, Aerocivil Primera Fase `aerocivil-primera-fase`, Corporaciones Autónomas Regionales CAR `corporaciones-autonomas-regionales-car`), con activity_check/activity recientes (21:00Z) y notice_state inicializado (9 avisos/9 eventos, pending 0). Siguen fuera de seguimiento PGN 2407 y ESE2 (antes seguidos); si se desean, requiere acción del usuario en la app (no se modifica following por ADB).
   No es deuda de código; es estado del dispositivo.

## DONE

9. **[NEXT_TASKS 6] Migraciones Room instrumentadas — DONE (2026-09-21)**
   Corregido MigrationTest.kt (no compilaba contra Room 2.6.1), activado exportSchema con schemas JSON 1/2/3, tres rutas de migración con datos sembrados, bug real corregido (MIGRATION_2_3: slug ahora TEXT NOT NULL DEFAULT ''). VERIFIED: build + assembleDebugAndroidTest + lint + 3/3 tests instrumentados en 8912c62d. Commits a4b8553 y ee96b33 (sin push hasta handoff).
   NOT VERIFIED: migración de base histórica real con datos previos del usuario.

9b. **[Robustez del historial del micrositio — DONE (2026-09-21)]**
    Cinco defectos corregidos con regresiones: (a) fallo en página posterior se propaga y conserva la caché previa (ActivityRefresh reporta error sin guardar); (b) CancellationException se propaga sin guardar ni reportar; (c) cada publicación conserva la URL real de su página; (d) la consulta manual descarga una sola vez la primera página (nuevo ProcessMicrositeReader comparte identidad+actividad); (e) pausa de 2 s entre solicitudes sucesivas en el recorrido. VERIFIED: assembleDebug + testDebugUnitTest + lintDebug + assembleDebugAndroidTest BUILD SUCCESSFUL; 61 tests JVM (15 ProcessActivityTest), lint 0 errores/19 advertencias, MigrationTest 3/3 vía am instrument sin desinstalar, radar.db real intacta. Commit y push locales (ver git log -1).
    NOT VERIFIED: fallo tardío en dispositivo real, recorrido real >3 páginas, cronometría física de la pausa.

10. **[Historial de publicaciones con paginación acotada] — PARCIAL (auditoría 2026-09-21)**
    ProcessActivityParser.parseAll conserva hasta 3 páginas del mismo micrositio oficial y varias publicaciones fechadas (título, resumen, fecha Bogotá, URL) en publications, ordenadas desc, sin alertas retroactivas. VERIFIED en JVM (56 tests) y dispositivo (DIAN 2676 con 15 publicaciones). Commit cdce4cf (pusheado).

11. **[NEXT_TASKS 3] Vigilancia prolongada y recuperación de red — DONE (2026-09-20)**
    Recuperación offline→online verificada físicamente sin pérdida de datos. NOT VERIFIED: Doze prolongado y entrega crítica real con pantalla bloqueada.

12. **[Outbox conserva eventos con canal bloqueado] — DONE (2026-09-20)**
    showEventNotification devuelve entrega real; isChannelBlocked considera bloqueado canal inexistente/IMPORTANCE_NONE; NoticeMonitor no retira de pending sin entrega confirmada. Verificado con NotificationDeliveryTest en QA.

13. **[Transporte y colisiones de notificación] — DONE (2026-09-20)**
    eventId completo como tag, PendingIntent con URI del eventId. Verificado en QA.

14. **[Backend local-first] — DONE (referencia)**
    Backend FastAPI/PostgreSQL conservado como tooling de referencia; Android es la arquitectura de producción. No es requisito de ejecución.

## Dependencias

- 8 (repoblar dispositivo) habilita verificación física de 1, 2, 4, 5.
- 1 (diagnóstico/preferencias) muestra el estado real que 8 debe recuperar.
- 2 y 5 dependen de una publicación oficial real con ventana confirmada de un seguido.