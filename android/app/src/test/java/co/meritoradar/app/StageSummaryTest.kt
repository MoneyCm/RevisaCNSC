package co.meritoradar.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StageSummaryTest {
    private fun stage(kind: String, confidence: String = "CONFIRMED", status: String = "SCHEDULED") =
        StageInfo(kind, kind, "OPEN", "GENERAL", "2026-09-21", "2026-09-28", status, confidence, "https://www.cnsc.gov.co/node/1")

    @Test fun emptyStagesAreExplicitlyUnconfirmed() {
        assertEquals("Sin etapa confirmada", stageSummary(emptyList()))
    }

    @Test fun confirmedStagesShowTheirKinds() {
        assertEquals("Inscripciones próximas desde 21 sep. 2026", stageSummary(listOf(stage("REGISTRATION"), stage("PAYMENT")), LocalDate.of(2026, 9, 20)))
    }

    @Test fun reviewTakesPrecedenceOverPublishedLabel() {
        assertEquals("Inscripciones aplazadas o en revisión", stageSummary(listOf(stage("REGISTRATION", status = "REVIEW_REQUIRED"))))
    }

    @Test fun registrationStatusUsesPublishedWindow() {
        val registration = stage("REGISTRATION")
        assertEquals("Inscripciones abiertas hasta 28 sep. 2026", stageSummary(listOf(registration), LocalDate.of(2026, 9, 24)))
        assertEquals("Inscripciones cerradas", stageSummary(listOf(registration), LocalDate.of(2026, 10, 1)))
    }

    @Test fun officialActivityFillsSummaryWhenNoDateWindowExists() {
        val event = EventInfo("e", "p", "RESULT_PUBLISHED", "Resultados", "INFO", "CONFIRMED", null, "2026-09-20T00:00:00Z", false, null)
        assertEquals("Actividad oficial: Resultados publicados", officialActivitySummary(listOf(event)))
    }

    @Test fun noticeTitleProvidesActivityWhenClassifierIsGeneric() {
        val event = EventInfo("e", "p", "NOTICE_PUBLISHED", "Publicación de algunas listas de elegibles", "INFO", "CONFIRMED", null, "2026-09-20T00:00:00Z", false, null)
        assertEquals("Actividad oficial: Lista de elegibles anunciada", officialActivitySummary(listOf(event)))
    }
}