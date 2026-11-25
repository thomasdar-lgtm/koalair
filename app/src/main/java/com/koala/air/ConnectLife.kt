package com.koala.air

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

/** Erreur lisible renvoyée à l'écran. */
class ClError(msg: String) : Exception(msg)

data class Tokens(val access: String, val expiresAt: Long, val refresh: String?)

data class Device(
    val puid: String,
    val name: String,
    val typeCode: String,
    val featureCode: String,
    val typeName: String,
    val featureName: String,
    val online: String,
    val status: Map<String, String>,
    val raw: JSONObject,
)

/**
 * Client du cloud ConnectLife (même échange que l'appli officielle).
 * Repris de la bibliothèque Python open source « connectlife » (oyvindwe), v0.15.0.
 */
object ConnectLife {
    private const val GIGYA_KEY = "4_yhTWQmHFpZkQZDSV1uV-_A"
    private const val LOGIN_URL = "https://accounts.eu1.gigya.com/accounts.login"
    private const val JWT_URL = "https://accounts.eu1.gigya.com/accounts.getJWT"
    private const val AUTHORIZE_URL = "https://oauth.hijuconn.com/oauth/authorize"
    private const val TOKEN_URL = "https://oauth.hijuconn.com/oauth/token"
    private const val CLIENT_ID = "5065059336212"
    private const val CLIENT_SECRET = "07swfKgvJhC3ydOUS9YV_SwVz0i4LKqlOLGNUukYHVMsJRF1b-iWeUGcNlXyYCeK"
    private const val REDIRECT_URI = "https://api.connectlife.io/swagger/oauth2-redirect.html"

    private const val GATEWAY = "https://clife-eu-gateway.hijuconn.com"
    const val DEVICE_LIST_URL = "$GATEWAY/clife-svc/pu/get_device_status_list"
    const val SET_URL = "$GATEWAY/device/pu/property/set"
    private const val APP_ID = "47110565134383"
    private const val APP_SECRET = "yOzhz6junYno-nmULM3Wr7PU_dpSZN22ZdluvVWZ4uW5ZwwG8fIGCHTbrhcnU-iv"
    private const val SIGN_SUFFIX = "D9519A4B756946F081B7BB5B5E8D1197"
    private const val USER_AGENT = "connectlife/0.15.0"
    private const val PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyyWrNG6q475HIHu7sMVu" +
        "vHof6vlgPeixmxa4EL/UsvVvHPz33NnWoQetQqit9TBNzUjMXw0KlY9PXM4iqHUU" +
        "U+dSyNDq1jZWIiJ2C2FccppswJtIKL3NRMFvT9PFh6NlP/4FUcQKojgKFbF7Kacc" +
        "JPKYHlwaO7qgoIjLxAHlSOXGpucJcOkPzT2EqsSVnW8sn8kenvNmghXDayhgxsh6" +
        "AyxK4kehJplEnmX/iYCfNoFXknGcLqFWYccgBz3fybvx30C/0IgU1980L8QsUAv5" +
        "esZmN8ugnbRgLRxKRlkQQLxQAiZMZdKTAx665YflT3YMHJvEFE8c2XFgoxHzSMc4" +
        "BwIDAQAB"

    private const val INVALID_TOKEN = 100026
    private const val RANDSTR_FAILED = 101005

    // ---------- Connexion ----------

    fun login(email: String, password: String): Tokens {
        val login = postForm(LOGIN_URL, mapOf("loginID" to email, "password" to password, "APIKey" to GIGYA_KEY))
        val code = login.optInt("errorCode", 0)
        if (code != 0 || login.has("errorMessage")) {
            val msg = login.optString("errorMessage", "")
            throw ClError(
                when (code) {
                    403042 -> "E-mail ou mot de passe incorrect"
                    206001 -> "Accepte d'abord les nouvelles conditions dans l'appli ConnectLife"
                    else -> "Connexion refusée ($code) $msg".trim()
                }
            )
        }
        val uid = login.optString("UID").ifEmpty { throw ClError("Réponse de connexion incomplète (UID)") }
        val cookie = login.optJSONObject("sessionInfo")?.optString("cookieValue").orEmpty()
            .ifEmpty { throw ClError("Réponse de connexion incomplète (session)") }

        val jwt = postForm(JWT_URL, mapOf("APIKey" to GIGYA_KEY, "login_token" to cookie))
        val idToken = jwt.optString("id_token").ifEmpty { throw ClError("Jeton JWT manquant") }

        val auth = postJson(
            AUTHORIZE_URL,
            JSONObject()
                .put("client_id", CLIENT_ID)
                .put("redirect_uri", REDIRECT_URI)
                .put("idToken", idToken)
                .put("response_type", "code")
                .put("thirdType", "CDC")
                .put("thirdClientId", uid),
        )
        val authCode = auth.optString("code").ifEmpty { throw ClError("Code d'autorisation manquant") }

        return tokenRequest(
            mapOf(
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "redirect_uri" to REDIRECT_URI,
                "grant_type" to "authorization_code",
                "code" to authCode,
            )
        )
    }

    fun refresh(t: Tokens): Tokens = tokenRequest(
        mapOf(
            "client_id" to CLIENT_ID,
            "client_secret" to CLIENT_SECRET,
            "redirect_uri" to REDIRECT_URI,
            "grant_type" to "refresh_token",
            "refresh_token" to (t.refresh ?: throw ClError("Pas de jeton de rafraîchissement")),
        )
    )

    private fun tokenRequest(form: Map<String, String>): Tokens {
        val o = postForm(TOKEN_URL, form)
        val access = o.optString("access_token").ifEmpty { throw ClError("Jeton d'accès manquant") }
        val expiresIn = o.optLong("expires_in", 3600)
        return Tokens(access, System.currentTimeMillis() + (expiresIn - 90) * 1000, o.optString("refresh_token").ifEmpty { null })
    }

    // ---------- Appareils ----------

    /** Renvoie la liste des appareils et le JSON brut complet. */
    fun devices(token: String): Pair<List<Device>, JSONObject> {
        val r = gateway(DEVICE_LIST_URL, token, JSONObject(), get = true)
        val list = r.optJSONArray("deviceList") ?: throw ClError("Réponse inattendue : pas de liste d'appareils")
        return parseDevices(list) to r
    }

    fun setProperties(token: String, puid: String, props: Map<String, String>) {
        val p = JSONObject()
        props.forEach { (k, v) -> p.put(k, v) }
        gateway(SET_URL, token, JSONObject().put("puid", puid).put("properties", p), get = false)
    }

    fun parseDevices(list: JSONArray): List<Device> = (0 until list.length()).map { i ->
        val d = list.getJSONObject(i)
        val st = d.optJSONObject("statusList")
        val status = linkedMapOf<String, String>()
        st?.keys()?.forEach { k -> status[k] = st.opt(k)?.toString() ?: "" }
        Device(
            puid = d.optString("puid"),
            name = d.optString("deviceNickName").ifEmpty { d.optString("deviceTypeName", "Appareil") },
            typeCode = d.optString("deviceTypeCode"),
            featureCode = d.optString("deviceFeatureCode"),
            typeName = d.optString("deviceTypeName"),
            featureName = d.optString("deviceFeatureName"),
            online = d.opt("offlineState")?.toString() ?: "?",
            status = status,
            raw = d,
        )
    }

    // ---------- Passerelle signée ----------

    class GatewayError(val code: Int, msg: String) : Exception(msg)

    private fun gateway(url: String, token: String, payload: JSONObject, get: Boolean, retry: Boolean = true): JSONObject {
        val data = signedRequest(token, payload)
        val body = if (get) {
            val q = data.keys().asSequence().joinToString("&") { k -> enc(k) + "=" + enc(data.get(k).toString()) }
            http("$url?$q", "GET", null, null)
        } else http(url, "POST", data.toString(), "application/json")
        val resp = body.optJSONObject("response") ?: throw ClError("Réponse inattendue de la passerelle")
        val rc = resp.opt("resultCode")
        if (rc == null || rc.toString() == "0") return resp
        val ec = resp.optInt("errorCode", -1)
        if (retry && ec == RANDSTR_FAILED) return gateway(url, token, payload, get, retry = false)
        throw GatewayError(ec, "Erreur passerelle $ec : ${resp.optString("errorDesc", "inconnue")}")
    }

    fun isInvalidToken(e: Throwable) = e is GatewayError && e.code == INVALID_TOKEN

    fun signedRequest(token: String, payload: JSONObject, now: Long = System.currentTimeMillis(), rand: String = randHex()): JSONObject {
        val d = JSONObject()
            .put("accessToken", token)
            .put("appId", APP_ID)
            .put("appSecret", APP_SECRET)
            .put("languageId", "12")
            .put("randStr", rand)
            .put("timeStamp", now.toString())
            .put("timezone", "1.0")
            .put("version", "5.0")
        payload.keys().forEach { d.put(it, payload.get(it)) }
        d.put("sign", sign(d))
        return d
    }

    /** Chaîne signée : clés triées, « clé=valeur » joints par « & », objets en JSON compact. */
    fun canonical(d: JSONObject): String =
        d.keys().asSequence().filter { it != "sign" }.sorted().joinToString("&") { k ->
            val v = d.get(k)
            "$k=" + when (v) {
                is JSONObject, is JSONArray -> compactJson(v)
                else -> v.toString()
            }
        }

    /** JSON compact identique à json.dumps(separators=(",", ":")) de Python (ordre conservé, sans échapper « / »). */
    fun compactJson(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().joinToString(",", "{", "}") { k -> quote(k) + ":" + compactJson(v.get(k)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { compactJson(v.get(it)) }
        is String -> quote(v)
        is Boolean, is Int, is Long -> v.toString()
        is Number -> v.toString()
        else -> quote(v.toString())
    }

    private fun quote(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\r' -> b.append("\\r")
            c == '\t' -> b.append("\\t")
            c == '\b' -> b.append("\\b")
            c == '\u000c' -> b.append("\\f")
            c.code < 0x20 || c.code > 0x7f -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    fun sign(d: JSONObject): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((canonical(d) + SIGN_SUFFIX).toByteArray())
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)))
        val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(c.doFinal(digest))
    }

    private fun randHex(): String {
        val b = ByteArray(16); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    // ---------- HTTP ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun postForm(url: String, form: Map<String, String>): JSONObject =
        http(url, "POST", form.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }, "application/x-www-form-urlencoded")

    private fun postJson(url: String, o: JSONObject): JSONObject = http(url, "POST", o.toString(), "application/json")

    private fun http(url: String, method: String, body: String?, type: String?): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.requestMethod = method
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Accept", "application/json")
        try {
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "$type; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code != 200) throw ClError("Serveur ${URL(url).host} : HTTP $code ${text.take(150)}")
            return runCatching { JSONObject(text) }.getOrElse { throw ClError("Réponse illisible de ${URL(url).host}") }
        } finally {
            c.disconnect()
        }
    }
}
