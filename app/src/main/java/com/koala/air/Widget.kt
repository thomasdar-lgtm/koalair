package com.koala.air

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Dernier état connu des clims, partagé entre l'appli et le widget. */
object Cache {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cache", Context.MODE_PRIVATE)

    fun save(ctx: Context, deviceList: JSONArray) {
        prefs(ctx).edit().putString("devices", deviceList.toString()).putLong("at", System.currentTimeMillis()).apply()
    }

    fun devices(ctx: Context): List<Device> =
        runCatching { ConnectLife.parseDevices(JSONArray(prefs(ctx).getString("devices", "[]"))) }.getOrDefault(emptyList())

    fun at(ctx: Context) = prefs(ctx).getLong("at", 0)

    /** Applique tout de suite une commande au cache (avant confirmation du serveur). */
    fun patch(ctx: Context, puid: String, props: Map<String, String>) {
        val arr = runCatching { JSONArray(prefs(ctx).getString("devices", "[]")) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            if (d.optString("puid") == puid) {
                val st = d.optJSONObject("statusList") ?: JSONObject().also { d.put("statusList", it) }
                props.forEach { (k, v) -> st.put(k, v) }
            }
        }
        prefs(ctx).edit().putString("devices", arr.toString()).apply()
    }

    /** Relit l'état sur le serveur (hors fil principal). */
    fun fetch(ctx: Context, session: Session) {
        val (_, json) = session.call { ConnectLife.devices(it) }
        json.optJSONArray("deviceList")?.let { save(ctx, it) }
    }
}

class KoalairWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx)
        val app = ctx.applicationContext
        val session = Session(app)
        if (!session.loggedIn) return
        val done = goAsync()
        thread {
            try { runCatching { Cache.fetch(app, session) }; render(app) } finally { done.finish() }
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_POWER && action != ACTION_NIGHT) { super.onReceive(ctx, intent); return }
        val puid = intent.getStringExtra("puid") ?: return
        val app = ctx.applicationContext
        val session = Session(app)
        if (!session.loggedIn) return
        val done = goAsync()
        thread {
            try {
                val d = Cache.devices(app).firstOrNull { it.puid == puid }
                if (d != null) runCatching {
                    if (action == ACTION_POWER) {
                        val props = mapOf("t_power" to if (d.status["t_power"] == "1") "0" else "1")
                        Cache.patch(app, puid, props); render(app)
                        session.call { ConnectLife.setProperties(it, puid, props) }
                    } else {
                        if (Night.run(app, puid) != null) {
                            Night.stop(app, session, puid, turnOff = false)
                        } else {
                            val c = Night.cfg(app, puid)
                            Night.start(app, session, d, c)
                            Cache.patch(app, puid, Night.startProps(c))
                        }
                        render(app)
                    }
                    Thread.sleep(3500)
                    Cache.fetch(app, session)
                }
                render(app)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val ACTION_POWER = "com.koala.air.WIDGET_POWER"
        const val ACTION_NIGHT = "com.koala.air.WIDGET_NIGHT"

        private fun modeBtn(mode: String?) = when (mode) {
            "2" -> R.drawable.wbtn_ice
            "1" -> R.drawable.wbtn_heat
            "3" -> R.drawable.wbtn_teal
            "0" -> R.drawable.wbtn_lav
            else -> R.drawable.wbtn_yellow
        }

        private const val NAVY = 0xFF00071A.toInt()
        private const val MUTED = 0xFF8C9AB8.toInt()
        private const val LAV = 0xFFB9A6FF.toInt()

        private fun broadcast(ctx: Context, action: String, puid: String) = PendingIntent.getBroadcast(
            ctx, (action + puid).hashCode(),
            Intent(ctx, KoalairWidget::class.java).setAction(action).putExtra("puid", puid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Redessine tous les widgets à partir du cache. */
        fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, KoalairWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.root, open)
            val devices = if (Session(ctx).loggedIn) Cache.devices(ctx) else emptyList()
            val at = Cache.at(ctx)
            v.setTextViewText(R.id.updated, if (at > 0) SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(at)) else "")
            v.setViewVisibility(R.id.empty, if (devices.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)

            val rows = listOf(
                Row(R.id.row1, R.id.info1, R.id.name1, R.id.detail1, R.id.night1, R.id.power1),
                Row(R.id.row2, R.id.info2, R.id.name2, R.id.detail2, R.id.night2, R.id.power2),
            )
            rows.forEachIndexed { i, r ->
                val d = devices.getOrNull(i)
                if (d == null) { v.setViewVisibility(r.row, android.view.View.GONE); return@forEachIndexed }
                v.setViewVisibility(r.row, android.view.View.VISIBLE)
                val s = d.status
                val on = s["t_power"] == "1"
                val night = Night.run(ctx, d.puid)
                v.setTextViewText(r.name, d.name)
                v.setTextViewText(r.detail, buildString {
                    append(if (on) "${Ac.modeName(s["t_work_mode"])} ${s["t_temp"]}°" else "Éteinte")
                    append(" · pièce ${s["f_temp_in"] ?: "?"}°")
                    if (night != null) append(if (night.endAt == 0L) " · nuit" else " · nuit → ${Night.hhmm(minutesOf(night.endAt))}")
                })
                v.setInt(r.power, "setBackgroundResource", if (on) modeBtn(s["t_work_mode"]) else R.drawable.wbtn_off)
                v.setInt(r.power, "setColorFilter", if (on) NAVY else MUTED)
                v.setInt(r.night, "setBackgroundResource", if (night != null) R.drawable.wbtn_lav else R.drawable.wbtn_off)
                v.setInt(r.night, "setColorFilter", if (night != null) NAVY else LAV)
                v.setOnClickPendingIntent(r.power, broadcast(ctx, ACTION_POWER, d.puid))
                v.setOnClickPendingIntent(r.night, broadcast(ctx, ACTION_NIGHT, d.puid))
                v.setOnClickPendingIntent(r.info, open)
            }
            mgr.updateAppWidget(ids, v)
        }

        private data class Row(val row: Int, val info: Int, val name: Int, val detail: Int, val night: Int, val power: Int)
    }
}
