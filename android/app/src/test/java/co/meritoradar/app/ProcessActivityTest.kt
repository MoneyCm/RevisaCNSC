package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
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

    @Test fun keepsOrderedHistoryWithSourceUrlAndNoRetroactiveAlerts() {
        val result = ProcessActivityParser.parse(process, page(
            card("Ampliación cierre de inscripciones DIAN 2676", "06/02/2026 - 16:18") +
            card("LISTADO DE RESPUESTAS A RECLAMACIONES", "01/06/2026 - 12:42") +
            card("Recuerdo. Cierre de inscripciones", "04/02/2026 - 18:31")
        ), url, now)
        assertEquals(3, result.noticesChecked)
        assertEquals("LISTADO DE RESPUESTAS A RECLAMACIONES", result.title) // newer first
        val ordered = result.publications.orEmpty().map { it.publishedAt }
        assertEquals(listOf("2026-06-01T17:42:00Z", "2026-02-06T21:18:00Z", "2026-02-04T23:31:00Z"), ordered)
        assertTrue(result.publications.orEmpty().all { it.sourceUrl == url })
        assertEquals("Aviso oficial del concurso", result.publications?.last()?.summary)
    }

    @Test fun nextPageRequiresSameMicrositeAndAvisoCategory() {
        val next = "<main><h1>DIAN 2676</h1><nav class='pager'><ul>" +
            "<li class='pager__item--next'><a href='" + url + "&page=1'>Siguiente</a></li></ul></nav></main>"
        assertEquals(url + "&page=1", ProcessActivityParser.nextPage(process, next, url))
        val foreign = next.replace(url + "&page=1", "https://www.cnsc.gov.co/convocatorias/otro")
        assertNull(ProcessActivityParser.nextPage(process, foreign, url))
        val otherCategory = next.replace("&page=1", "&page=1#aviso").replace(url, url.replace("=64", "=65"))
        assertNull(ProcessActivityParser.nextPage(process, otherCategory, url))
        assertNull(ProcessActivityParser.nextPage(process, "<main><h1>DIAN 2676</h1></main>", url))
    }

    @Test fun parseAllFollowsBoundedPagesAndMergesHistory() = runBlocking {
        val page1 = "<main><h1>DIAN 2676</h1><div class='view-content'>" +
            card("Nuevo aviso reciente", "20/09/2026 - 12:00") +
            "</div><nav class='pager'><ul><li class='pager__item--next'><a href='" + url + "&page=1'>Siguiente</a></li></ul></nav></main>"
        val page2 = page(card("Resultados de VRM", "20/09/2025 - 12:00"))
        val requests = mutableListOf<String>()
        val result = ProcessActivityParser.parseAll(process, url,
            { u -> requests.add(u); if (u.endsWith("&page=1")) page2 else page1 }, now)
        assertEquals(listOf(url, url + "&page=1"), requests)
        assertEquals(2, result.noticesChecked)
        assertEquals("Nuevo aviso reciente", result.title)
        assertEquals("Resultados de VRM", result.publications?.last()?.title)
    }

    @Test fun parseAllBoundsPagesAndToleratesLatePageFailure() = runBlocking {
        var calls = 0
        val page1 = "<main><h1>DIAN 2676</h1><div class='view-content'>" +
            card("Aviso", "20/09/2026 - 12:00") +
            "</div><nav class='pager'><ul><li class='pager__item--next'><a href='" + url + "&page=1'>Siguiente</a></li></ul></nav></main>"
        val result = ProcessActivityParser.parseAll(process, url,
            {
                calls++
                when (calls) {
                    1 -> page1
                    2 -> throw RuntimeException("caída de página")
                    else -> throw IllegalStateException("no debe seguir")
                }
            }, now, maxPages = 2)
        assertEquals(2, calls)
        assertEquals("Aviso", result.title)
        assertEquals(1, result.noticesChecked)
    }

    @Test(expected = ParseError::class) fun unknownStructureIsNotEmptySuccess() {
        ProcessActivityParser.parse(process, page("<p>Sin estructura reconocida</p>"), url, now)
    }

    @Test(expected = IllegalArgumentException::class) fun nonNoticeCategoryIsRejected() {
        ProcessActivityParser.parse(process, page(card("Aviso", "20/09/2026 - 12:00")),
            url.replace("=64", "=65"), now)
    }
}
