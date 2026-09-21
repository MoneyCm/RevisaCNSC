package co.meritoradar.app

import java.time.Duration
import java.time.Instant

/**
 * La diagnosis se construye únicamente a partir de fuentes reales (Room, WorkManager
 * y configuración Android). Un dato no disponible se muestra como desconocido; nunca
 * se inventa periodismo, conteo ni estado. La frecuencia se presenta como una solicitud
 * a WorkManager, no como una cita exacta.
 */
data class PeriodicWorkSnapshot(
    val state: String,
    val runAttemptCount: Int,
    val nextRunAtMillis: Long?,
    val periodic: Boolean = true
)

/** Representa el estado de un resultado de WorkInfo para el trabajo periódico único.
 *  `getWorkInfosForUniqueWorkFlow` puede devolver también entradas históricas terminadas
 *  (CANCELLED/SUCCEEDED/FAILED); la selección del trabajo vigente debe ignorarlas. */
data class PeriodicWorkCandidate(
    val state: String,
    val runAttemptCount: Int,
    val nextRunAtMillis: Long?
)

data class MicrositeDiagnosis(
    val processId: String,
    val processName: String,
    val lastAttemptAt: Instant?,
    val lastError: String?,
    val hasRecentFailure: Boolean
)

enum class WatchStatus(val label: String) {
    OPERATIONAL("Vigilando correctamente"),
    DEGRADED("Vigilando con problemas"),
    AT_RISK("Programación sin revisión activa"),
    WARMING_UP("Primera revisión pendiente"),
    PAUSED("Vigilancia en pausa"),
    UNKNOWN("Estado desconocido")
}

enum class LineKind { OK, WARN, ERROR, INFO }

data class DiagnosticLine(val label: String, val value: String, val kind: LineKind = LineKind.INFO)

data class DiagnosticsSnapshot(
    val generatedAt: Instant,
    val status: WatchStatus,
    val statusReason: String,
    val lastNoticesCheckAt: Instant?,
    val lastMicrositeAttemptAt: Instant?,
    val lastMicrositeSuccessAt: Instant?,
    val periodicState: String?,
    val periodMinutes: Int,
    val nextRunAt: Instant?,
    val followed: List<Pair<String, String>>,
    val catalogCount: Int,
    val noticesCount: Int,
    val eventsCount: Int,
    val pendingCount: Int,
    val initialized: Boolean,
    val microsites: List<MicrositeDiagnosis>,
    val recentMicrositeFailures: List<MicrositeDiagnosis>,
    val notificationPermission: Boolean?,
    val channelsBlocked: List<String>,
    val batteryOptimizationExempt: Boolean?,
    val runsPerDay: Int,
    val paused: Boolean
) {
    fun lines(): List<DiagnosticLine> {
        val out = mutableListOf<DiagnosticLine>()
        val statusKind = when (status) {
            WatchStatus.OPERATIONAL -> LineKind.OK
            WatchStatus.DEGRADED, WatchStatus.AT_RISK, WatchStatus.WARMING_UP -> LineKind.WARN
            else -> LineKind.ERROR
        }
        out += DiagnosticLine("Estado de vigilancia", status.label, statusKind)
        out += DiagnosticLine("Última revisión de avisos CNSC",
            lastNoticesCheckAt?.let { Diagnostics.formatInstant(it) } ?: "Sin revisión de avisos aún", statusKind)
        out += DiagnosticLine("Último intento en micrositios seguidos",
            lastMicrositeAttemptAt?.let { Diagnostics.formatInstant(it) } ?: "Sin intento aún",
            if (recentMicrositeFailures.isEmpty()) LineKind.INFO else LineKind.WARN)
        out += DiagnosticLine("Última actividad verificada en micrositios",
            lastMicrositeSuccessAt?.let { Diagnostics.formatInstant(it) } ?: "Sin actividad verificada aún")
        out += DiagnosticLine("Revisión solicitada cada", if (paused) "En pausa" else "$periodMinutes minutos" +
            " (WorkManager lo solicita; Android puede retrasarlo o agruparlo)")
        out += DiagnosticLine("Próxima ejecución programada",
            nextRunAt?.let { Diagnostics.formatInstant(it) } ?: "Por determinar si Android lo agrupa", LineKind.INFO)
        out += DiagnosticLine("Programación actual",
            periodicState ?: "Sin trabajo programado (en pausa)",
            if (periodicState != null && periodicState == "ENQUEUED") LineKind.OK else LineKind.WARN)
        out += DiagnosticLine("Concursos en seguimiento", followed.size.toString() +
            if (followed.isEmpty()) " (elige concursos desde la pestaña Concursos)" else "")
        if (followed.isNotEmpty()) out += DiagnosticLine("Concursos", followed.joinToString(", ") { it.first })
        out += DiagnosticLine("Fuentes vigiladas", "Catálogo: $catalogCount · Avisos guardados: $noticesCount · " +
            "Micrositios seguidos: ${followed.size}")
        out += DiagnosticLine("Errores recientes por micrositio",
            if (recentMicrositeFailures.isEmpty()) "Sin errores en las últimas 24 h" else
                recentMicrositeFailures.joinToString("; ") { "${it.processName}: ${it.lastError ?: "fallo"}" },
            if (recentMicrositeFailures.isEmpty()) LineKind.OK else LineKind.ERROR)
        out += DiagnosticLine("Avisos guardados", noticesCount.toString())
        out += DiagnosticLine("Eventos guardados", eventsCount.toString())
        out += DiagnosticLine("Avisos pendientes de notificar", pendingCount.toString(),
            if (pendingCount == 0) LineKind.INFO else LineKind.WARN)
        out += DiagnosticLine(
            "Notificaciones",
            when {
                notificationPermission == null -> "Estado del permiso no disponible"
                !notificationPermission -> "Permiso denegado: los avisos se guardan pero no se notifican"
                channelsBlocked.isEmpty() -> "Permiso concedido y canales activos"
                else -> "Permiso concedido, canal(es) bloqueado(s): " + channelsBlocked.joinToString(", ")
            },
            if (notificationPermission == false || channelsBlocked.isNotEmpty()) LineKind.WARN else LineKind.OK)
        out += DiagnosticLine("Optimización de batería",
            when (batteryOptimizationExempt) {
                null -> "No disponible"
                true -> "La app está excluida de la optimización de batería"
                false -> "No excluida: Android puede retrasar revisiones en segundo plano (recomendado excluirla si quieres avisos puntuales)"
            },
            if (batteryOptimizationExempt == false) LineKind.WARN else LineKind.INFO)
        val pendingFailures = recentMicrositeFailures.size
        out += DiagnosticLine("Ejecuciones diarias estimadas",
            if (paused) "En pausa: no hay revisiones programadas"
            else if (periodMinutes <= 0) "Sin programación"
            else "${runsPerDay} aproximadas (24 h / $periodMinutes min)",
            if (pendingFailures > 0) LineKind.WARN else LineKind.OK)
        if (notificationPermission == false) {
            out += DiagnosticLine("Acción sugerida", "Concede el permiso de notificaciones en los ajustes del teléfono", LineKind.WARN)
        }
        return out
    }
}

object Diagnostics {
    const val MIN_PERIOD_MINUTES = 15
    const val RECENT_FAILURE_WINDOW_HOURS = 24L

    val PERIOD_OPTIONS = listOf(15, 30, 60, 120)

    fun coercePeriod(minutes: Int): Int = minutes.coerceAtLeast(MIN_PERIOD_MINUTES)

    fun formatInstant(instant: Instant): String = instant.toString()

    /** Estado general con precedencia: pausada > sin programación > sin primera revisión > degradada > operativa. */
    fun status(
        paused: Boolean,
        periodicState: String?,
        initialized: Boolean,
        recentFailures: List<MicrositeDiagnosis>,
        notificationPermission: Boolean?
    ): Pair<WatchStatus, String> = when {
        paused -> WatchStatus.PAUSED to "La vigilancia está en pausa: no hay revisiones programadas."
        periodicState == null -> WatchStatus.AT_RISK to "No hay trabajo de vigilancia programado."
        periodicState != "ENQUEUED" && periodicState != "RUNNING" ->
            WatchStatus.AT_RISK to "El trabajo de vigilancia está en estado $periodicState."
        !initialized -> WatchStatus.WARMING_UP to "La primera revisión de avisos aún no se ha completado (hasta entonces no se emiten alertas)."
        recentFailures.isNotEmpty() ->
            WatchStatus.DEGRADED to "${recentFailures.size} micrositio(s) seguido(s) fallaron en las últimas 24 h."
        notificationPermission == false ->
            WatchStatus.DEGRADED to "El permiso de notificaciones está denegado: los avisos se guardan pero no se entregan."
        else -> WatchStatus.OPERATIONAL to "Catálogo, avisos y micrositios seguidos se revisan según la programación."
    }

    fun micrositeDiagnosis(
        processes: List<Process>,
        followedIds: Set<String>,
        checks: List<ActivityCheck>,
        now: Instant
    ): List<MicrositeDiagnosis> {
        val byProcess = checks.groupBy { it.processId }
        return processes.filter { it.id in followedIds }.sortedBy { it.name }.map { process ->
            val entries = byProcess[process.id].orEmpty()
            val last = entries.maxByOrNull { runCatching { Instant.parse(it.checkedAt) }.getOrNull() ?: Instant.MIN }
            val lastAttemptAt = last?.let { runCatching { Instant.parse(it.checkedAt) }.getOrNull() }
            val recent = lastAttemptAt != null &&
                Duration.between(lastAttemptAt, now).toHours() <= RECENT_FAILURE_WINDOW_HOURS
            MicrositeDiagnosis(
                processId = process.id,
                processName = process.name,
                lastAttemptAt = lastAttemptAt,
                lastError = last?.error,
                hasRecentFailure = recent && last?.error != null && lastAttemptAt != null
            )
        }
    }

    fun runsPerDay(periodMinutes: Int): Int = if (periodMinutes <= 0) 0 else (24 * 60) / periodMinutes

    /**
     * Selecciona de forma determinística el trabajo periódico actualmente activo.
     * Un trabajo histórico CANCELLED/SUCCEEDED/FAILED nunca debe ganar sobre uno activo
     * (ENQUEUED o RUNNING); si no hay trabajo activo devuelve null, que el diagnóstico
     * interpreta como ausencia de programación.
     */
    fun selectActivePeriodic(candidates: List<PeriodicWorkCandidate>): PeriodicWorkSnapshot? {
        val active = candidates.firstOrNull { it.state == "ENQUEUED" || it.state == "RUNNING" } ?: return null
        return PeriodicWorkSnapshot(
            state = active.state,
            runAttemptCount = active.runAttemptCount,
            nextRunAtMillis = active.nextRunAtMillis,
            periodic = true
        )
    }

    fun build(
        now: Instant,
        processes: List<Process>,
        followedIds: Set<String>,
        checks: List<ActivityCheck>,
        noticeState: NoticeState?,
        noticeStateSavedAt: Instant?,
        periodic: PeriodicWorkSnapshot?,
        periodMinutes: Int,
        paused: Boolean,
        notificationPermission: Boolean?,
        channelsBlocked: List<String>,
        batteryOptimizationExempt: Boolean?
    ): DiagnosticsSnapshot {
        val microsites = micrositeDiagnosis(processes, followedIds, checks, now)
        val recentFailures = microsites.filter { it.hasRecentFailure }
        val (status, reason) = status(paused, periodic?.state, noticeState?.initialized == true, recentFailures,
            notificationPermission)
        val followed = processes.filter { it.id in followedIds }.sortedBy { it.name }.map { it.name to it.slug }
        return DiagnosticsSnapshot(
            generatedAt = now,
            status = status,
            statusReason = reason,
            lastNoticesCheckAt = noticeStateSavedAt,
            lastMicrositeAttemptAt = microsites.mapNotNull { it.lastAttemptAt }.maxOrNull(),
            lastMicrositeSuccessAt = microsites.mapNotNull { microsite ->
                checks.filter { c -> c.processId == microsite.processId && c.error == null }
                    .mapNotNull { c -> runCatching { Instant.parse(c.checkedAt) }.getOrNull() }
                    .maxOrNull()
            }.maxOrNull(),
            periodicState = periodic?.state,
            periodMinutes = periodMinutes,
            nextRunAt = periodic?.nextRunAtMillis?.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it) },
            followed = followed,
            catalogCount = processes.size,
            noticesCount = noticeState?.notices?.size ?: 0,
            eventsCount = noticeState?.events?.size ?: 0,
            pendingCount = noticeState?.pending?.size ?: 0,
            initialized = noticeState?.initialized ?: false,
            microsites = microsites,
            recentMicrositeFailures = recentFailures,
            notificationPermission = notificationPermission,
            channelsBlocked = channelsBlocked,
            batteryOptimizationExempt = batteryOptimizationExempt,
            runsPerDay = if (paused) 0 else runsPerDay(periodMinutes),
            paused = paused
        )
    }
}