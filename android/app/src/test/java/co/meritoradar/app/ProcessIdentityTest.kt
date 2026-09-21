package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test

class ProcessIdentityTest {
    private val process = Process("p", "dian-2676", "DIAN 2676", "https://www.cnsc.gov.co/convocatorias/dian-2676")
    @Test fun processNumberIsNotAYear() {
        assertNull(ProcessIdentityParser.namedYear("DIAN 2676"))
        assertEquals(2022, ProcessIdentityParser.namedYear("DIAN 2022"))
    }
    @Test fun separatesCallYearFromRegistrationYear() {
        val html = "<main><h1>DIAN 2676</h1><p>Proceso DIAN 2676 de 2025. Inscripciones en febrero de 2026.</p></main>"
        val identity = ProcessIdentityParser.parse(process, html, process.officialUrl, "2026-09-20T00:00:00Z")
        assertEquals(2025, identity.year)
    }
    @Test fun missingOrConflictingYearIsUnknown() {
        for (text in listOf("Inscripciones 2026", "DIAN 2676 de 2025 y DIAN 2676 de 2026")) {
            assertNull(ProcessIdentityParser.parse(process, "<main><h1>DIAN 2676</h1><p>$text</p></main>",
                process.officialUrl, "2026-09-20T00:00:00Z").year)
        }
    }
    @Test fun wrongProcessHeadingIsRejected() {
        assertThrows(ParseError::class.java) {
            ProcessIdentityParser.parse(process, "<main><h1>DIAN 2022</h1></main>", process.officialUrl, "")
        }
    }
    @Test fun sanitizedOfficialFixtureConfirms2025() {
        val html = javaClass.getResource("/identity/dian-2676.html")!!.readText()
        assertEquals(2025, ProcessIdentityParser.parse(process, html, process.officialUrl, "2026-09-20T00:00:00Z").year)
    }
}
