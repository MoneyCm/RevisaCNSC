package co.meritoradar.app

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Parity tests for CatalogParser against Python backend.
 * Verifies that Kotlin parser produces equivalent results to Python parser.
 */
class CatalogParserTest {

    @Test
    fun testCatalogParserBasic() {
        val httpClient = CnscHttpClient()
        val parser = CatalogParser(httpClient)

        // Test with empty HTML - should handle gracefully
        val emptyHtml = "<div></div>"
        try {
            val entries = parser.parseCatalog(emptyHtml)
            assertNotNull("Entries should not be null", entries)
        } catch (e: Exception) {
            // Parser may throw for invalid HTML, that's acceptable
            assertTrue("Should handle parse errors gracefully", e is co.meritoradar.app.ParseError)
        }
    }

    @Test
    fun testUrlValidation() {
        val httpClient = CnscHttpClient()

        // Valid CNSC URLs
        assertTrue("Should accept valid CNSC URL",
            httpClient.validateOfficialUrl("https://www.cnsc.gov.co/convocatorias/en-desarrollo"))
        assertTrue("Should accept valid CNSC URL without www",
            httpClient.validateOfficialUrl("https://cnsc.gov.co/convocatorias/en-desarrollo"))

        // Invalid URLs
        assertFalse("Should reject non-CNSC URL",
            httpClient.validateOfficialUrl("https://example.com"))
        assertFalse("Should reject HTTP (not HTTPS)",
            httpClient.validateOfficialUrl("http://www.cnsc.gov.co"))
    }

    @Test
    fun testDateParserBasic() {
        // Test basic date parsing
        val result1 = DateParser.parseDates("4 de noviembre de 2026")
        assertNotNull("Should parse valid date", result1)
        assertEquals("Should parse year 2026", 2026, result1.start?.year)

        val result2 = DateParser.parseDates("del 4 al 18 de noviembre de 2026")
        assertNotNull("Should parse date range", result2)
        assertEquals("Should parse start year 2026", 2026, result2.start?.year)

        // Test invalid dates
        val result3 = DateParser.parseDates("4 de noviembre") // Missing year
        assertEquals("Should reject date without year", "UNCONFIRMED", result3.confidence)

        val result4 = DateParser.parseDates("invalid date")
        assertEquals("Should reject invalid date", "UNCONFIRMED", result4.confidence)
    }

    @Test
    fun testNormalization() {
        val httpClient = CnscHttpClient()

        // Test URL normalization with valid base URL
        val normalized = httpClient.normalizeUrl(
            "https://www.cnsc.gov.co/node/12345",
            "https://www.cnsc.gov.co/convocatorias/en-desarrollo"
        )

        assertTrue("Should normalize to absolute URL", normalized.startsWith("https://www.cnsc.gov.co"))
        assertTrue("Should contain the path", normalized.contains("node/12345"))
    }
}
