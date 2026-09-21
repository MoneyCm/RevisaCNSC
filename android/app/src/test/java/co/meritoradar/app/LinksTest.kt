package co.meritoradar.app
import org.junit.Assert.*
import org.junit.Test
class LinksTest {
    @Test fun onlyOfficialHttps() {
        assertTrue(officialLink("https://www.cnsc.gov.co/convocatorias/territorial-12"))
        assertFalse(officialLink("https://cnsc.gov.co.evil.test"))
        assertFalse(officialLink("http://www.cnsc.gov.co"))
        assertFalse(officialLink("https://user@cnsc.gov.co"))
    }
    @Test fun deepLinkTargetsExactId() {
        val id = "12345678-1234-1234-1234-123456789012"
        assertEquals(id, processLink("meritoradar://process/$id"))
        assertNull(processLink("https://evil.test/$id"))
        assertNull(processLink("meritoradar://process/invalid"))
    }
}
