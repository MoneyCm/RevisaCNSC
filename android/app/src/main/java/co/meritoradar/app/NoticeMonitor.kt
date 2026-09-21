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
        // Bounded revalidation of older followed notices outside the recent window.
        // Reuses stored ETag/Last-Modified; a 200 with changed content still yields
        // a NOTICE_UPDATED event with evidence through NoticeEngine.merge.
        val freshUrls = incoming.map { it.url }.toSet()
        val followedForRevalidation = dao.following().first().toSet()
        val revalidatedAt = state.revalidatedAt?.toMutableMap() ?: mutableMapOf()
        val staleCandidates = NoticeRevalidator.staleCandidates(state, processes, followedForRevalidation, freshUrls)
        for (notice in staleCandidates) {
            val previous = state.notices.find { it.url == notice.url }
            if (previous == null) { revalidatedAt[notice.url] = Instant.now().toString(); continue }
            try {
                val response = fetch(notice.url, validators[notice.url].orEmpty())
                val parsed = if (response.statusCode == 304) previous
                    else if (response.statusCode == 200) parser.parse(response.html, notice.url)
                    else throw ParseError("HTTP " + response.statusCode)
                incoming.add(parsed)
                if (response.statusCode == 200) {
                    validators[notice.url] = buildMap {
                        response.etag?.let { put("If-None-Match", it) }
                        response.lastModified?.let { put("If-Modified-Since", it) }
                    }
                }
            } catch (e: Exception) {
                // A failed revalidation must never destroy stored state.
                android.util.Log.w("CnscMonitoring", "Revalidación de aviso antiguo fallida: " + e.javaClass.simpleName + ": " + e.message)
            } finally {
                revalidatedAt[notice.url] = Instant.now().toString()
            }
        }
        // Bounded rotation of followed microsites; does not create alerts from history.
        val followedForActivity = dao.following().first().toSet()
        val activityCache = dao.activities().first().associateBy { it.cacheKey.removePrefix("activity:") }
        val attempts = activityCache.mapValues { it.value.savedAt }.toMutableMap()
        dao.activityChecks().first().forEach {
            val id = it.cacheKey.removePrefix("activity_check:")
            attempts[id] = maxOf(attempts[id] ?: 0L, it.savedAt)
        }
        ActivityRefresh.run(ActivityRefresh.candidates(processes, followedForActivity, attempts, System.currentTimeMillis()),
            read = { process ->
                val source = process.officialUrl + "?field_tipo_de_contenido_convocat_target_id=64"
                ProcessActivityParser.parseAll(process, source, { fetch(it).html }, Instant.now())
            },
            save = { process, activity ->
                dao.cache(ContentCache("activity:" + process.id, gson.toJson(activity), System.currentTimeMillis()))
            },
            report = { process, error ->
                val check = ActivityCheck(process.id, process.name, Instant.now().toString(), error)
                dao.cache(ContentCache("activity_check:" + process.id, gson.toJson(check), System.currentTimeMillis()))
            })
        val followed = dao.following().first().toSet()
        state = NoticeEngine.merge(state, incoming, processes, followed, Instant.now())
            .copy(validators = validators, revalidatedAt = revalidatedAt)
        // Time-based reminders for confirmed windows, only for followed processes and
        // only when the source stage is present in this run (clean dates were computed
        // from the same notices). Reminders deduplicate per window and never claim a
        // publication: they fire only when the passage of time brings the window near.
        val firedReminders = state.firedReminders.toMutableMap()
        val reminderEvents = mutableListOf<EventInfo>()
        val freshNoticeUrls = incoming.map { it.url }.toSet()
        for (process in processes.filter { it.id in followed }) {
            val stages = StageDetector.build(state.notices, process, Instant.now())
            for (candidate in NoticeReminder.candidates(process, stages, Instant.now())) {
                val key = NoticeReminder.key(process.id, candidate.stage.id, candidate.kind,
                    candidate.stage.startDate!!, candidate.stage.endDate!!)
                if (key in firedReminders) continue
                if (candidate.stage.officialUrl !in freshNoticeUrls) continue
                val id = hash("REMINDER_EVENT|" + key)
                val event = EventInfo(id, process.id,
                    candidate.stage.kind + (if (candidate.kind == NoticeReminder.Kind.CLOSING) "_CLOSING_SOON" else "_OPEN"),
                    candidate.message, "IMPORTANT", "CONFIRMED",
                    null, Instant.now().toString(), true,
                    EvidenceInfo(candidate.stage.officialUrl, candidate.message, null,
                        mapOf("stage_id" to candidate.stage.id, "start_date" to candidate.stage.startDate,
                            "end_date" to candidate.stage.endDate)))
                reminderEvents.add(event)
                firedReminders[key] = id
            }
        }
        if (reminderEvents.isNotEmpty()) {
            state = state.copy(
                events = (reminderEvents + state.events).sortedByDescending { it.detectedAt },
                pending = state.pending + reminderEvents.map { it.id }.toSet(),
                firedReminders = firedReminders)
        }
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
