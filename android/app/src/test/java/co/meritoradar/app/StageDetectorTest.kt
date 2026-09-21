package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class StageDetectorTest {
    private val now = Instant.parse("2026-09-20T15:00:00Z")
    private val process = Process("p", "territorial-12", "Territorial 12", "https://www.cnsc.gov.co/convocatorias/territorial-12")
    private fun notice(text: String = "Inscripciones en modalidad Abierto General del 21 al 28 de septiembre de 2026",
        date: String = "2026-09-19T15:00:00Z", url: String = "https://www.cnsc.gov.co/node/1") =
        LocalNotice(url, process.officialUrl, "Fechas de inscripción", date, listOf(text), emptyList())
    private fun merge(state: NoticeState, vararg notices: LocalNotice) =
        NoticeEngine.merge(state, notices.toList(), listOf(process), setOf("p"), now)

    @Test fun realTerritorialNoticeHasExplicitPaymentAndRegistration() {
        val http = CnscHttpClient()
        try {
            val parsed = NoticeParser(http).parse(javaClass.getResource("/notices/registration.html")!!.readText(),
                "https://www.cnsc.gov.co/node/65132")
            val stages = StageDetector.extract(parsed, process)
            assertEquals(setOf("REGISTRATION", "PAYMENT"), stages.map { it.stage.kind }.toSet())
            assertTrue(stages.all { it.stage.startDate == "2026-05-25" && it.stage.endDate == "2026-06-12" &&
                it.stage.confidence == "CONFIRMED" })
        } finally { http.close() }
    }
    @Test fun missingYearInvalidDateAndMultipleWindowsAreRejected() {
        for (text in listOf("del 21 al 28 de septiembre", "del 30 al 31 de febrero de 2026",
            "del 21 al 28 de septiembre de 2026 y el 30 de septiembre de 2026")) {
            assertEquals(null to null, StageDetector.window(text))
        }
    }
    @Test fun ambiguousScopeNeverCreatesCriticalAlert() {
        val n = notice("Inscripciones Abierto y Ascenso General del 21 al 28 de septiembre de 2026")
        assertEquals("UNCONFIRMED", StageDetector.extract(n, process).single().stage.confidence)
        assertTrue(merge(NoticeState(initialized = true), n).events.none { it.priority == "CRITICAL" })
    }
    @Test fun negationAndPostponementAreNotOpenWindows() {
        assertTrue(StageDetector.extract(notice("No se abrirán inscripciones Abierto General del 21 al 28 de septiembre de 2026"), process).isEmpty())
        val postponed = notice(date = "2026-09-20T10:00:00Z", url = "https://www.cnsc.gov.co/node/2")
            .copy(title = "Aplazamiento de inscripciones", paragraphs = listOf("Se informará un nuevo calendario."))
        val stage = StageDetector.build(listOf(notice(), postponed), process, now).single().stage
        assertEquals("REVIEW_REQUIRED", stage.status)
    }
    @Test fun sameTimestampConflictingSourcesRequireReview() {
        val second = notice("Inscripciones Abierto General del 22 al 29 de septiembre de 2026", url = "https://www.cnsc.gov.co/node/2")
        val stage = StageDetector.build(listOf(notice(), second), process, now).single().stage
        assertEquals("UNCONFIRMED", stage.confidence)
        assertNull(stage.startDate)
    }
    @Test fun baselineAndRepeatedIngestionAreSilent() {
        val state = merge(NoticeState(), notice())
        assertTrue(state.pending.isEmpty())
        assertEquals(state, merge(state, notice()))
    }
    @Test fun dateChangeKeepsOldAndNewAndDeduplicates() {
        val state = merge(NoticeState(), notice())
        val changed = notice("Inscripciones Abierto General del 21 al 30 de septiembre de 2026",
            date = "2026-09-20T10:00:00Z")
        val result = merge(state, changed)
        val event = result.events.single { it.eventType == "REGISTRATION_DATE_CHANGED" }
        assertEquals("2026-09-28", event.evidence!!.old!!["end_date"])
        assertEquals("2026-09-30", event.evidence!!.new!!["end_date"])
        assertEquals("CRITICAL", event.priority)
        assertEquals(setOf(event.id), result.pending)
        assertEquals(result, merge(result, changed))
    }
    @Test fun duplicateSourceDoesNotRepeatStageAnnouncement() {
        val state = merge(NoticeState(initialized = true), notice(), notice(url = "https://www.cnsc.gov.co/node/2"))
        assertEquals(1, state.events.count { it.eventType == "REGISTRATION_ANNOUNCED" })
        assertEquals(1, state.pending.size)
    }
    @Test fun expiredDatesDoNotNotifyAsCurrent() {
        val result = merge(NoticeState(initialized = true),
            notice("Inscripciones Abierto General del 1 al 10 de septiembre de 2026"))
        assertTrue(result.events.filter { it.eventType.startsWith("REGISTRATION") }.none { it.notifyEligible })
    }
}
