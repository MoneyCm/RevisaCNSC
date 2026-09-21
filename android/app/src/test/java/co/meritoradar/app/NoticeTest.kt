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
}
