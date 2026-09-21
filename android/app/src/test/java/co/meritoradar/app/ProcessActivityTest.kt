package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProcessActivityTest {
    private val process = Process("p", name = "DIAN 2676", officialUrl = "https://www.cnsc.gov.co/convocatorias/dian-2676")
    private val url = process.officialUrl + "?field_tipo_de_contenido_convocat_target_id=64"
    private val now = Instant.parse("2026-09-21T12:00:00Z")
    private fun card(title: String, date: String) =
        "<div class='card'><h5><button>" + title + "</button></h5><span class='views-field-created'>" + date + "</span></div>"
    private fun page(cards: String) = "<main><h1>DIAN 2676</h1><div class='view-content'>" + cards + "</div></main>"

    @Test fun latestOfficialDisabilityNoticeIsNotGeneralVrm() {
        // Sanitized heading/date observed on official DIAN microsite, 2026-09-20.
        val title = "LISTADO DE RESPUESTAS A RECLAMACIONES FRENTE A LOS RESULTADOS DE LA VERIFICACIÓN DEL CERTIFICADO DE DISCAPACIDAD."
        val result = ProcessActivityParser.parse(process, page(
            card(title, "Lun, 01/06/2026 - 12:42") +
            card("Ampliación cierre de inscripciones DIAN 2676", "06/02/2026 - 16:18")
        ), url, now)
        assertEquals(title, result.title)
        assertEquals("Reclamaciones sobre certificado de discapacidad", result.summary)
        assertEquals("2026-06-01T17:42:00Z", result.publishedAt)
        assertEquals(url, result.sourceUrl)
        assertEquals(2, result.noticesChecked)
    }

    @Test fun distinguishesVrmMilestonesWithoutClaimingCurrentStage() {
        assertEquals("Inicio de verificación de requisitos mínimos anunciado",
            ProcessActivityParser.describe("Inicio de Verificación de Requisitos Mínimos"))
        assertEquals("Resultados de verificación de requisitos mínimos anunciados",
            ProcessActivityParser.describe("Resultados de VRM"))
        assertEquals("Reclamaciones de verificación de requisitos mínimos",
            ProcessActivityParser.describe("Respuestas a reclamaciones de VRM"))
        assertEquals("Aviso sobre VRM; consultar alcance oficial",
            ProcessActivityParser.describe("Aplazamiento del inicio de VRM"))
        assertEquals("Aviso sobre verificación de requisitos mínimos",
            ProcessActivityParser.describe("Documentos para verificación de requisitos mínimos"))
    }

    @Test fun ignoresFutureDatesAndRejectsImpossibleDates() {
        val result = ProcessActivityParser.parse(process, page(
            card("Futuro", "01/01/2027 - 12:00") +
            card("Imposible", "31/02/2026 - 12:00") +
            card("Resultados de VRM", "20/09/2026 - 12:00")
        ), url, now)
        assertEquals("Resultados de VRM", result.title)
        assertEquals(1, result.noticesChecked)
    }

    @Test(expected = ParseError::class) fun wrongContestIsRejected() {
        ProcessActivityParser.parse(process, page(card("Aviso", "20/09/2026 - 12:00"))
            .replace("<h1>DIAN 2676</h1>", "<h1>DIAN 2022</h1>"), url, now)
    }

    @Test(expected = ParseError::class) fun unknownStructureIsNotEmptySuccess() {
        ProcessActivityParser.parse(process, page("<p>Sin estructura reconocida</p>"), url, now)
    }

    @Test(expected = IllegalArgumentException::class) fun nonNoticeCategoryIsRejected() {
        ProcessActivityParser.parse(process, page(card("Aviso", "20/09/2026 - 12:00")),
            url.replace("=64", "=65"), now)
    }
}
