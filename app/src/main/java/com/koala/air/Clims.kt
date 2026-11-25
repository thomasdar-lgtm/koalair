package com.koala.air

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Valeurs connues du modèle 009-104 (d'après l'intégration Home Assistant connectlife-ha). */
object Ac {
    val MODES = listOf("2" to "Froid", "1" to "Chaud", "3" to "Sec", "0" to "Ventil.", "4" to "Auto")
    val FANS = listOf("0" to "Auto", "5" to "1", "6" to "2", "7" to "3", "8" to "4", "9" to "5")
    val SLEEPS = listOf("0" to "Arrêt", "1" to "Général", "2" to "Senior", "3" to "Jeune", "4" to "Enfant")
    const val MIN_TEMP = 16
    const val MAX_TEMP = 32

    fun modeName(v: String?) = MODES.firstOrNull { it.first == v }?.second ?: "?"
    fun fanName(v: String?) = when (v) { "0" -> "Auto"; null -> "?"; else -> FANS.firstOrNull { it.first == v }?.second?.let { "Vitesse $it" } ?: v }
}

/** État partagé des clims + envoi des commandes. */
class Clims(private val ctx: android.content.Context, private val session: Session, private val scope: CoroutineScope) {
    var devices by mutableStateOf<List<Device>>(emptyList())
    var raw by mutableStateOf<JSONObject?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var lastUpdate by mutableStateOf(0L)

    /** Valeurs envoyées mais pas encore confirmées par le serveur : affichées tout de suite. */
    private val pending = mutableMapOf<String, MutableMap<String, Pair<String, Long>>>()
    private var refreshJob: Job? = null
    private var tempJob: Job? = null

    /** Change à chaque début/fin de mode nuit, pour que l'écran relise l'état. */
    var nightVersion by mutableStateOf(0)
    var nightBusy by mutableStateOf<String?>(null)

    fun device(puid: String) = devices.firstOrNull { it.puid == puid }

    fun nightRun(puid: String): NightRun? = Night.run(ctx, puid).also { nightVersion }

    fun startNight(d: Device, c: NightCfg) {
        nightBusy = d.puid
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Night.start(ctx, session, d, c) } }
            nightBusy = null
            nightVersion++
            r.onFailure { error = "Mode nuit non lancé : ${it.message}" }.onSuccess {
                devices = devices.map { x -> if (x.puid == d.puid) x.copy(status = x.status + Night.startProps(c)) else x }
                scheduleRefresh(4000)
            }
        }
    }

    fun stopNight(puid: String, turnOff: Boolean) {
        nightBusy = puid
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Night.stop(ctx, session, puid, turnOff) } }
            nightBusy = null
            nightVersion++
            r.onFailure { error = "Arrêt du mode nuit impossible : ${it.message}" }.onSuccess { scheduleRefresh(3000) }
        }
    }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { session.call { ConnectLife.devices(it) } } }
            loading = false
            r.onSuccess { (list, json) ->
                raw = json
                devices = list.map { applyPending(it) }
                lastUpdate = System.currentTimeMillis()
                error = null
            }.onFailure { error = it.message ?: it.toString() }
        }
    }

    private fun applyPending(d: Device): Device {
        val p = pending[d.puid] ?: return d
        val now = System.currentTimeMillis()
        // Le serveur met quelques secondes à refléter une commande : on garde la valeur envoyée 20 s max.
        p.entries.removeAll { (k, v) -> d.status[k] == v.first || now - v.second > 20_000 }
        if (p.isEmpty()) return d
        return d.copy(status = d.status + p.mapValues { it.value.first })
    }

    /** Envoie des réglages ; l'écran change aussitôt, puis on relit l'état réel. */
    fun send(puid: String, props: Map<String, String>) {
        val now = System.currentTimeMillis()
        val p = pending.getOrPut(puid) { mutableMapOf() }
        props.forEach { (k, v) -> p[k] = v to now }
        devices = devices.map { if (it.puid == puid) it.copy(status = it.status + props) else it }
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { session.call { ConnectLife.setProperties(it, puid, props) } } }
            r.onFailure {
                props.keys.forEach { k -> p.remove(k) }
                error = "Commande non envoyée : ${it.message}"
                refresh()
            }.onSuccess { scheduleRefresh(4000) }
        }
    }

    /** Température : on attend que les appuis +/− s'arrêtent avant d'envoyer. */
    fun setTemp(puid: String, t: Int) {
        val v = t.coerceIn(Ac.MIN_TEMP, Ac.MAX_TEMP).toString()
        devices = devices.map { if (it.puid == puid) it.copy(status = it.status + ("t_temp" to v)) else it }
        tempJob?.cancel()
        tempJob = scope.launch { delay(900); send(puid, mapOf("t_temp" to v)) }
    }

    private fun scheduleRefresh(ms: Long) {
        refreshJob?.cancel()
        refreshJob = scope.launch { delay(ms); refresh() }
    }
}
