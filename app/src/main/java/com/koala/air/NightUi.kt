package com.koala.air

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Seg(label: String, selected: Boolean, accent: Color = Lavender, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) accent else CardHi)
            .clickable { onClick() }.padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Navy else Soft, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1) }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(G.Minus, onMinus, 40.dp, CardHi)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(92.dp))
        RoundIcon(G.Plus, onPlus, 40.dp, CardHi)
    }
}

@Composable
private fun Field(label: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Carte « Mode nuit » de l'écran d'une clim. */
@Composable
fun NightSection(d: Device, clims: Clims) {
    val ctx = LocalContext.current
    var cfg by remember(d.puid) { mutableStateOf(Night.cfg(ctx, d.puid)) }
    fun upd(c: NightCfg) { cfg = c; Night.saveCfg(ctx, d.puid, c) }
    val run = clims.nightRun(d.puid)
    val busy = clims.nightBusy == d.puid
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape)
            .background(Card)
            .background(Brush.linearGradient(listOf(Lavender.copy(alpha = if (run != null) 0.25f else 0.10f), Color.Transparent)))
            .border(1.dp, if (run != null) Lavender.copy(alpha = 0.5f) else Line, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Lavender.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Glyph(G.Moon, Lavender, 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mode nuit", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    when {
                        run == null -> "La clim ne s'éteint pas toute seule"
                        run.endAt == 0L -> "Actif · sans fin"
                        else -> "Actif · fin à ${Night.hhmm(minutesOf(run.endAt))} → ${if (cfg.endOff) "extinction" else "réglages d'avant"}"
                    },
                    color = if (run != null) Lavender else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        Field("Température") {
            Stepper("${cfg.temp}°", { upd(cfg.copy(temp = (cfg.temp - 1).coerceAtLeast(Ac.MIN_TEMP))) }, { upd(cfg.copy(temp = (cfg.temp + 1).coerceAtMost(Ac.MAX_TEMP))) })
        }
        Field("Ventilation") {
            Ac.FANS.forEach { (v, label) -> Seg(label, cfg.fan == v, modifier = Modifier.weight(1f)) { upd(cfg.copy(fan = v)) } }
        }
        Field("Écran de la clim") {
            Seg("Éteint", cfg.screenOff, modifier = Modifier.weight(1f)) { upd(cfg.copy(screenOff = true)) }
            Seg("Allumé", !cfg.screenOff, modifier = Modifier.weight(1f)) { upd(cfg.copy(screenOff = false)) }
        }
        Field("Fin du mode nuit") {
            Seg("Sans fin", !cfg.endEnabled, modifier = Modifier.weight(1f)) { upd(cfg.copy(endEnabled = false)) }
            Seg("À heure fixe", cfg.endEnabled, modifier = Modifier.weight(1f)) { upd(cfg.copy(endEnabled = true)) }
        }
        if (cfg.endEnabled) {
            Field("Heure de fin") {
                Stepper(
                    Night.hhmm(cfg.endMinutes),
                    { upd(cfg.copy(endMinutes = (cfg.endMinutes - 15 + 1440) % 1440)) },
                    { upd(cfg.copy(endMinutes = (cfg.endMinutes + 15) % 1440)) },
                )
            }
            Field("À l'heure de fin") {
                Seg("Réglages d'avant", !cfg.endOff, modifier = Modifier.weight(1f)) { upd(cfg.copy(endOff = false)) }
                Seg("Éteindre", cfg.endOff, modifier = Modifier.weight(1f)) { upd(cfg.copy(endOff = true)) }
            }
            if (!Night.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
                Text(
                    "Pour une fin pile à l'heure, autorise les alarmes pour Koalair (sinon Android peut la décaler de quelques minutes). Toucher ici.",
                    color = Yellow, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp).clickable {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                        }
                    },
                )
            }
        }
        if (cfg.endEnabled) Text(
            "Le téléphone envoie la commande de fin : il doit rester allumé avec internet.",
            color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(16.dp))
        if (busy) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lavender, modifier = Modifier.size(26.dp)) }
        } else if (run == null) {
            BigButton("Lancer le mode nuit", Lavender) { clims.startNight(d, cfg) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Appliquer", CardHi, Soft, Modifier.weight(1f)) { clims.startNight(d, cfg) }
                BigButton("Arrêter", Lavender, Navy, Modifier.weight(1f)) { clims.stopNight(d.puid, turnOff = false) }
            }
        }
    }
}

@Composable
fun BigButton(label: String, bg: Color, fg: Color = Navy, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Black) }
}

fun minutesOf(ms: Long): Int {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}
