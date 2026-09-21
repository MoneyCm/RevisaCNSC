package co.meritoradar.app

import java.net.URI

fun officialLink(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.host in setOf("www.cnsc.gov.co", "cnsc.gov.co", "simo.cnsc.gov.co") && uri.userInfo == null && uri.port in setOf(-1, 443)
}.getOrDefault(false)
fun processLink(value: String?): String? = runCatching {
    val uri = URI(value ?: return null)
    if (uri.scheme != "meritoradar" || uri.host != "process") return null
    uri.path.removePrefix("/").takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }
}.getOrNull()
