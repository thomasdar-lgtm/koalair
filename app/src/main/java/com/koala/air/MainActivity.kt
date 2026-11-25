package com.koala.air

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val session = Session(this)
        setContent { KoalairTheme { App(session) } }
    }
}

@Composable
fun App(session: Session) {
    var loggedIn by remember { mutableStateOf(session.loggedIn) }
    val upd = remember { UpdateState() }
    LaunchedEffect(Unit) { upd.info = runCatching { Updater.check() }.getOrNull() }
    Column(Modifier.fillMaxSize().background(BgBrush).safeDrawingPadding()) {
        UpdateBanner(upd)
        if (loggedIn) Main(session) { session.logout(); loggedIn = false }
        else LoginScreen(session) { loggedIn = true }
    }
}

@Composable
fun Main(session: Session, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clims = remember { Clims(session, scope) }
    var open by remember { mutableStateOf<String?>(null) }
    // Actualisation toutes les 60 s tant que l'appli est à l'écran (pas plus : le serveur bannit les abus)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { clims.refresh(); delay(60_000) }
        }
    }
    BackHandler(enabled = open != null) { open = null }
    AnimatedContent(
        targetState = open,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        label = "screen",
    ) { puid ->
        val d = puid?.let { clims.device(it) }
        if (d != null) DetailScreen(d, clims) { open = null }
        else HomeScreen(clims, session.email, onOpen = { open = it }, onLogout = onLogout)
    }
}

// ---------------- Briques visuelles ----------------

@Composable
fun Logo(size: Int = 34) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Koa", color = Yellow, fontSize = size.sp, fontWeight = FontWeight.Black)
        Text("lair", color = Ice, fontSize = size.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun RoundIcon(g: G, onClick: () -> Unit, size: Dp = 44.dp, bg: Color = CardHi, fg: Color = Color.White, enabled: Boolean = true) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Glyph(g, fg, size * 0.5f) }
}

@Composable
fun Pill(g: G?, text: String, color: Color, spin: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (g != null) { Glyph(g, color, 14.dp, spin = spin); Spacer(Modifier.width(5.dp)) }
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String, hint: String? = null) {
    Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp)) {
        Text(text.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        hint?.let { Text(it, color = Muted.copy(alpha = 0.8f), fontSize = 11.sp) }
    }
}

@Composable
fun PowerButton(on: Boolean, accent: Color, size: Dp = 60.dp, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) accent else CardHi, tween(400), label = "pbg")
    val fg by animateColorAsState(if (on) Navy else Muted, tween(400), label = "pfg")
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .border(2.dp, if (on) accent.copy(alpha = 0.5f) else Line, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Glyph(G.Power, fg, size * 0.45f) }
}

/** Tuile sélectionnable : icône + libellé. */
@Composable
fun Tile(
    label: String, g: G?, selected: Boolean, accent: Color, modifier: Modifier = Modifier,
    sub: String? = null, enabled: Boolean = true, onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) accent else Card, tween(300), label = "tbg")
    val fg = if (selected) Navy else Color.White
    Column(
        modifier.alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, if (selected) Color.Transparent else Line, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }.padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (g != null) { Glyph(g, if (selected) Navy else accent, 24.dp); Spacer(Modifier.height(6.dp)) }
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        sub?.let { Text(it, color = fg.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1) }
    }
}

fun ago(ms: Long): String {
    if (ms == 0L) return "jamais"
    val s = (System.currentTimeMillis() - ms) / 1000
    return when { s < 10 -> "à l'instant"; s < 60 -> "il y a $s s"; else -> "il y a ${s / 60} min" }
}

// ---------------- Accueil ----------------

@Composable
fun HomeScreen(clims: Clims, email: String, onOpen: (String) -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    // Rafraîchit le texte « il y a … » chaque seconde
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Logo()
                val n = clims.devices.size
                val updated = if (tick >= 0) ago(clims.lastUpdate) else ""
                Text(
                    (if (n > 0) "$n clim${if (n > 1) "s" else ""} · " else "") + "mis à jour $updated",
                    color = Muted, fontSize = 13.sp,
                )
            }
            if (clims.loading) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Yellow, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else RoundIcon(G.Refresh, { clims.refresh() })
        }
        clims.error?.let { ErrorBox(it) }
        if (clims.devices.isEmpty() && clims.loading) {
            Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Yellow) }
        }
        clims.devices.forEach { d -> HomeCard(d, clims, onOpen) }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Déconnexion", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable { onLogout() }.padding(vertical = 8.dp))
            Text("Infos techniques", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable {
                val r = clims.raw
                if (r != null) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Koalair", r.toString(2)))
                    toast(ctx, "Infos techniques copiées")
                }
            }.padding(vertical = 8.dp))
        }
        Text("$email · v${BuildConfig.VERSION_NAME}", color = Muted.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ErrorBox(msg: String) {
    Text(
        msg, color = Red, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(Red.copy(alpha = 0.12f)).padding(12.dp),
    )
}

@Composable
fun HomeCard(d: Device, clims: Clims, onOpen: (String) -> Unit) {
    val s = d.status
    val on = s["t_power"] == "1"
    val mode = s["t_work_mode"]
    val accent by animateColorAsState(if (on) modeColor(mode) else Muted, tween(500), label = "acc")
    val glow by animateColorAsState(if (on) modeColor(mode).copy(alpha = 0.22f) else Card, tween(500), label = "glow")
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(Card)
            .background(Brush.linearGradient(listOf(glow, Color.Transparent, Color.Transparent)))
            .border(1.dp, if (on) accent.copy(alpha = 0.35f) else Line, shape)
            .clickable { onOpen(d.puid) }.padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Glyph(modeGlyph(mode), accent, 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(if (on) Ac.modeName(mode) else "Éteinte", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            PowerButton(on, modeColor(mode), 56.dp) { clims.send(d.puid, mapOf("t_power" to if (on) "0" else "1")) }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("CONSIGNE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Row(verticalAlignment = Alignment.Top) {
                    Text(s["t_temp"] ?: "--", color = if (on) accent else Soft.copy(alpha = 0.5f), fontSize = 54.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp)
                    Text("°C", color = if (on) accent else Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("PIÈCE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.Thermo, Soft, 18.dp)
                    Text("${s["f_temp_in"] ?: "?"}°", color = Soft, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (on) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(G.Fan, Ac.fanName(s["t_fan_speed"]), Soft, spin = true)
                if (s["t_up_down"] == "1") Pill(G.Swing, "Balayage", Soft)
                if (s["t_sleep"].let { it != null && it != "0" }) Pill(G.Moon, "Sommeil", Lavender)
                if (s["t_eco"] == "1") Pill(G.Leaf, "Éco", Teal)
                if (s["t_fan_mute"] == "1") Pill(G.Mute, "Silence", Soft)
                if (s["t_super"] == "1") Pill(G.Bolt, "Turbo", Yellow)
            }
        }
    }
}

// ---------------- Détail d'une clim ----------------

@Composable
fun DetailScreen(d: Device, clims: Clims, onBack: () -> Unit) {
    val s = d.status
    val on = s["t_power"] == "1"
    val mode = s["t_work_mode"]
    val accent by animateColorAsState(if (on) modeColor(mode) else Muted, tween(500), label = "dacc")
    val temp = s["t_temp"]?.toIntOrNull()?.takeIf { it > 0 }
    fun send(vararg p: Pair<String, String>) = clims.send(d.puid, p.toMap())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(G.Back, onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(if (on) Ac.modeName(mode) else "Éteinte", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Pill(G.Thermo, "Pièce ${s["f_temp_in"] ?: "?"}°", Soft)
        }
        clims.error?.let { ErrorBox(it) }

        // Cadran de température
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            TempDial(temp, on, accent, Modifier.size(270.dp)) { clims.setTemp(d.puid, it) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(G.Minus, { clims.setTemp(d.puid, (temp ?: Ac.MIN_TEMP) - 1) }, 58.dp, Card, Color.White, temp != null)
            PowerButton(on, modeColor(mode), 72.dp) { send("t_power" to if (on) "0" else "1") }
            RoundIcon(G.Plus, { clims.setTemp(d.puid, (temp ?: Ac.MIN_TEMP) + 1) }, 58.dp, Card, Color.White, temp != null)
        }

        SectionTitle("Mode")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ac.MODES.forEach { (v, label) ->
                Tile(label, modeGlyph(v), on && mode == v, modeColor(v), Modifier.weight(1f)) {
                    send("t_power" to "1", "t_work_mode" to v)
                }
            }
        }

        SectionTitle("Ventilation", "Le Sommeil et le Silence de la clim imposent la vitesse basse")
        FanSelector(s["t_fan_speed"], accent) { send("t_fan_speed" to it) }

        SectionTitle("Options")
        val turboOk = mode == "1" || mode == "2"
        val ecoOk = mode in listOf("0", "1", "2", "3")
        val muteOk = mode in listOf("0", "1", "2")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sw = s["t_up_down"] == "1"
            Tile("Balayage", G.Swing, sw, Ice, Modifier.weight(1f), if (sw) "Activé" else "Arrêt") { send("t_up_down" to if (sw) "0" else "1") }
            val eco = s["t_eco"] == "1"
            Tile("Éco", G.Leaf, eco, Teal, Modifier.weight(1f), if (eco) "Activé" else "Arrêt", ecoOk) {
                send("t_eco" to if (eco) "0" else "1", "t_super" to "0")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mute = s["t_fan_mute"] == "1"
            Tile("Silence", G.Mute, mute, Lavender, Modifier.weight(1f), if (mute) "Activé" else "Arrêt", muteOk) {
                send("t_fan_mute" to if (mute) "0" else "1", "t_super" to "0")
            }
            val turbo = s["t_super"] == "1"
            Tile("Turbo", G.Bolt, turbo, Yellow, Modifier.weight(1f), if (turbo) "Activé" else "Arrêt", turboOk) {
                if (turbo) send("t_super" to "0") else send("t_super" to "1", "t_eco" to "0", "t_fan_mute" to "0", "t_sleep" to "0")
            }
        }

        SectionTitle("Sommeil de la clim", "Courbe imposée par la clim : la température dérive de 1 à 2 °C, ventilation basse et arrêt automatique")
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Ac.SLEEPS.forEach { (v, label) ->
                val sel = (s["t_sleep"] ?: "0") == v
                val bg by animateColorAsState(if (sel) Lavender else Card, tween(300), label = "slp")
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg)
                        .clickable { if (v == "0") send("t_sleep" to "0") else send("t_sleep" to v, "t_super" to "0") }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (sel) Navy else Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }

        SectionTitle("Voyant de la clim")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Allumer", G.Bulb, false, Yellow, Modifier.weight(1f)) { send("t_dimmer" to "1") }
            Tile("Éteindre", G.Bulb, false, Muted, Modifier.weight(1f)) { send("t_dimmer" to "0") }
        }

        val errors = s.filter { it.key.startsWith("f_e_") && it.value != "0" }.keys
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ErrorBox("Défaut signalé par la clim : ${errors.joinToString()}")
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Auto + 5 barres de hauteur croissante. */
@Composable
fun FanSelector(value: String?, accent: Color, onPick: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(10.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val auto = value == "0"
        val abg by animateColorAsState(if (auto) accent else CardHi, tween(300), label = "fauto")
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(abg).clickable { onPick("0") }.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Glyph(G.Fan, if (auto) Navy else Soft, 22.dp, spin = auto)
            Text("Auto", color = if (auto) Navy else Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        val level = Ac.FANS.indexOfFirst { it.first == value }.takeIf { it > 0 } ?: 0
        Ac.FANS.drop(1).forEachIndexed { i, (v, _) ->
            val filled = !auto && level >= i + 1
            val c by animateColorAsState(if (filled) accent else CardHi, tween(250), label = "bar$i")
            Box(
                Modifier.weight(1f).height((18 + i * 8).dp).clip(RoundedCornerShape(8.dp)).background(c).clickable { onPick(v) },
                contentAlignment = Alignment.BottomCenter,
            ) { Text("${i + 1}", color = if (filled) Navy else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 2.dp)) }
        }
    }
}

// ---------------- Connexion ----------------

@Composable
fun LoginScreen(session: Session, onDone: () -> Unit) {
    var email by remember { mutableStateOf(session.email) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        focusedBorderColor = Yellow, unfocusedBorderColor = Line, focusedLabelColor = Yellow, unfocusedLabelColor = Muted,
        focusedContainerColor = Card, unfocusedContainerColor = Card, cursorColor = Yellow,
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(Card).border(1.dp, Line, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(G.Snow, Ice, 48.dp)
        }
        Spacer(Modifier.height(16.dp))
        Logo(42)
        Text("Tes clims, sans prise de tête", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            email, { email = it }, label = { Text("E-mail ConnectLife") }, singleLine = true, colors = colors,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Mot de passe") }, singleLine = true, colors = colors,
            shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = Red, fontSize = 14.sp) }
        Spacer(Modifier.height(20.dp))
        val ok = !busy && email.isNotBlank() && pass.isNotEmpty()
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(if (ok) Yellow else CardHi)
                .clickable(enabled = ok) {
                    busy = true; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { session.login(email, pass) } }
                        busy = false
                        r.onSuccess { onDone() }.onFailure { error = it.message ?: it.toString() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(color = Navy, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            else Text("Se connecter", color = if (ok) Navy else Muted, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
    }
}
