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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Navy = Color(0xFF00071A)
val Card = Color(0xFF0E1A33)
val CardHi = Color(0xFF1A2A4D)
val Yellow = Color(0xFFFFD23F)
val Ice = Color(0xFF7FD4FF)
val Heat = Color(0xFFFF8A3D)
val Red = Color(0xFFE63946)
val Muted = Color(0xFF8C9AB8)

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val session = Session(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, background = Navy, surface = Card)) {
                App(session)
            }
        }
    }
}

@Composable
fun App(session: Session) {
    var loggedIn by remember { mutableStateOf(session.loggedIn) }
    val upd = remember { UpdateState() }
    LaunchedEffect(Unit) { upd.info = runCatching { Updater.check() }.getOrNull() }
    Column(Modifier.fillMaxSize().background(Navy).safeDrawingPadding()) {
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
    val d = open?.let { clims.device(it) }
    if (d != null) DetailScreen(d, clims) { open = null }
    else HomeScreen(clims, session.email, onOpen = { open = it }, onLogout = onLogout)
}

// ---------------- Éléments communs ----------------

@Composable
fun Header(title: String, sub: String? = null, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 34.sp)
            }
        } else Spacer(Modifier.width(12.dp))
        Column {
            if (onBack == null) Row(verticalAlignment = Alignment.Bottom) {
                Text("Koa", color = Yellow, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("lair", color = Ice, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text("v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            } else Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            sub?.let { Text(it, color = Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, enabled: Boolean = true, color: Color = Yellow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) color else CardHi)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            color = when { selected -> Navy; enabled -> Color.White; else -> Muted.copy(alpha = 0.5f) },
        )
    }
}

@Composable
fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)
    ) {
        Text(title, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        hint?.let { Text(it, color = Muted, fontSize = 11.sp) }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

fun modeColor(mode: String?) = when (mode) { "1" -> Heat; "2" -> Ice; else -> Yellow }

// ---------------- Accueil ----------------

@Composable
fun HomeScreen(clims: Clims, email: String, onOpen: (String) -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Koalair")
        clims.error?.let { Text(it, color = Red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (clims.devices.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                if (clims.loading) CircularProgressIndicator(color = Yellow)
                else Text("Aucune clim trouvée", color = Muted)
            }
        }
        clims.devices.forEach { d -> HomeCard(d, clims, onOpen) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { clims.refresh() }) { Text(if (clims.loading) "Actualisation…" else "Actualiser", color = Ice) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val r = clims.raw ?: return@TextButton
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Koalair", r.toString(2)))
                toast(ctx, "Infos techniques copiées")
            }) { Text("Copier les infos", color = Muted, fontSize = 12.sp) }
        }
        TextButton(onClick = onLogout, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("Se déconnecter ($email)", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeCard(d: Device, clims: Clims, onOpen: (String) -> Unit) {
    val on = d.status["t_power"] == "1"
    val mode = d.status["t_work_mode"]
    val accent = if (on) modeColor(mode) else Muted
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)).background(Card)
            .clickable { onOpen(d.puid) }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Pièce : ${d.status["f_temp_in"] ?: "?"} °C", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            if (on) {
                Text("${Ac.modeName(mode)} · ${d.status["t_temp"]} °C", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(Ac.fanName(d.status["t_fan_speed"]) + extras(d), color = Muted, fontSize = 13.sp)
            } else Text("Éteinte", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        PowerButton(on, accent) { clims.send(d.puid, mapOf("t_power" to if (on) "0" else "1")) }
    }
}

fun extras(d: Device): String = buildList {
    if (d.status["t_sleep"].let { it != null && it != "0" }) add("Sommeil")
    if (d.status["t_eco"] == "1") add("Éco")
    if (d.status["t_fan_mute"] == "1") add("Silence")
    if (d.status["t_super"] == "1") add("Turbo")
}.joinToString("") { " · $it" }

@Composable
fun PowerButton(on: Boolean, accent: Color, size: Int = 64, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (on) accent else CardHi)
            .border(2.dp, if (on) accent else Muted, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text("⏻", color = if (on) Navy else Muted, fontSize = (size / 2.3).sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Détail d'une clim ----------------

@Composable
fun DetailScreen(d: Device, clims: Clims, onBack: () -> Unit) {
    val s = d.status
    val on = s["t_power"] == "1"
    val mode = s["t_work_mode"]
    val accent = if (on) modeColor(mode) else Muted
    val temp = s["t_temp"]?.toIntOrNull()
    fun send(vararg p: Pair<String, String>) = clims.send(d.puid, p.toMap())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(d.name, "Température de la pièce : ${s["f_temp_in"] ?: "?"} °C", onBack)
        clims.error?.let { Text(it, color = Red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }

        // Marche + consigne
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(20.dp)).background(Card).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerButton(on, accent, 72) { send("t_power" to if (on) "0" else "1") }
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(52.dp).clip(CircleShape).background(CardHi).clickable(enabled = temp != null) { clims.setTemp(d.puid, (temp ?: 0) - 1) },
                contentAlignment = Alignment.Center) { Text("−", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Text(
                if (temp == null || temp == 0) "--" else "$temp°",
                color = if (on) accent else Muted, fontSize = 48.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(52.dp).clip(CircleShape).background(CardHi).clickable(enabled = temp != null) { clims.setTemp(d.puid, (temp ?: 0) + 1) },
                contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        }

        Section("Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ac.MODES.forEach { (v, label) ->
                    Chip(label, mode == v, color = modeColor(v), modifier = Modifier.weight(1f)) {
                        send("t_power" to "1", "t_work_mode" to v)
                    }
                }
            }
        }

        Section("Ventilation", "Le mode Sommeil et le mode Silence imposent la vitesse basse") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ac.FANS.forEach { (v, label) ->
                    Chip(label, s["t_fan_speed"] == v, modifier = Modifier.weight(1f)) { send("t_fan_speed" to v) }
                }
            }
        }

        Section("Balayage vertical") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Arrêt", s["t_up_down"] == "0", modifier = Modifier.weight(1f)) { send("t_up_down" to "0") }
                Chip("Marche", s["t_up_down"] == "1", modifier = Modifier.weight(1f)) { send("t_up_down" to "1") }
            }
        }

        Section("Options") {
            val turboOk = mode == "1" || mode == "2"
            val ecoOk = mode in listOf("0", "1", "2", "3")
            val muteOk = mode in listOf("0", "1", "2")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Éco", s["t_eco"] == "1", enabled = ecoOk, modifier = Modifier.weight(1f)) { send("t_eco" to if (s["t_eco"] == "1") "0" else "1", "t_super" to "0") }
                Chip("Silence", s["t_fan_mute"] == "1", enabled = muteOk, modifier = Modifier.weight(1f)) { send("t_fan_mute" to if (s["t_fan_mute"] == "1") "0" else "1", "t_super" to "0") }
                Chip("Turbo", s["t_super"] == "1", enabled = turboOk, modifier = Modifier.weight(1f)) {
                    if (s["t_super"] == "1") send("t_super" to "0")
                    else send("t_super" to "1", "t_eco" to "0", "t_fan_mute" to "0", "t_sleep" to "0")
                }
            }
        }

        Section("Sommeil de la clim", "Courbe imposée par la clim : la température dérive de 1 à 2 °C, ventilation basse et arrêt automatique") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ac.SLEEPS.forEach { (v, label) ->
                    Chip(label, (s["t_sleep"] ?: "0") == v, modifier = Modifier.weight(1f)) {
                        if (v == "0") send("t_sleep" to "0") else send("t_sleep" to v, "t_super" to "0")
                    }
                }
            }
        }

        Section("Voyant de la clim") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Allumer", false, modifier = Modifier.weight(1f)) { send("t_dimmer" to "1") }
                Chip("Éteindre", false, modifier = Modifier.weight(1f)) { send("t_dimmer" to "0") }
            }
        }

        val errors = s.filter { it.key.startsWith("f_e_") && it.value != "0" }.keys
        if (errors.isNotEmpty()) Text("Défaut signalé par la clim : ${errors.joinToString()}", color = Red, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
        Spacer(Modifier.height(24.dp))
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
        focusedBorderColor = Yellow, unfocusedBorderColor = Muted, focusedLabelColor = Yellow, unfocusedLabelColor = Muted,
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Header("Koalair", "Connexion avec le compte de l'appli ConnectLife")
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                email, { email = it }, label = { Text("E-mail") }, singleLine = true, colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Mot de passe") }, singleLine = true, colors = colors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Red, fontSize = 14.sp) }
            Button(
                enabled = !busy && email.isNotBlank() && pass.isNotEmpty(),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { session.login(email, pass) } }
                        busy = false
                        r.onSuccess { onDone() }.onFailure { error = it.message ?: it.toString() }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(color = Navy, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                else Text("Se connecter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
