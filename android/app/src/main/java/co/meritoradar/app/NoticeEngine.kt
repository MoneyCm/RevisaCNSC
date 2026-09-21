package co.meritoradar.app

import java.time.Instant
import java.time.Duration

data class NoticeState(
    val notices: List<LocalNotice> = emptyList(),
    val events: List<EventInfo> = emptyList(),
    val pending: Set<String> = emptySet(),
    val initialized: Boolean = false,
    val validators: Map<String, Map<String, String>> = emptyMap()
)

/** Publication-level changes only. Never interprets a publication as an open stage. */
object NoticeEngine {
    fun merge(state: NoticeState, incoming: List<LocalNotice>, processes: List<Process>,
        followed: Set<String>, now: Instant): NoticeState {
        val notices = state.notices.associateBy { it.url }.toMutableMap()
        val events = state.events.associateBy { it.id }.toMutableMap()
        val pending = state.pending.toMutableSet()
        for (notice in incoming) {
            val old = notices[notice.url]
            notices[notice.url] = notice
            if (old?.semanticKey() == notice.semanticKey()) continue
            val process = processes.singleOrNull { it.officialUrl == notice.processUrl } ?: continue
            val type = if (old == null) "NOTICE_PUBLISHED" else "NOTICE_UPDATED"
            // URL is evidence, not event identity: identical content across nodes deduplicates.
            val id = hash(process.id + "|" + type + "|" + notice.semanticKey())
            if (id in events) continue
            val published = notice.publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val eligible = state.initialized && process.id in followed && published != null &&
                published <= now && published >= now.minus(Duration.ofDays(7))
            val event = EventInfo(id, process.id, type, notice.title, "INFO",
                if (published != null) "CONFIRMED" else "UNCONFIRMED", notice.publishedAt,
                now.toString(), eligible,
                EvidenceInfo(notice.url, notice.paragraphs.joinToString("\n"),
                    old?.let { mapOf("text" to it.paragraphs.joinToString("\n"), "title" to it.title) },
                    mapOf("text" to notice.paragraphs.joinToString("\n"), "title" to notice.title)))
            events[id] = event
            if (eligible) pending.add(id)
        }
        for (process in processes) {
            val before = StageDetector.build(state.notices, process, now).associateBy { it.stage.id }
            for (finding in StageDetector.build(notices.values.toList(), process, now)) {
                val stage = finding.stage
                val previous = before[stage.id]?.stage
                fun values(s: StageInfo) = mapOf("start_date" to s.startDate, "end_date" to s.endDate,
                    "status" to s.status, "confidence" to s.confidence)
                if (previous != null && values(previous) == values(stage)) continue
                val type = stage.kind + when {
                    stage.status == "REVIEW_REQUIRED" -> "_REVIEW_REQUIRED"
                    previous != null -> "_DATE_CHANGED"
                    else -> "_ANNOUNCED"
                }
                val predecessor = state.events.firstOrNull { it.processId == process.id &&
                    it.evidence?.new?.get("stage_id") == stage.id }?.id.orEmpty()
                val id = hash(listOf(stage.id, type, previous?.let(::values), values(stage), predecessor).joinToString("|"))
                if (id in events) continue
                val published = Instant.parse(finding.publishedAt)
                val today = now.atZone(java.time.ZoneId.of("America/Bogota")).toLocalDate()
                val eligible = state.initialized && process.id in followed && stage.confidence == "CONFIRMED" &&
                    stage.status == "SCHEDULED" && published <= now && published >= now.minus(Duration.ofDays(7)) &&
                    stage.endDate?.let { java.time.LocalDate.parse(it) >= today } == true
                val event = EventInfo(id, process.id, type, process.name, if (eligible) "CRITICAL" else "INFO",
                    stage.confidence, finding.publishedAt, now.toString(), eligible,
                    EvidenceInfo(stage.officialUrl, finding.excerpt, previous?.let(::values),
                        values(stage) + mapOf("stage_id" to stage.id, "modality" to stage.modality, "population" to stage.population)))
                events[id] = event
                if (eligible) {
                    val coveredUrls = notices.values.filter { notice ->
                        StageDetector.extract(notice, process).any { it.stage.id == stage.id &&
                            it.stage.startDate == stage.startDate && it.stage.endDate == stage.endDate }
                    }.map { it.url }.toSet()
                    pending.removeAll { existing -> events[existing]?.let {
                        it.eventType.startsWith("NOTICE_") && it.evidence?.url in coveredUrls
                    } == true }
                    pending.add(id)
                }
            }
        }
        return NoticeState(notices.values.toList(), events.values.sortedByDescending { it.detectedAt }, pending, true, state.validators)
    }
}
