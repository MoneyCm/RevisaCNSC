package co.meritoradar.app
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Adds a missing intermediate, not a trust anchor. Android validates the completed
 * chain against its own roots. OkHttp still verifies the hostname. */
internal class CnscTrustManager : X509TrustManager {
    private val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(null as KeyStore?)
    }.trustManagers.filterIsInstance<X509TrustManager>().single()
    private val intermediate = requireNotNull(javaClass.getResourceAsStream("/geotrust_tls_rsa_ca_g1.pem")).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val completed = if (chain.lastOrNull()?.issuerX500Principal == intermediate.subjectX500Principal &&
            chain.none { it == intermediate }) chain + intermediate else chain
        system.checkServerTrusted(completed, authType)
    }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}
