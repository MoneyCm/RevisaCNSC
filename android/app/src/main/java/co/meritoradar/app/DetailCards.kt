package co.meritoradar.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun calendarDate(value: String?): String = value?.let {
    runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CO"))) }.getOrDefault("Por confirmar")
} ?: "No publicada"

fun stageSummary(stages: List<StageInfo>, today: LocalDate = LocalDate.now(java.time.ZoneId.of("America/Bogota"))): String {
    if (stages.isEmpty()) return "Sin etapa confirmada"
    if (stages.any { it.status == "REVIEW_REQUIRED" }) return "Inscripciones aplazadas o en revisión"
    val names = stages.map { if (it.kind == "PAYMENT") "Recaudo" else "Inscripción" }.distinct()
    val label = names.joinToString(" y ")
    val registration = stages.filter { it.kind == "REGISTRATION" }
    if (registration.any { it.status == "REVIEW_REQUIRED" }) return "Inscripciones aplazadas o en revisión"
    val confirmedRegistration = registration.filter { it.confidence == "CONFIRMED" && it.startDate != null && it.endDate != null }
    if (confirmedRegistration.isNotEmpty()) {
        val current = confirmedRegistration.firstOrNull { datesContain(it, today) }
        if (current != null) return "Inscripciones abiertas hasta ${calendarDate(current.endDate)}"
        if (confirmedRegistration.all { LocalDate.parse(it.endDate) < today }) return "Inscripciones cerradas"
        if (confirmedRegistration.all { LocalDate.parse(it.startDate) > today }) return "Inscripciones próximas desde ${calendarDate(confirmedRegistration.minBy { it.startDate!! }.startDate)}"
    }
    return if (stages.all { it.confidence == "CONFIRMED" && it.startDate != null && it.endDate != null })
        "Etapas publicadas: $label"
    else "Etapa publicada; estado de inscripción por confirmar"
}

fun datesContain(stage: StageInfo, date: LocalDate): Boolean =
    runCatching { date >= LocalDate.parse(stage.startDate) && date <= LocalDate.parse(stage.endDate) }.getOrDefault(false)
fun officialActivitySummary(events: List<EventInfo>): String? {
    val labels = events.mapNotNull { event ->
        val title = Normalizer.normalize(event.title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        when (event.eventType) {
            "EXAM_CITATION" -> "Citación publicada"
            "RESULT_PUBLISHED" -> "Resultados publicados"
            "ELIGIBLE_LIST_PUBLISHED" -> "Lista de elegibles publicada"
            "PROCESS_SUSPENDED" -> "Proceso suspendido"
            "PROCESS_RESUMED" -> "Proceso reanudado"
            "NOTICE_PUBLISHED" -> when {
                "valoracion medica" in title -> "Valoración médica anunciada"
                "lista de elegibles" in title || "listas de elegibles" in title -> "Lista de elegibles anunciada"
                "resultados" in title && "pruebas" in title -> "Resultados de pruebas anunciados"
                "aplazamiento" in title && "inscripciones" in title -> "Inscripciones aplazadas"
                "continuidad" in title -> "Continuidad del proceso publicada"
                else -> "Aviso oficial publicado; etapa específica por confirmar"
            }
            else -> null
        }
    }.distinct()
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { "Actividad oficial: $it" }
}

fun eventLabel(type: String): String = when(type) {
    "PAYMENT_REVIEW_REQUIRED" -> "Recaudo: fechas por revisar"
    "REGISTRATION_REVIEW_REQUIRED" -> "Inscripción: fechas por revisar"
    "NOTICE_PUBLISHED" -> "Aviso oficial detectado"
    "NOTICE_UPDATED" -> "Contenido del aviso actualizado"
    "PROCESS_DISCOVERED" -> "Nuevo concurso"
    "PAYMENT_ANNOUNCED" -> "Fechas de recaudo publicadas"
    "REGISTRATION_ANNOUNCED" -> "Fechas de inscripción publicadas"
    "PAYMENT_DATE_CHANGED" -> "Cambió la fecha de recaudo"
    "REGISTRATION_DATE_CHANGED" -> "Cambió la fecha de inscripción"
    "PAYMENT_POSTPONED" -> "Recaudo aplazado"
    "REGISTRATION_POSTPONED" -> "Inscripciones aplazadas"
    "PAYMENT_OPEN" -> "Inicio de recaudo según fecha publicada"
    "REGISTRATION_OPEN" -> "Inicio de inscripciones según fecha publicada"
    "PAYMENT_CLOSING_SOON" -> "Cierre de recaudo próximo"
    "REGISTRATION_CLOSING_SOON" -> "Cierre de inscripciones próximo"
    "PROCESS_SUSPENDED" -> "Suspensión del proceso"
    "PROCESS_RESUMED" -> "Reanudación del proceso"
    "EXAM_CITATION" -> "Citación publicada"
    "RESULT_PUBLISHED" -> "Resultados publicados"
    "ELIGIBLE_LIST_PUBLISHED" -> "Lista de elegibles publicada"
    else -> "Publicación oficial"
}

@Composable
fun OfficialButton(url: String, label: String = "Ver publicación oficial") {
    val context = LocalContext.current
    if (officialLink(url)) OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(label) }
}

@Composable
fun StageCard(stage: StageInfo) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (stage.kind == "PAYMENT") "Recaudo" else "Inscripción", style = MaterialTheme.typography.titleMedium)
            Text((when (stage.modality) { "PROMOTION" -> "Ascenso"; "OPEN" -> "Abierto"; else -> "Modalidad por confirmar" }) + " · " +
                (when (stage.population) { "DISABILITY_RESERVED" -> "Reserva para discapacidad"; "GENERAL" -> "General"; else -> "Población por confirmar" }))
            if (stage.confidence != "CONFIRMED") Text("Interpretación por confirmar", color = MaterialTheme.colorScheme.error)
            Text(stageSummary(listOf(stage)), color = if (stage.status == "REVIEW_REQUIRED") MaterialTheme.colorScheme.error else LocalContentColor.current)
            Text("Inicio publicado: ${calendarDate(stage.startDate)}")
            Text("Cierre publicado: ${calendarDate(stage.endDate)}")
            if (stage.endDate?.let { runCatching { LocalDate.parse(it) < LocalDate.now(java.time.ZoneId.of("America/Bogota")) }.getOrDefault(false) } == true)
                Text("Ventana publicada ya transcurrida; no implica que el proceso haya terminado.")
            Text("Consulta la fuente antes de actuar.", style = MaterialTheme.typography.bodySmall)
            OfficialButton(stage.officialUrl)
        }
    }
}

@Composable
fun EventCard(event: EventInfo, openDetail: (() -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eventLabel(event.eventType), style = MaterialTheme.typography.titleMedium)
            Text(event.title)
            if (event.confidence != "CONFIRMED") Text("Interpretación por confirmar", color = MaterialTheme.colorScheme.error)
            if (!event.notifyEligible) Text("Histórico o informativo · sin aviso automático", style = MaterialTheme.typography.bodySmall)
            event.publishedAt?.let { Text("Publicado: ${displayDate(it)}", style = MaterialTheme.typography.bodySmall) }
            Text("Detectado: ${displayDate(event.detectedAt)}", style = MaterialTheme.typography.bodySmall)
            listOf("start_date" to "Inicio", "end_date" to "Cierre").forEach { (field, label) ->
                val before = event.evidence?.old?.get(field)
                val after = event.evidence?.new?.get(field)
                if (event.evidence?.old != null && before != after) {
                    Text("$label antes: ${calendarDate(before)}")
                    Text("$label ahora: ${calendarDate(after)}")
                }
            }
            if (openDetail != null) TextButton(onClick = openDetail) { Text("Ver concurso") }
            event.evidence?.excerpt?.let { Text(it, maxLines = 8) }
            event.evidence?.old?.get("text")?.let { Text("Texto anterior: " + it, maxLines = 5) }
            event.evidence?.url?.let { OfficialButton(it) }
        }
    }
}

@Composable
fun PublicationCard(publication: PublicationInfo) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(publication.title, style = MaterialTheme.typography.titleMedium)
            publication.publishedAt?.let { Text(displayDate(it)) }
            publication.paragraphs.firstOrNull()?.let { Text(it, maxLines = 5) }
            OfficialButton(publication.officialUrl)
            publication.documents.forEach { OfficialButton(it.url, it.title) }
        }
    }
}
