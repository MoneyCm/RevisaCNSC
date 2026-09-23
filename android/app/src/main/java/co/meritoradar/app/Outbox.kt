package co.meritoradar.app

import com.google.gson.Gson
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val bogotaZone = ZoneId.of("America/Bogota")

/** Decisión del outbox durable por evento pendiente. */
enum class OutboxDecision { DELIVER, KEEP, DROP }

/**
 * Revisión pura de un evento pendiente. Replica la semántica del worker sin red ni
 * Android, para que sea probada por unitarias JVM:
 * - DROP: evento inexistente, concurso dejado de seguir, evento vencido (>7 días) o
 *   ilegible, o ventana oficial que ya no coincide con lo guardado (aplazamiento/cambio).
 * - KEEP: evento con etapa confirmada cuya fuente no fue rechecada en esta corrida
 *   ([freshUrls] null o sin la URL oficial): espera una revisión real con red.
 * - DELIVER: resto.
 */
object OutboxReview {
    fun review(
        event: EventInfo?,
        followed: Set<String>,
        processes: List<Process>,
        notices: List<LocalNotice>,
        freshUrls: Set<String>?,
        now: Instant
    ): OutboxDecision {
        if (event == null || event.processId !in followed) return OutboxDecision.DROP
        val detected = runCatching { Instant.parse(event.detectedAt) }.getOrNull() ?: return OutboxDecision.DROP
        if (detected < now.minus(Duration.ofDays(7))) return OutboxDecision.DROP
        val change = event.evidence?.new
        val stageId = change?.get("stage_id") ?: return OutboxDecision.DELIVER
        val process = processes.find { it.id == event.processId } ?: return OutboxDecision.DROP
        val current = StageDetector.build(notices, process, now).find { it.stage.id == stageId }?.stage
            ?: return OutboxDecision.DROP
        if (current.status != "SCHEDULED" || current.confidence != "CONFIRMED" ||
            current.startDate != change.get("start_date") ||
            current.endDate != change.get("end_date") ||
            current.endDate?.let { LocalDate.parse(it) < LocalDate.now(bogotaZone) } != false) {
            return OutboxDecision.DROP
        }
        // Nunca entregar una alerta crítica en cola desde una fuente no rechecada en la corrida.
        if (freshUrls == null || current.officialUrl !in freshUrls) return OutboxDecision.KEEP
        return OutboxDecision.DELIVER
    }
}

/**
 * Entrega lo pendiente del outbox durable y persiste el estado tras cada entrega.
 * Sin red: solo decide con lo guardado ([freshUrls] null conserva las alertas con
 * etapa hasta una revisión real). Devuelve el estado actualizado y lo entregado.
 */
suspend fun deliverOutbox(
    dao: RadarDao,
    notifier: LocalNotifier,
    gson: Gson,
    state: NoticeState,
    processes: List<Process>,
    followed: Set<String>,
    freshUrls: Set<String>?,
    now: Instant
): Pair<NoticeState, Int> {
    val pending = state.pending.toMutableSet()
    var delivered = 0
    for (id in state.pending) {
        val event = state.events.find { it.id == id }
        when (OutboxReview.review(event, followed, processes, state.notices, freshUrls, now)) {
            OutboxDecision.DROP -> { pending.remove(id); continue }
            OutboxDecision.KEEP -> continue
            OutboxDecision.DELIVER -> Unit
        }
        if (event == null || !notifier.hasNotificationPermission()) continue
        val posted = notifier.showEventNotification(event.id, event.processId, "", event.eventType,
            eventLabel(event.eventType), event.title, event.priority)
        if (!posted) continue
        pending.remove(id)
        delivered++
        dao.cache(ContentCache("notice_state", gson.toJson(state.copy(pending = pending.toSet())),
            System.currentTimeMillis()))
    }
    val flushed = state.copy(pending = pending.toSet())
    dao.cache(ContentCache("notice_state", gson.toJson(flushed), System.currentTimeMillis()))
    return flushed to delivered
}
