package co.meritoradar.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SyncState(val loading: Boolean = false, val error: String? = null, val health: Health? = null, val workStatus: String = "idle")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RadarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as RadarApp).repository
    private val selectedId = MutableStateFlow<String?>(null)
    val identities = repository.identities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val activities = repository.activities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val stageSummaries = repository.stageSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val detailContent = selectedId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeDetail(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val alerts = repository.alerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val processes = repository.processes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val following = repository.following.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val workStatus = repository.observeWorkManagerStatus().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "idle")
    private val mutableSync = MutableStateFlow(SyncState())
    val sync = mutableSync.asStateFlow()
    init { refresh() }
    fun refresh() {
        if (mutableSync.value.loading) return
        viewModelScope.launch {
            mutableSync.value = mutableSync.value.copy(loading = true, error = null)
            try { mutableSync.value = SyncState(health = repository.refresh()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableSync.value = mutableSync.value.copy(loading = false, error = e.message ?: "No pudimos actualizar. Conservamos la información guardada.") }
        }
    }
    fun loadDetail(id: String, force: Boolean = false) { selectedId.value = id; viewModelScope.launch {
        try { repository.detail(id, force) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableSync.value = mutableSync.value.copy(error = "No pudimos consultar el detalle. Reintenta cuando tengas conexión.") }
    } }
    fun follow(id: String, enabled: Boolean) { viewModelScope.launch { repository.follow(id, enabled) } }
}
