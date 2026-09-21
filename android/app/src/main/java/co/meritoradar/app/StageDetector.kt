package co.meritoradar.app

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

data class StageEvidence(val stage: StageInfo, val publishedAt: String, val excerpt: String)

/** Explicit windows only. Publication is not proof that a stage is currently open. */
object StageDetector {
    private fun fold(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()
    private const val months = "enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre"
    private val full = Regex("\\b\\d{1,2} de ($months) de \\d{4}\\b|\\b\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{4}\\b")
    private val compact = Regex("(?:del|entre el) \\d{1,2} (?:al|y el) \\d{1,2} de ($months) de \\d{4}")
    private val uncertain = Regex("\\bno\\b|posible|podria|proyecto|sujeto a|tentativ|aplaz|suspend")
    fun window(raw: String): Pair<LocalDate?, LocalDate?> {
        val text = fold(raw).replace(Regex("\\bdel (\\d{4})\\b"), "de $1")
        val ranges = compact.findAll(text).toList()
        if (ranges.size == 1 && full.findAll(text.removeRange(ranges.single().range)).none()) {
            val parsed = DateParser.parseDates(ranges.single().value)
            return parsed.start to parsed.end
        }
        if (ranges.isNotEmpty()) return null to null
        val dates = full.findAll(text).toList()
        if (dates.size != 2) return null to null
        if (!Regex("\\bhasta\\b|\\bal\\b|\\by el\\b").containsMatchIn(text.substring(dates[0].range.last + 1, dates[1].range.first)))
            return null to null
        val start = DateParser.parseDates(dates[0].value).start
        val end = DateParser.parseDates(dates[1].value).start
        return if (start != null && end != null && start <= end) start to end else null to null
    }
    private fun scope(raw: String): Pair<String, String> {
        val t = fold(raw)
        val open = Regex("\\babierto\\b").containsMatchIn(t)
        val promotion = Regex("\\bascenso\\b").containsMatchIn(t)
        val mode = if (open == promotion) "UNKNOWN" else if (open) "OPEN" else "PROMOTION"
        val general = Regex("\\bgeneral\\b|sin reserva").containsMatchIn(t)
        val reserved = "reserva" in t && "discapacidad" in t && "sin reserva" !in t
        if ("con reserva" in t && !reserved) return mode to "UNKNOWN"
        return mode to if (general == reserved) "UNKNOWN" else if (general) "GENERAL" else "DISABILITY_RESERVED"
    }
    private fun kinds(t: String): List<String> = buildList {
        if ("inscripcion" in t) add("REGISTRATION")
        if (Regex("recaudo|derechos de participacion|venta de derechos").containsMatchIn(t)) add("PAYMENT")
    }
    fun extract(notice: LocalNotice, process: Process): List<StageEvidence> {
        val published = notice.publishedAt ?: return emptyList()
        if (notice.processUrl != process.officialUrl || uncertain.containsMatchIn(fold(notice.title))) return emptyList()
        val fallback = scope(notice.title)
        return notice.paragraphs.flatMap { paragraph ->
            val text = fold(paragraph)
            if (uncertain.containsMatchIn(text)) return@flatMap emptyList()
            val (start, end) = window(text)
            if (start == null || end == null) return@flatMap emptyList()
            val local = scope(text)
            val mode = if (local.first == "UNKNOWN" && !Regex("abierto|ascenso").containsMatchIn(text)) fallback.first else local.first
            val population = if (local.second == "UNKNOWN" && !Regex("general|reserva|discapacidad").containsMatchIn(text)) fallback.second else local.second
            kinds(text).map { kind ->
                StageEvidence(StageInfo(hash(listOf(process.id, kind, mode, population).joinToString("|")),
                    kind, mode, population, start.toString(), end.toString(), "SCHEDULED",
                    if (mode != "UNKNOWN" && population != "UNKNOWN") "CONFIRMED" else "UNCONFIRMED", notice.url), published, paragraph)
            }
        }.groupBy { it.stage.id }.values.map { group ->
            val first = group.first()
            if (group.map { it.stage.startDate to it.stage.endDate }.distinct().size == 1) first
            else first.copy(stage = first.stage.copy(startDate = null, endDate = null, confidence = "UNCONFIRMED", status = "REVIEW_REQUIRED"),
                excerpt = group.joinToString("\n") { it.excerpt })
        }
    }
    fun build(notices: List<LocalNotice>, process: Process, now: Instant): List<StageEvidence> {
        val stages = linkedMapOf<String, StageEvidence>()
        notices.filter { it.processUrl == process.officialUrl && it.publishedAt != null &&
            runCatching { Instant.parse(it.publishedAt) <= now }.getOrDefault(false) }
            .sortedWith(compareBy<LocalNotice> { it.publishedAt }.thenBy { it.url }).forEach { notice ->
                val title = fold(notice.title)
                val suspended = Regex("suspension (del|de los) proceso|se suspende el proceso").containsMatchIn(title)
                val postponed = Regex("aplazamiento|se aplaza").containsMatchIn(title)
                if ((suspended || postponed) && !Regex("\\bno\\b|posible|proyecto").containsMatchIn(title)) {
                    val affected = if (suspended) listOf("REGISTRATION", "PAYMENT") else kinds(title)
                    stages.replaceAll { _, current ->
                        if (current.stage.kind in affected) current.copy(stage = current.stage.copy(status = "REVIEW_REQUIRED",
                            officialUrl = notice.url), publishedAt = notice.publishedAt!!, excerpt = notice.title) else current
                    }
                }
                for (candidate in extract(notice, process)) {
                    if (candidate.stage.modality == "UNKNOWN" || candidate.stage.population == "UNKNOWN") {
                        stages.replaceAll { _, current ->
                            if (current.stage.kind == candidate.stage.kind) current.copy(stage = current.stage.copy(
                                status = "REVIEW_REQUIRED", confidence = "UNCONFIRMED"), publishedAt = candidate.publishedAt,
                                excerpt = candidate.excerpt) else current
                        }
                    }
                    val previous = stages[candidate.stage.id]
                    val conflict = previous != null && previous.publishedAt == candidate.publishedAt &&
                        (previous.stage.startDate != candidate.stage.startDate || previous.stage.endDate != candidate.stage.endDate ||
                            previous.stage.status == "REVIEW_REQUIRED")
                    stages[candidate.stage.id] = if (conflict) candidate.copy(stage = candidate.stage.copy(startDate = null,
                        endDate = null, confidence = "UNCONFIRMED", status = "REVIEW_REQUIRED")) else candidate
                }
            }
        return stages.values.toList()
    }
}
