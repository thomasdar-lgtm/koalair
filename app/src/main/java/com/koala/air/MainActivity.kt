package com.koala.air

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

val Navy = Color(0xFF00071A)
val Card = Color(0xFF0E1A33)
val Yellow = Color(0xFFFFD23F)
val Ice = Color(0xFF7FD4FF)
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
        if (loggedIn) DevicesScreen(session) { session.logout(); loggedIn = false }
        else LoginScreen(session) { loggedIn = true }
    }
}

@Composable
fun Title(sub: String) {
    Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Koa", color = Yellow, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("lair", color = Ice, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(8.dp))
            Text("v${BuildConfig.VERSION_NAME} · test", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(sub, color = Muted, fontSize = 14.sp)
    }
}

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
        Title("Connexion avec le compte de l'appli ConnectLife")
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
                if (busy) CircularProgressIndicator(color = Navy, modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 3.dp)
                else Text("Se connecter", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(
                "Si le compte ConnectLife a été créé avec Google ou Apple, il faut d'abord lui définir un mot de passe dans l'appli ConnectLife.",
                color = Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun DevicesScreen(session: Session, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<Device>?>(null) }
    var raw by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf<String?>(null) }

    fun load() {
        busy = true; error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { session.call { ConnectLife.devices(it) } } }
            busy = false
            r.onSuccess { devices = it.first; raw = it.second }.onFailure { error = it.message ?: it.toString() }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Title(session.email)
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { load() }, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Navy),
            ) { Text(if (busy) "Chargement…" else "Actualiser", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                enabled = raw != null,
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Koalair", raw!!.toString(2)))
                    toast(ctx, "Infos copiées : colle-les dans la conversation avec Claude")
                },
            ) { Text("Copier les infos", color = Ice) }
        }
        error?.let { Text(it, color = Red, fontSize = 14.sp, modifier = Modifier.padding(20.dp)) }
        devices?.let { list ->
            if (list.isEmpty()) Text("Aucun appareil sur ce compte", color = Muted, modifier = Modifier.padding(20.dp))
            list.forEach { d ->
                DeviceCard(d, sending == d.puid) { on ->
                    sending = d.puid; error = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { session.call { ConnectLife.setProperties(it, d.puid, mapOf("t_power" to if (on) "1" else "0")) } }
                        }
                        r.onSuccess {
                            toast(ctx, if (on) "Commande envoyée : allumer" else "Commande envoyée : éteindre")
                            delay(4000)
                            load()
                        }.onFailure { error = it.message ?: it.toString() }
                        sending = null
                    }
                }
            }
        }
        TextButton(onClick = onLogout, modifier = Modifier.padding(12.dp)) { Text("Se déconnecter", color = Muted) }
    }
}

@Composable
fun DeviceCard(d: Device, sending: Boolean, onPower: (Boolean) -> Unit) {
    val power = d.status["t_power"]
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp)
    ) {
        Text(d.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("Code : ${d.typeCode}-${d.featureCode}", color = Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("${d.typeName} · ${d.featureName} · offlineState=${d.online}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (power) { "1" -> "Allumée"; "0" -> "Éteinte"; null -> "État inconnu (pas de t_power)"; else -> "t_power = $power" },
                color = if (power == "1") Ice else Muted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (sending) CircularProgressIndicator(color = Yellow, modifier = Modifier.width(28.dp).height(28.dp))
            else {
                Button(
                    onClick = { onPower(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Ice, contentColor = Navy),
                ) { Text("Allumer", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onPower(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Muted, contentColor = Navy),
                ) { Text("Éteindre", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Toutes les valeurs (${d.status.size})", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        d.status.entries.sortedBy { it.key }.forEach { (k, v) ->
            Row {
                Text(k, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(v, color = Ice, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}
