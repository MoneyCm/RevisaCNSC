package co.meritoradar.app

import org.junit.Assert.*
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class CnscNetworkRegressionTest {
    @Test fun relativeCatalogLinksAreCanonicalAndDeduplicated() {
        val client = CnscHttpClient()
        try {
            val entries = CatalogParser(client).parseCatalog(
                "<main><h1>En desarrollo</h1><div class='views-field-name'>" +
                "<a href='/convocatorias/territorial-12?field_tipo=1'>Territorial 12</a>" +
                "<a href='https://www.cnsc.gov.co/convocatorias/territorial-12/#avisos'>Territorial 12</a>" +
                "</div></main>")
            assertEquals(1, entries.size)
            assertEquals("https://www.cnsc.gov.co/convocatorias/territorial-12", entries.single().officialUrl)
            assertEquals("territorial-12", entries.single().slug)
        } finally { client.close() }
    }

    @Test fun unsafeUrlsAreRejected() {
        val client = CnscHttpClient()
        try {
            for (url in listOf("http://www.cnsc.gov.co/a", "https://www.cnsc.gov.co.evil.test/a",
                "https://user@www.cnsc.gov.co/a", "https://www.cnsc.gov.co:8443/a")) {
                assertFalse(client.validateOfficialUrl(url))
                assertThrows(IllegalArgumentException::class.java) { client.normalizeUrl(url) }
            }
        } finally { client.close() }
    }

    @Test fun selfSignedCertificateIsNotTrusted() {
        val certificate = javaClass.getResourceAsStream("/untrusted.pem")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        assertThrows(CertificateException::class.java) {
            CnscTrustManager().checkServerTrusted(arrayOf(certificate), "RSA")
        }
    }

    @Test fun intermediateIsNotAddedAsTrustAnchor() {
        val certificate = javaClass.getResourceAsStream("/geotrust_tls_rsa_ca_g1.pem")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        assertFalse(CnscTrustManager().acceptedIssuers.contains(certificate))
    }
}
