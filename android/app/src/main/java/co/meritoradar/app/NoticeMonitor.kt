package co.meritoradar.app

import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.Duration

class NoticeMonitor(private val database: LegacyRadarDatabase, private val http: CnscHttpClient,
    private val notifier: LocalNotifier) {
    private val dao = database.dao()
    private val gson = Gson()
    private val parser = NoticeParser(http)
    private var lastRequest = 0L

    private suspend fun fetch(url: String, conditional: Map<String, String> = emptyMap()): CnscHttpClient.FetchResult {
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastRequest
        if (elapsed < 2000) delay(2000 - elapsed)
        lastRequest = android.os.SystemClock.elapsedRealtime()
        return http.fetch(url, conditional)
    }

    suspend fun run() {
        val saved = dao.cached("notice_state").first()
        var state = saved?.let { gson.fromJson(it.payload, NoticeState::class.java) } ?: NoticeState()
        delay(2000) // Keep separation from the catalogue request in the same worker.
        val validators = state.validators.toMutableMap()
        val known = state.notices.map { it.url }.toSet()
        val incoming = mutableListOf<LocalNotice>()
        var next: String? = CnscSources.NOTICES
        var reachedBoundary = !state.initialized
        var pages = 0
        // First load establishes a recent-page baseline. Later runs traverse back to known coverage.
        while (next != null && pages < 3) {
            val (links, followingPage) = parser.index(fetch(next).html, next)
            for (link in links) {
                val response = fetch(link, validators[link].orEmpty())
                val parsed = if (response.statusCode == 304) state.notices.find { it.url == link }
                    ?: throw ParseError("Respuesta condicional sin aviso guardado")
                else parser.parse(response.html, link)
                incoming.add(parsed)
                if (response.statusCode == 200) {
                    validators[link] = buildMap {
                        response.etag?.let { put("If-None-Match", it) }
                        response.lastModified?.let { put("If-Modified-Since", it) }
                    }
                }
            }
            pages++
            if (!state.initialized || links.any { it in known } || followingPage == null) {
                reachedBoundary = true
                break
            }
            next = followingPage
        }
        if (!reachedBoundary) throw ParseError("Cobertura de avisos incompleta: más de tres páginas nuevas")
        val processes = dao.observe().first()
        // Bounded rotation of followed microsites; does not create alerts from history.
        val followedForActivity = dao.following().first().toSet()
        val activityCache = dao.activities().first().associateBy { it.cacheKey.removePrefix("activity:") }
        for (process in processes.filter { it.id in followedForActivity }
            .filter { System.currentTimeMillis() - (activityCache[it.id]?.savedAt ?: 0L) > 1_800_000 }
            .sortedBy { activityCache[it.id]?.savedAt ?: 0L }.take(3)) {
            val source = process.officialUrl + "?field_tipo_de_contenido_convocat_target_id=64"
            val response = fetch(source)
            val activity = ProcessActivityParser.parse(process, response.html, source, Instant.now())
            dao.cache(ContentCache("activity:" + process.id, gson.toJson(activity), System.currentTimeMillis()))
        }
        state = NoticeEngine.merge(state, incoming, processes, dao.following().first().toSet(), Instant.now()).copy(validators = validators)
        database.withTransaction {
            dao.cache(ContentCache("notice_state", gson.toJson(state), System.currentTimeMillis()))
            dao.cache(ContentCache("alerts", gson.toJson(AlertContent(state.events)), System.currentTimeMillis()))
            for (process in processes) {
                val publications = state.notices.filter { it.processUrl == process.officialUrl }.map {
                    PublicationInfo(hash(it.url), it.title, if (it.publishedAt != null) "CONFIRMED" else "UNCONFIRMED",
                        it.publishedAt, it.url, it.paragraphs, it.documents)
                }.sortedByDescending { it.publishedAt }
                val stages = StageDetector.build(state.notices, process, Instant.now()).map { it.stage }
                val detail = DetailContent(stages, state.events.filter { it.processId == process.id }, publications)
                dao.cache(ContentCache("detail:" + process.id, gson.toJson(detail), System.currentTimeMillis()))
            }
        }
        // Durable outbox: same event ID replaces a notification after an interrupted delivery.
        val pending = state.pending.toMutableSet()
        val followed = dao.following().first().toSet()
        for (id in state.pending) {
            val event = state.events.find { it.id == id }
            if (event == null || event.processId !in followed ||
                Instant.parse(event.detectedAt) < Instant.now().minus(Duration.ofDays(7))) {
                pending.remove(id)
                continue
            }
            if (!notifier.hasNotificationPermission()) continue
            val stageId = event.evidence?.new?.get("stage_id")
            if (stageId != null) {
                val process = processes.find { it.id == event.processId }
                val current = process?.let { StageDetector.build(state.notices, it, Instant.now()) }
                    ?.find { it.stage.id == stageId }?.stage
                if (current == null || current.status != "SCHEDULED" || current.confidence != "CONFIRMED" ||
                    current.startDate != event.evidence?.new?.get("start_date") ||
                    current.endDate != event.evidence?.new?.get("end_date") ||
                    current.endDate?.let { java.time.LocalDate.parse(it) < java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")) } != false) {
                    pending.remove(id)
                    continue
                }
                // Never deliver a queued critical alert from a source not rechecked in this run.
                if (incoming.none { it.url == current.officialUrl }) continue
            }
            // Only a delivered event leaves the outbox: a blocked channel or a refused post
            // must keep the event queued instead of appearing as delivered.
            val delivered = notifier.showEventNotification(event.id, event.processId, "", event.eventType,
                eventLabel(event.eventType),
                event.title, event.priority)
            if (!delivered) continue
            pending.remove(id)
            dao.cache(ContentCache("notice_state", gson.toJson(state.copy(pending = pending.toSet())), System.currentTimeMillis()))
        }
        dao.cache(ContentCache("notice_state", gson.toJson(state.copy(pending = pending.toSet())), System.currentTimeMillis()))
    }
}
