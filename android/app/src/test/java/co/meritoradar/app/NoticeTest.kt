package co.meritoradar.app
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class NoticeTest {
    private val now = Instant.parse("2026-09-20T15:00:00Z")
    private val process = Process("p", "territorial-12", "Territorial 12",
        "https://www.cnsc.gov.co/convocatorias/territorial-12")
    private fun notice() = LocalNotice("https://www.cnsc.gov.co/node/1", process.officialUrl,
        "Aviso oficial", "2026-09-19T15:00:00Z", listOf("Texto oficial"), emptyList())
    private fun merge(state: NoticeState, vararg notices: LocalNotice) =
        NoticeEngine.merge(state, notices.toList(), listOf(process), setOf("p"), now)

    @Test fun baselineIsSilentAndRepetitionIsIdempotent() {
        val baseline = merge(NoticeState(), notice())
        assertEquals(1, baseline.events.size)
        assertTrue(baseline.pending.isEmpty())
        assertFalse(baseline.events.single().notifyEligible)
        assertEquals(baseline, merge(baseline, notice()))
    }
    @Test fun newFollowedNoticeEnqueuesOnceAcrossSources() {
        val state = merge(NoticeState(initialized = true), notice(), notice().copy(url = "https://www.cnsc.gov.co/node/2"))
        assertEquals(1, state.events.size)
        assertEquals(1, state.pending.size)
        assertEquals(2, state.notices.size)
        assertEquals(state, merge(state, notice()))
    }
    @Test fun changedContentPreservesEvidence() {
        val baseline = merge(NoticeState(), notice())
        val changed = merge(baseline, notice().copy(paragraphs = listOf("Nueva fecha oficial")))
        val event = changed.events.first { it.eventType == "NOTICE_UPDATED" }
        assertEquals("Texto oficial", event.evidence!!.old!!["text"])
        assertEquals("Nueva fecha oficial", event.evidence!!.new!!["text"])
        assertEquals(1, changed.pending.size)
    }
    @Test fun oldFutureUndatedAndUnassignedDoNotNotify() {
        for (n in listOf(notice().copy(publishedAt = "2025-01-01T00:00:00Z"),
            notice().copy(publishedAt = "2027-01-01T00:00:00Z"),
            notice().copy(publishedAt = null), notice().copy(processUrl = null))) {
            assertTrue(merge(NoticeState(initialized = true), n).pending.isEmpty())
        }
        assertTrue(NoticeEngine.merge(NoticeState(initialized = true), listOf(notice()),
            listOf(process), emptySet(), now).pending.isEmpty())
    }
    @Test fun realFixturesAndCosmeticChange() {
        val http = CnscHttpClient()
        try {
            val parser = NoticeParser(http)
            val html = javaClass.getResource("/notices/registration.html")!!.readText()
            val parsed = parser.parse(html, "https://www.cnsc.gov.co/node/65132")
            assertEquals(process.officialUrl, parsed.processUrl)
            assertEquals("2026-05-25T20:19:00Z", parsed.publishedAt)
            assertTrue(parsed.paragraphs.any { it.contains("12") && it.contains("junio") })
            val cosmetic = parser.parse(html.replace("<main", "<main style='color:red'"), parsed.url)
            assertEquals(parsed.semanticKey(), cosmetic.semanticKey())
            val index = parser.index(javaClass.getResource("/notices/notices.html")!!.readText(), CnscSources.NOTICES)
            assertTrue(index.first.isNotEmpty())
            assertNotNull(index.second)
        } finally { http.close() }
    }
    @Test fun malformedArticleAndEmptyIndexFail() {
        val http = CnscHttpClient()
        try {
            val parser = NoticeParser(http)
            assertThrows(ParseError::class.java) { parser.index("<main><h1>Avisos informativos</h1></main>", CnscSources.NOTICES) }
            val html = javaClass.getResource("/notices/registration.html")!!.readText()
            assertThrows(ParseError::class.java) { parser.parse(html, "https://www.cnsc.gov.co/node/999") }
        } finally { http.close() }
    }
    @Test fun staleRevalidationKeepsLoadedOnesAndLimitsCandidates() {
        val older = notice().copy(url = "https://www.cnsc.gov.co/node/10",
            publishedAt = "2026-09-01T15:00:00Z")
        val fresh = notice().copy(url = "https://www.cnsc.gov.co/node/11")
        val otherProcess = Process("q", "dian-2676", "DIAN 2676",
            "https://www.cnsc.gov.co/convocatorias/dian-2676")
        val followedNotice = older.copy(url = "https://www.cnsc.gov.co/node/12",
            processUrl = otherProcess.officialUrl)
        val state = NoticeState(initialized = true, notices = listOf(older, fresh, older.copy(url="https://www.cnsc.gov.co/node/13"),
            older.copy(url="https://www.cnsc.gov.co/node/14"), older.copy(url="https://www.cnsc.gov.co/node/15"), followedNotice),
            revalidatedAt = mapOf())
        // Following only DIAN (q): territorial-12 notices are not revalidated, and the
        // just-loaded fresh notice is excluded even though it belongs to a followed process.
        val candidates = NoticeRevalidator.staleCandidates(state, listOf(process, otherProcess), setOf("q"), setOf("https://www.cnsc.gov.co/node/11"))
        assertEquals(listOf("https://www.cnsc.gov.co/node/12"), candidates.map { it.url })
    }
    @Test fun staleRevalidationRotatesOldestRevalidatedFirstAndBounded() {
        val processId = process.id
        val notices = (1..10).map { notice().copy(url = "https://www.cnsc.gov.co/node/$it") }
        val revalidatedAt = notices.withIndex().associate { (i, n) -> n.url to "2026-09-${(i % 5) + 1}T12:00:00Z" }
        val state = NoticeState(initialized = true, notices = notices,
            revalidatedAt = revalidatedAt)
        val candidates = NoticeRevalidator.staleCandidates(state, listOf(process), setOf(processId), emptySet())
        assertEquals(NoticeRevalidator.MAX_STALE_PER_RUN, candidates.size)
        val expected = notices.sortedBy { revalidatedAt[it.url] }.take(NoticeRevalidator.MAX_STALE_PER_RUN).map { it.url }
        assertEquals(expected, candidates.map { it.url })
    }
    @Test fun revalidatedOldNoticeUpdateStillDeduplicatesAndPreservesState() {
        val baseline = merge(NoticeState(initialized = true),
            notice().copy(url = "https://www.cnsc.gov.co/node/20", publishedAt = "2026-09-01T15:00:00Z"))
        // Revalidation returns the same published notice unchanged: no event, no pending, ids stable.
        val same = NoticeEngine.merge(baseline, listOf(notice().copy(url = "https://www.cnsc.gov.co/node/20",
            publishedAt = "2026-09-01T15:00:00Z")), listOf(process), setOf("p"), now)
        assertEquals(baseline.events, same.events)
        assertTrue(same.pending.isEmpty())
        // A real change to an old followed notice surfaces as NOTICE_UPDATED with evidence,
        // not as a new publication, and never fabricates eligibility for a past window.
        val changed = NoticeEngine.merge(baseline, listOf(notice().copy(url = "https://www.cnsc.gov.co/node/20",
            publishedAt = "2026-09-01T15:00:00Z", paragraphs = listOf("Aviso corregido"))), listOf(process), setOf("p"), now)
        val upd = changed.events.first { it.eventType == "NOTICE_UPDATED" }
        assertEquals("Texto oficial", upd.evidence?.old?.get("text"))
        assertEquals("Aviso corregido", upd.evidence?.new?.get("text"))
        assertFalse(upd.notifyEligible)
        assertEquals(changed, NoticeEngine.merge(changed, listOf(notice().copy(url = "https://www.cnsc.gov.co/node/20",
            publishedAt = "2026-09-01T15:00:00Z", paragraphs = listOf("Aviso corregido"))), listOf(process), setOf("p"), now))
    }
}
