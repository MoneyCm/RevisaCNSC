package co.meritoradar.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var linkedId by mutableStateOf<String?>(null)

    // Notification permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channels
        LocalNotifier.createNotificationChannels(this)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        linkedId = processLink(intent.dataString)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFBCEB9D))
                else lightColorScheme(primary = Color(0xFF245E4D))) {
                RadarScreen(linkedId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkedId = processLink(intent.dataString)
    }
}

fun displayDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("America/Bogota"))
        .format(DateTimeFormatter.ofPattern("d MMM · h:mm a", Locale.forLanguageTag("es-CO")))
}.getOrDefault("Fecha no disponible")

@Composable
fun RadarScreen(linkedId: String?, vm: RadarViewModel = viewModel()) {
    val context = LocalContext.current
    var notificationTestResult by rememberSaveable { mutableStateOf<String?>(null) }
    val processes by vm.processes.collectAsStateWithLifecycle()
    val identities by vm.identities.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val stageSummaries by vm.stageSummaries.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val detailContent by vm.detailContent.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf("Inicio") }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFollowing by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(linkedId) { if (linkedId != null) selected = linkedId }
    LaunchedEffect(selected) { selected?.let { vm.loadDetail(it) } }
    BackHandler(enabled = selected != null) { selected = null }
    val detail = processes.find { it.id == selected }
    val tabs = listOf("Inicio" to Icons.Default.Home, "Concursos" to Icons.AutoMirrored.Filled.List,
        "Alertas" to Icons.Default.Notifications, "Ajustes" to Icons.Default.Settings)
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEach { (name, icon) -> NavigationBarItem(selected = tab == name,
                onClick = { tab = name; selected = null }, icon = { Icon(icon, contentDescription = null) },
                label = { Text(name) }) }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Mérito Radar", style = MaterialTheme.typography.headlineLarge)
                Text("No pierdas tu próxima oportunidad.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                if (sync.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                sync.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Catálogo CNSC · ${processes.size} procesos guardados")
                Text("Última revisión: " + (sync.health?.lastRun?.let(::displayDate)
                    ?: processes.maxOfOrNull { it.lastCheckedAt }?.let(::displayDate) ?: "Sin revisión"))
                if (sync.health?.status == "degraded") Text("Problemas de vigilancia", color = MaterialTheme.colorScheme.error)
                Text("Avisos locales para concursos seguidos. La primera lectura se guarda como histórico.", style = MaterialTheme.typography.bodySmall)
            }
            if (selected != null) {
                item { TextButton(onClick = { selected = null }) { Text("Volver a concursos") } }
                if (detail == null) {
                    item { Text("El detalle no está guardado."); Button(onClick = { vm.loadDetail(selected!!) }) { Text("Reintentar detalle") } }
                } else {
                    item {
                        Text(detail.name, style = MaterialTheme.typography.headlineMedium)
                        Text(processYearLabel(detail, identities[detail.id]))
                        Text("El año de convocatoria puede ser distinto al de inscripción. Estar en desarrollo no significa que puedas inscribirte ahora.")
                        identities[detail.id]?.let { OfficialButton(it.sourceUrl, "Ver avisos de esta convocatoria") }
                        Text("Estado del catálogo: ${if (detail.category == "IN_DEVELOPMENT") "en desarrollo" else "publicado por CNSC"}")
                        Text(detailContent?.let {
                            stageSummary(it.stages).takeUnless { summary -> summary == "Sin etapa confirmada" }
                                ?: officialActivitySummary(it.events) ?: "Sin etapa confirmada"
                        } ?: "Consultando etapas guardadas...")
                        Button(onClick = { vm.follow(detail.id, detail.id !in following) }) { Text(if (detail.id in following) "★ Siguiendo" else "☆ Seguir") }
                        OfficialButton(detail.officialUrl, "Ver fuente oficial")
                        TextButton(onClick = { vm.loadDetail(detail.id, true) }) { Text("Actualizar detalle") }
                        activities[detail.id]?.let { activity ->
                            Text("Última actividad encontrada", style = MaterialTheme.typography.titleMedium)
                            Text(activity.summary)
                            Text(activity.title)
                            Text("Publicado: " + displayDate(activity.publishedAt))
                            Text("Revisado: " + displayDate(activity.checkedAt))
                            Text("Describe el último aviso fechado de la página consultada; puede referirse solo a una modalidad o población.")
                            OfficialButton(activity.sourceUrl, "Ver aviso en el micrositio oficial")
                        }
                    }
                    item { Text("Fechas publicadas", style = MaterialTheme.typography.titleLarge) }
                    if (detailContent == null) item { Text("Consultando avisos. Si no tienes conexión, sólo se mostrarán los detalles guardados.") }
                    else if (detailContent!!.stages.isEmpty()) item {
                        Text("Los avisos guardados no contienen fechas que podamos confirmar. Esto no significa que CNSC no las haya publicado: la cobertura de avisos antiguos aún es limitada.")
                        OfficialButton(detail.officialUrl + "?field_tipo_de_contenido_convocat_target_id=64", "Consultar historial oficial de avisos")
                    }
                    items(detailContent?.stages ?: emptyList(), key = { "stage:${it.id}" }) { StageCard(it) }
                    item { Text("Actividad reciente", style = MaterialTheme.typography.titleLarge) }
                    items(detailContent?.events ?: emptyList(), key = { "event:${it.id}" }) { EventCard(it) }
                    item { Text("Publicaciones y documentos", style = MaterialTheme.typography.titleLarge) }
                    items(detailContent?.publications ?: emptyList(), key = { "publication:${it.id}" }) { PublicationCard(it) }
                }
            } else if (tab == "Ajustes") {
                item {
                    Text("Sistema de vigilancia", style = MaterialTheme.typography.titleLarge)
                    Text(sync.health?.let { "Fuentes disponibles: ${it.sourcesOk} · con problemas: ${it.sourcesFailed} · publicaciones por revisar: ${it.reviewRequired}" } ?: "Estado del monitor no disponible")
                    Button(onClick = vm::refresh, enabled = !sync.loading) { Text("Consultar estado") }
                    Text("Prueba de notificaciones", style = MaterialTheme.typography.titleMedium)
                    Text("Envía un aviso de prueba silencioso a la bandeja del teléfono. No es una novedad CNSC ni comprueba la vigilancia automática.")
                    OutlinedButton(onClick = {
                        notificationTestResult = if (LocalNotifier(context).showTestNotification())
                            "Prueba enviada a Android. Abre la bandeja de notificaciones para comprobar si aparece."
                        else "Notificaciones bloqueadas. Revisa el permiso de la app y el canal Información general."
                    }) { Text("Enviar notificación de prueba") }
                    notificationTestResult?.let { Text(it) }
                    TextButton(onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text("Configurar notificaciones del teléfono") }
                    Text("Versión ${BuildConfig.VERSION_NAME}")
                    Text("Los favoritos y la información reciente se conservan en este teléfono. No solicitamos credenciales de SIMO.")
                    Text("Revisión periódica solicitada cada 15 minutos; Android puede retrasarla. Cobertura inicial: página reciente de avisos CNSC. Fechas extraídas solo de intervalos explícitos; recordatorios de vencimiento y preferencias pendientes.")
                    OfficialButton("https://www.cnsc.gov.co/avisos-informativos", "Fuentes de información")
                }
            } else if (tab == "Alertas") {
                item {
                    Text("Alertas", style = MaterialTheme.typography.headlineMedium)
                    FilterChip(selected = history, onClick = { history = !history }, label = { Text("Incluir histórico") })
                    Button(onClick = vm::refresh, enabled = !sync.loading) { Text("Actualizar alertas") }
                }
                val visible = alerts.filter { history || it.notifyEligible }
                if (visible.isEmpty()) item { Text("No hay alertas nuevas guardadas. Puedes consultar el histórico o actualizar.") }
                items(visible, key = { it.id }) { event -> EventCard(event) { selected = event.processId } }
            } else {
                if (tab == "Inicio") {
                    item {
                        Text("Mis concursos", style = MaterialTheme.typography.headlineMedium)
                        Text("${following.size} concursos en seguimiento local")
                        Button(onClick = { tab = "Concursos" }) { Text("Elegir concursos") }
                    }
                    items(alerts.filter { it.notifyEligible && it.priority == "CRITICAL" }.take(3), key = { "urgent:${it.id}" }) { event -> EventCard(event) { selected = event.processId } }
                } else {
                    item {
                        Button(onClick = vm::refresh, enabled = !sync.loading) { Text("Actualizar catálogo") }
                        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar concurso") }, modifier = Modifier.fillMaxWidth())
                        Text("Este catálogo incluye convocatorias de años anteriores que siguen en desarrollo. El número del proceso no es su año y no confirma inscripciones abiertas.")
                        FilterChip(selected = onlyFollowing, onClick = { onlyFollowing = !onlyFollowing }, label = { Text("Siguiendo") })
                    }
                }
                val visible = processes.filter { (tab != "Inicio" || it.id in following) &&
                    (tab != "Concursos" || (it.name.contains(query, ignoreCase = true) && (!onlyFollowing || it.id in following))) }
                if (visible.isEmpty() && !sync.loading) item { Text(if (tab == "Inicio") "Elige los concursos que quieres seguir." else "No hay concursos que coincidan.") }
                items(visible, key = { it.id }) { process ->
                    OutlinedCard(onClick = { selected = process.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(process.name, style = MaterialTheme.typography.titleMedium)
                            Text(processYearLabel(process, identities[process.id]), style = MaterialTheme.typography.bodySmall)
                            Text(activities[process.id]?.let { "Último aviso: " + it.summary }
                                ?: stageSummaries[process.id] ?: "Etapas aún no consultadas")
                            TextButton(onClick = { vm.follow(process.id, process.id !in following) }) { Text(if (process.id in following) "★ Siguiendo" else "☆ Seguir") }
                        }
                    }
                }
            }
            item { HorizontalDivider(); Text("Mérito Radar es una aplicación independiente de seguimiento de información pública. No está afiliada ni representa a la Comisión Nacional del Servicio Civil. Para actuaciones oficiales, consulte siempre la CNSC y SIMO.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
