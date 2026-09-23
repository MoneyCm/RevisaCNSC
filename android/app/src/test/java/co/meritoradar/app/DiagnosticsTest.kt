package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DiagnosticsTest {

    private fun now() = Instant.parse("2026-09-21T12:00:00Z")

    private fun process(id: String, name: String, slug: String) =
        Process(id, slug = slug, name = name, officialUrl = "https://www.cnsc.gov.co/convocatorias/" + id)

    private fun check(processId: String, name: String, at: String, error: String? = null) =
        ActivityCheck(processId, name, at, error)

    private fun noticeState(notices: Int, events: Int, pending: Int, initialized: Boolean = true) =
        NoticeState(
            notices = List(notices) { i -> LocalNotice("/node/$i", null, "Aviso $i", null, emptyList(), emptyList()) },
            events = List(events) { i -> EventInfo("e$i", "p$i", "NOTICE_PUBLISHED", "T$i", "INFO", "CONFIRMED", null, now().toString(), false, null) },
            pending = List(pending) { i -> "e$i" }.toSet(),
            initialized = initialized
        )

    @Test fun clampsPeriodToWorkManagerMinimum() {
        assertEquals(15, Diagnostics.coercePeriod(5))
        assertEquals(15, Diagnostics.coercePeriod(15))
        assertEquals(30, Diagnostics.coercePeriod(30))
    }

    @Test fun statusPrecedencePausedWins() {
        val empty: List<MicrositeDiagnosis> = emptyList()
        assertEquals(WatchStatus.PAUSED, Diagnostics.status(true, "ENQUEUED", true, empty, true).first)
        assertEquals(WatchStatus.PAUSED, Diagnostics.status(true, null, false, empty, false).first)
    }

    @Test fun missingOrFailedProgramIsAtRisk() {
        val empty: List<MicrositeDiagnosis> = emptyList()
        assertEquals(WatchStatus.AT_RISK, Diagnostics.status(false, null, true, empty, true).first)
        assertEquals(WatchStatus.AT_RISK, Diagnostics.status(false, "FAILED", true, empty, true).first)
        assertEquals(WatchStatus.AT_RISK, Diagnostics.status(false, "CANCELLED", true, empty, true).first)
    }

    @Test fun enqueuedWithoutInitializationIsWarmingUp() {
        val empty: List<MicrositeDiagnosis> = emptyList()
        assertEquals(WatchStatus.WARMING_UP, Diagnostics.status(false, "ENQUEUED", false, empty, true).first)
    }

    @Test fun recentFailureOrDeniedPermissionIsDegraded() {
        val failure = MicrositeDiagnosis("p1", "Proceso A", now(), "HTTP 500", true)
        assertEquals(WatchStatus.DEGRADED, Diagnostics.status(false, "ENQUEUED", true, listOf(failure), true).first)
        assertEquals(WatchStatus.DEGRADED, Diagnostics.status(false, "ENQUEUED", true, emptyList(), false).first)
    }

    @Test fun healthySetupIsOperational() {
        assertEquals(WatchStatus.OPERATIONAL,
            Diagnostics.status(false, "ENQUEUED", true, emptyList(), true).first)
        assertEquals(WatchStatus.OPERATIONAL,
            Diagnostics.status(false, "RUNNING", true, emptyList(), true).first)
    }

    @Test fun micrositeDiagnosisGroupsAndFlagsRecentFailures() {
        val processes = listOf(process("p1", "Proceso A", "proceso-a"), process("p2", "Proceso B", "proceso-b"))
        val checks = listOf(
            check("p1", "Proceso A", now().minusSeconds(3600).toString(), "No se pudo leer el micrositio."),
            check("p1", "Proceso A", now().minusSeconds(7200).toString(), null),
            check("p2", "Proceso B", now().minusSeconds(28 * 3600).toString(), "HTTP 500")
        )
        val diag = Diagnostics.micrositeDiagnosis(processes, setOf("p1", "p2"), checks, now())
        val byId = diag.associateBy { it.processId }
        assertEquals(2, diag.size)
        assertEquals("Proceso A", byId["p1"]?.processName)
        assertEquals("No se pudo leer el micrositio.", byId["p1"]?.lastError)
        assertTrue(byId["p1"]!!.hasRecentFailure)
        // Failures older than 24 h are not "recientes".
        assertFalse(byId["p2"]!!.hasRecentFailure)
        assertEquals("HTTP 500", byId["p2"]?.lastError)
    }

    @Test fun buildDerivesRealCountsNamesAndNextRun() {
        val processes = listOf(
            process("p1", "DIAN 2676", "dian-2676"),
            process("p2", "Aerocivil Primera Fase", "aerocivil-primera-fase"),
            process("p3", "CAR", "corporaciones-autonomas-regionales-car")
        )
        val checks = listOf(check("p1", "DIAN 2676", now().toString(), null))
        val periodic = PeriodicWorkSnapshot(state = "ENQUEUED", runAttemptCount = 2,
            nextRunAtMillis = now().plusSeconds(900).toEpochMilli())
        val snapshot = Diagnostics.build(
            now = now(),
            processes = processes,
            followedIds = setOf("p1", "p2"),
            checks = checks,
            noticeState = noticeState(9, 9, 0),
            noticeStateSavedAt = now().minusSeconds(600),
            periodic = periodic,
            periodMinutes = 30,
            paused = false,
            notificationPermission = true,
            channelsBlocked = emptyList(),
            batteryOptimizationExempt = false
        )
        assertEquals(WatchStatus.OPERATIONAL, snapshot.status)
        assertEquals(3, snapshot.catalogCount)
        assertEquals(9, snapshot.noticesCount)
        assertEquals(9, snapshot.eventsCount)
        assertEquals(0, snapshot.pendingCount)
        assertEquals(listOf("Aerocivil Primera Fase" to "aerocivil-primera-fase", "DIAN 2676" to "dian-2676"), snapshot.followed)
        assertEquals(now().plusSeconds(900), snapshot.nextRunAt)
        assertEquals(48, snapshot.runsPerDay)
        assertTrue(snapshot.initialized)
        assertEquals(2, snapshot.microsites.size)
        assertEquals(0, snapshot.recentMicrositeFailures.size)
    }

    @Test fun buildReportsRecentFailureAndDeniedPermission() {
        val processes = listOf(process("p1", "Proceso A", "proceso-a"))
        val checks = listOf(check("p1", "Proceso A", now().minusSeconds(600).toString(), "HTTP 500"))
        val snapshot = Diagnostics.build(
            now = now(), processes = processes, followedIds = setOf("p1"), checks = checks,
            noticeState = noticeState(1, 1, 0), noticeStateSavedAt = now(), periodic = PeriodicWorkSnapshot("ENQUEUED", 1, null),
            periodMinutes = 15, paused = false, notificationPermission = false,
            channelsBlocked = listOf("Alertas urgentes"), batteryOptimizationExempt = null
        )
        assertEquals(WatchStatus.DEGRADED, snapshot.status)
        assertEquals(1, snapshot.microsites.size)
        assertTrue(snapshot.microsites.first().hasRecentFailure)
        assertEquals(listOf("Alertas urgentes"), snapshot.channelsBlocked)
    }

    @Test fun pausedBuildShowsPausedStatusWithoutProgram() {
        val snapshot = Diagnostics.build(
            now = now(), processes = listOf(process("p1", "Proceso A", "proceso-a")), followedIds = emptySet(),
            checks = emptyList(), noticeState = null, noticeStateSavedAt = null, periodic = null,
            periodMinutes = 15, paused = true, notificationPermission = null, channelsBlocked = emptyList(),
            batteryOptimizationExempt = null
        )
        assertEquals(WatchStatus.PAUSED, snapshot.status)
        assertTrue(snapshot.lines().any { it.label == "Programación actual" && it.value.contains("pausa") })
        assertEquals(0, snapshot.runsPerDay)
    }

    @Test fun linesCoverEveryDiagnosticArea() {
        val snapshot = Diagnostics.build(
            now = now(), processes = listOf(process("p1", "Proceso A", "proceso-a")), followedIds = setOf("p1"),
            checks = emptyList(), noticeState = noticeState(1, 1, 1), noticeStateSavedAt = now(),
            periodic = PeriodicWorkSnapshot("ENQUEUED", 1, null), periodMinutes = 15, paused = false,
            notificationPermission = true, channelsBlocked = emptyList(), batteryOptimizationExempt = false
        )
        val labels = snapshot.lines().map { it.label }
        assertTrue("última revisión", labels.any { it.contains("Última revisión") })
        assertTrue("programación", labels.any { it.contains("Programación") })
        assertTrue("estado general", labels.any { it.contains("Estado de vigilancia") })
        assertTrue("seguidos", labels.any { it == "Concursos en seguimiento" })
        assertTrue("errores", labels.any { it.contains("Errores recientes") })
        assertTrue("fuentes", labels.any { it.contains("Fuentes vigiladas") })
        assertTrue("frecuencia", labels.any { it.contains("Revisión solicitada") })
        assertTrue("notificaciones", labels.any { it == "Notificaciones" })
        assertTrue("restricciones", labels.any { it.contains("Optimización de batería") })
    }

    @Test fun selectActivePeriodicIgnoresHistoricalFinishedState() {
        // Histórico CANCELLED + actual ENQUEUED → gana el actual activo.
        val cancelledThenEnqueued = listOf(
            PeriodicWorkCandidate("CANCELLED", 2, now().minusSeconds(900).toEpochMilli()),
            PeriodicWorkCandidate("ENQUEUED", 0, now().plusSeconds(900).toEpochMilli())
        )
        val enqueued = Diagnostics.selectActivePeriodic(cancelledThenEnqueued)
        assertEquals("ENQUEUED", enqueued?.state)
        assertEquals(now().plusSeconds(900).toEpochMilli(), enqueued?.nextRunAtMillis)
        // Histórico CANCELLED + actual RUNNING → gana RUNNING.
        val cancelledThenRunning = listOf(
            PeriodicWorkCandidate("CANCELLED", 1, null),
            PeriodicWorkCandidate("RUNNING", 1, null)
        )
        assertEquals("RUNNING", Diagnostics.selectActivePeriodic(cancelledThenRunning)?.state)
        // Históricos terminados también pueden ser SUCCEEDED/FAILED antes del actual activo.
        assertEquals("ENQUEUED", Diagnostics.selectActivePeriodic(listOf(
            PeriodicWorkCandidate("SUCCEEDED", 3, null),
            PeriodicWorkCandidate("FAILED", 1, null),
            PeriodicWorkCandidate("ENQUEUED", 0, null)
        ))?.state)
    }

    @Test fun selectActivePeriodicWithoutActiveWorkIsNull() {
        // Solo CANCELLED histórico → ausencia de programación.
        assertNull(Diagnostics.selectActivePeriodic(listOf(PeriodicWorkCandidate("CANCELLED", 1, null))))
        // Lista vacía → ausencia de programación.
        assertNull(Diagnostics.selectActivePeriodic(emptyList()))
        // Históricos SUCCEEDED/FAILED sin activo → ausencia de programación.
        assertNull(Diagnostics.selectActivePeriodic(listOf(
            PeriodicWorkCandidate("SUCCEEDED", 2, null),
            PeriodicWorkCandidate("FAILED", 1, null)
        )))
    }

    @Test fun environmentRefreshConvergesDiagnosticsWhenPlatformChanges() {
        val processes = listOf(process("p1", "Proceso A", "proceso-a"))
        fun build(permission: Boolean?): DiagnosticsSnapshot = Diagnostics.build(
            now = now(), processes = processes, followedIds = setOf("p1"), checks = emptyList(),
            noticeState = noticeState(1, 1, 0), noticeStateSavedAt = now(),
            periodic = PeriodicWorkSnapshot("ENQUEUED", 1, null), periodMinutes = 15, paused = false,
            notificationPermission = permission, channelsBlocked = emptyList(), batteryOptimizationExempt = false
        )
        // Antes del refresco el permiso estaba concedido (operativo); tras concederlo de nuevo
        // la relectura vuelve a operativo; un cambio en el teléfono se refleja en el próximo build.
        assertEquals(WatchStatus.OPERATIONAL, build(true).status)
        assertEquals(WatchStatus.DEGRADED, build(false).status)
        assertEquals(WatchStatus.OPERATIONAL, build(true).status)
        // El endpoint que alimenta el entorno cambia la factura de canales bloqueados.
        val blocked = Diagnostics.build(
            now = now(), processes = processes, followedIds = setOf("p1"), checks = emptyList(),
            noticeState = noticeState(1, 1, 0), noticeStateSavedAt = now(),
            periodic = PeriodicWorkSnapshot("ENQUEUED", 1, null), periodMinutes = 15, paused = false,
            notificationPermission = true, channelsBlocked = listOf("Información general"),
            batteryOptimizationExempt = false
        )
        assertTrue(blocked.lines().any { it.label == "Notificaciones" && it.value.contains("bloqueado") })
    }

    @Test fun resumeAfterPauseDerivesActiveProgram() {
        val empty: List<MicrositeDiagnosis> = emptyList()
        // Pausa → sin trabajo activo y estado PAUSED.
        assertEquals(WatchStatus.PAUSED, Diagnostics.status(true, null, true, empty, true).first)
        // Reanudar → la re-programación activa vuelve a ser ENQUEUED y el estado retorna operativo.
        assertEquals(WatchStatus.OPERATIONAL, Diagnostics.status(false, "ENQUEUED", true, empty, true).first)
        val resumed = Diagnostics.build(
            now = now(), processes = listOf(process("p1", "Proceso A", "proceso-a")), followedIds = setOf("p1"),
            checks = emptyList(), noticeState = noticeState(1, 1, 0), noticeStateSavedAt = now(),
            periodic = PeriodicWorkSnapshot("ENQUEUED", 1, now().plusSeconds(900).toEpochMilli()),
            periodMinutes = 15, paused = false, notificationPermission = true, channelsBlocked = emptyList(),
            batteryOptimizationExempt = false
        )
        assertFalse(resumed.paused)
        assertEquals(WatchStatus.OPERATIONAL, resumed.status)
        assertEquals(96, resumed.runsPerDay)
        assertTrue(resumed.lines().any { it.label == "Programación actual" && it.value == "ENQUEUED" })
    }

    private fun pendingState() = NoticeState(
        notices = emptyList(),
        events = listOf(EventInfo("e0", "p1", "NOTICE_PUBLISHED", "Aviso oficial", "INFO",
            "CONFIRMED", "2026-09-20T15:00:00Z", now().toString(), true, null)),
        pending = setOf("e0", "e-perdido"),
        initialized = true
    )

    private fun pendingSnapshot(state: NoticeState, channelsBlocked: List<String> = emptyList()) =
        Diagnostics.build(
            now = now(),
            processes = listOf(process("p1", "DIAN 2676", "dian-2676")),
            followedIds = setOf("p1"),
            checks = emptyList(),
            noticeState = state,
            noticeStateSavedAt = now(),
            periodic = null,
            periodMinutes = 30,
            paused = false,
            notificationPermission = true,
            channelsBlocked = channelsBlocked,
            batteryOptimizationExempt = null
        )

    @Test fun buildExposesPendingDeliveryDetailAndAction() {
        val snapshot = pendingSnapshot(pendingState())
        assertEquals(2, snapshot.pendingCount)
        assertEquals(1, snapshot.pendingEvents.size)
        assertEquals("DIAN 2676", snapshot.pendingEvents.single().processName)
        assertEquals("Aviso oficial", snapshot.pendingEvents.single().title)
        val line = snapshot.lines().first { it.label == "Avisos pendientes de notificar" }
        assertTrue(line.value.contains("DIAN 2676"))
        assertTrue(snapshot.lines().any { it.label == "Acción sugerida" && it.value.contains("al abrir la app") })
    }
    @Test fun pendingWithBlockedChannelSuggestsSystemSettings() {
        val snapshot = pendingSnapshot(pendingState(), channelsBlocked = listOf("Alertas urgentes"))
        assertTrue(snapshot.lines().any { it.label == "Acción sugerida" && it.value.contains("canales de notificación") })
    }
    @Test fun successiveIntervalChangesKeepClampingAndDailyRuns() {
        // Cambios sucesivos 15 → 30 → 60 → 15: el valor se acota a partir del mínimo de WorkManager.
        val sequence = listOf(15, 30, 60, 120, 15).map(Diagnostics::coercePeriod)
        assertEquals(listOf(15, 30, 60, 120, 15), sequence)
        val daily = sequence.map(Diagnostics::runsPerDay)
        assertEquals(listOf(96, 48, 24, 12, 96), daily)
        // Un intervalo por debajo del mínimo se persiste reencuadre al mínimo, no como inválido.
        assertEquals(15, Diagnostics.coercePeriod(5))
        assertEquals(60, Diagnostics.coercePeriod(60))
    }
}