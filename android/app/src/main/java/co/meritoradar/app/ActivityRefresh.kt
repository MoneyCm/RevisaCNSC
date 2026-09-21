package co.meritoradar.app

import kotlinx.coroutines.CancellationException

data class ActivityCheck(val processId: String, val processName: String, val checkedAt: String, val error: String?)

object ActivityRefresh {
    fun candidates(processes: List<Process>, followed: Set<String>, attempts: Map<String, Long>,
        now: Long): List<Process> = processes.filter { it.id in followed }
        .filter { now - (attempts[it.id] ?: 0L) > 1_800_000 }
        .sortedBy { attempts[it.id] ?: 0L }.take(3)

    suspend fun run(processes: List<Process>, read: suspend (Process) -> ProcessActivity,
        save: suspend (Process, ProcessActivity) -> Unit, report: suspend (Process, String?) -> Unit) {
        for (process in processes) {
            val activity = try { read(process) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                report(process, "No se pudo leer el micrositio. Se conserva la última información disponible.")
                continue
            }
            // Database failures must propagate; they are not failures of an external source.
            save(process, activity)
            report(process, null)
        }
    }
}
