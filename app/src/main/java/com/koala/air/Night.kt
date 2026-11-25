package com.koala.air

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.util.Calendar
import kotlin.concurrent.thread

/** Réglages du mode nuit Koalair pour une clim. */
data class NightCfg(
    val temp: Int = 16,
    val fan: String = "5",          // vitesse 1
    val screenOff: Boolean = true,
    val endEnabled: Boolean = false,
    val endMinutes: Int = 7 * 60,   // heure de fin (minutes depuis minuit)
    val endOff: Boolean = false,    // à la fin : true = éteindre, false = reprendre les réglages d'avant
    val autoEnabled: Boolean = false,
    val autoMinutes: Int = 22 * 60 + 30,
    val autoDays: Int = 0b1111111,  // bit 0 = lundi … bit 6 = dimanche
)

/** Mode nuit en cours. */
data class NightRun(val endAt: Long, val prevTemp: String?, val prevFan: String?, val label: String)

/**
 * Mode nuit piloté par l'appli : la clim reste en fonctionnement normal (pas de Sommeil de la clim,
 * donc pas d'arrêt automatique ni de dérive de température). La fin éventuelle est déclenchée par
 * une alarme Android, qui envoie la commande même si l'appli est fermée.
 */
object Night {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("night", Context.MODE_PRIVATE)

    fun cfg(ctx: Context, puid: String): NightCfg {
        val s = prefs(ctx).getString("cfg_$puid", null) ?: return NightCfg()
        return runCatching {
            val o = JSONObject(s)
            NightCfg(
                o.optInt("temp", 16), o.optString("fan", "5"), o.optBoolean("screenOff", true),
                o.optBoolean("endEnabled", false), o.optInt("endMinutes", 420), o.optBoolean("endOff", false),
                o.optBoolean("autoEnabled", false), o.optInt("autoMinutes", 22 * 60 + 30), o.optInt("autoDays", 0b1111111),
            )
        }.getOrDefault(NightCfg())
    }

    fun saveCfg(ctx: Context, puid: String, c: NightCfg) {
        val o = JSONObject().put("temp", c.temp).put("fan", c.fan).put("screenOff", c.screenOff)
            .put("endEnabled", c.endEnabled).put("endMinutes", c.endMinutes).put("endOff", c.endOff)
            .put("autoEnabled", c.autoEnabled).put("autoMinutes", c.autoMinutes).put("autoDays", c.autoDays)
        prefs(ctx).edit().putString("cfg_$puid", o.toString()).apply()
        scheduleAuto(ctx, puid, c)
    }

    fun run(ctx: Context, puid: String): NightRun? {
        val s = prefs(ctx).getString("run_$puid", null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            NightRun(o.optLong("endAt", 0), o.optString("prevTemp").ifEmpty { null }, o.optString("prevFan").ifEmpty { null }, o.optString("label"))
        }.getOrNull()
    }

    private fun saveRun(ctx: Context, puid: String, r: NightRun?) {
        if (r == null) prefs(ctx).edit().remove("run_$puid").apply()
        else prefs(ctx).edit().putString(
            "run_$puid",
            JSONObject().put("endAt", r.endAt).put("prevTemp", r.prevTemp ?: "").put("prevFan", r.prevFan ?: "").put("label", r.label).toString(),
        ).apply()
    }

    /** Commandes du mode nuit. */
    fun startProps(c: NightCfg): Map<String, String> = buildMap {
        put("t_power", "1")
        put("t_sleep", "0")
        put("t_super", "0")
        put("t_fan_mute", "0")
        put("t_temp", c.temp.toString())
        put("t_fan_speed", c.fan)
    }

    /** Prochaine occurrence de l'heure de fin (demain si elle est déjà passée). */
    fun nextEnd(minutes: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now + 60_000) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** Prochain lancement automatique (jours cochés uniquement), 0 si aucun jour. */
    fun nextAuto(minutes: Int, days: Int, now: Long = System.currentTimeMillis()): Long {
        if ((days and 0b1111111) == 0) return 0
        for (i in 0..7) {
            val c = Calendar.getInstance().apply {
                timeInMillis = now; add(Calendar.DAY_OF_MONTH, i)
                set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lundi = 0
            if (c.timeInMillis > now + 30_000 && (days and (1 shl bit)) != 0) return c.timeInMillis
        }
        return 0
    }

    private fun autoPending(ctx: Context, puid: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, ("auto$puid").hashCode(),
            Intent(ctx, NightAutoReceiver::class.java).putExtra("puid", puid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleAuto(ctx: Context, puid: String, c: NightCfg = cfg(ctx, puid)) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = autoPending(ctx, puid)
        am.cancel(pi)
        val at = if (c.autoEnabled) nextAuto(c.autoMinutes, c.autoDays) else 0L
        if (at == 0L) return
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** À appeler hors du fil principal. */
    fun start(ctx: Context, session: Session, d: Device, c: NightCfg) {
        session.call { ConnectLife.setProperties(it, d.puid, startProps(c)) }
        // L'écran : commande séparée, la clim l'applique mieux une fois les autres réglages pris en compte
        if (c.screenOff) runCatching { Thread.sleep(1500); session.call { ConnectLife.setProperties(it, d.puid, mapOf("t_dimmer" to "0")) } }
        val endAt = if (c.endEnabled) nextEnd(c.endMinutes) else 0L
        val prev = run(ctx, d.puid) // si déjà en mode nuit, on garde les réglages d'origine
        saveRun(ctx, d.puid, NightRun(endAt, prev?.prevTemp ?: d.status["t_temp"], prev?.prevFan ?: d.status["t_fan_speed"], d.name))
        if (endAt > 0) schedule(ctx, d.puid, endAt) else cancel(ctx, d.puid)
    }

    /** Fin du mode nuit (bouton ou alarme). À appeler hors du fil principal. */
    fun stop(ctx: Context, session: Session, puid: String, turnOff: Boolean) {
        val r = run(ctx, puid)
        if (turnOff) {
            session.call { ConnectLife.setProperties(it, puid, mapOf("t_power" to "0")) }
        } else {
            val p = buildMap {
                r?.prevTemp?.takeIf { it != "0" }?.let { put("t_temp", it) }
                r?.prevFan?.let { put("t_fan_speed", it) }
            }
            if (p.isNotEmpty()) session.call { ConnectLife.setProperties(it, puid, p) }
            runCatching { Thread.sleep(1500); session.call { ConnectLife.setProperties(it, puid, mapOf("t_dimmer" to "1")) } }
        }
        saveRun(ctx, puid, null)
        cancel(ctx, puid)
    }

    // ---------- Alarmes ----------

    private fun pending(ctx: Context, puid: String, attempt: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, puid.hashCode(),
            Intent(ctx, NightEndReceiver::class.java).putExtra("puid", puid).putExtra("attempt", attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, puid: String, at: Long, attempt: Int = 0) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, puid, attempt)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context, puid: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, puid))
    }

    /** Après un redémarrage du téléphone, Android oublie les alarmes : on les reprogramme. */
    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        prefs(ctx).all.keys.filter { it.startsWith("cfg_") }.forEach { scheduleAuto(ctx, it.removePrefix("cfg_")) }
        prefs(ctx).all.keys.filter { it.startsWith("run_") }.forEach { k ->
            val puid = k.removePrefix("run_")
            val r = run(ctx, puid) ?: return@forEach
            if (r.endAt > 0) schedule(ctx, puid, maxOf(r.endAt, now + 30_000))
        }
    }
}

class NightEndReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val puid = intent.getStringExtra("puid") ?: return
        val attempt = intent.getIntExtra("attempt", 0)
        val app = ctx.applicationContext
        if (Night.run(app, puid) == null) return
        val done = goAsync()
        thread {
            try {
                val turnOff = Night.cfg(app, puid).endOff
                val ok = runCatching { Night.stop(app, Session(app), puid, turnOff) }.isSuccess
                KoalairWidget.render(app)
                // Pas de réseau au réveil ? Nouvel essai dans 2 min, 5 fois maximum.
                if (!ok && attempt < 5) Night.schedule(app, puid, System.currentTimeMillis() + 120_000, attempt + 1)
            } finally {
                done.finish()
            }
        }
    }
}

/** Lancement automatique du mode nuit à l'heure choisie. */
class NightAutoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val puid = intent.getStringExtra("puid") ?: return
        val app = ctx.applicationContext
        val done = goAsync()
        thread {
            try {
                val session = Session(app)
                if (session.loggedIn && Night.run(app, puid) == null) {
                    var ok = false
                    for (attempt in 0 until 3) {
                        ok = runCatching {
                            Cache.fetch(app, session)
                            val d = Cache.devices(app).first { it.puid == puid }
                            val c = Night.cfg(app, puid)
                            Night.start(app, session, d, c)
                            Cache.patch(app, puid, Night.startProps(c))
                        }.isSuccess
                        if (ok) break
                        Thread.sleep(20_000)
                    }
                    KoalairWidget.render(app)
                }
            } finally {
                Night.scheduleAuto(app, puid) // prochain soir
                done.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Night.rescheduleAll(ctx.applicationContext)
    }
}
