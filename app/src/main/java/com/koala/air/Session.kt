package com.koala.air

import android.content.Context

/** Identifiants et jetons, gardés dans le stockage privé de l'appli (non sauvegardé). */
class Session(ctx: Context) {
    private val p = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)

    val email: String get() = p.getString("email", "") ?: ""
    private val password: String get() = p.getString("password", "") ?: ""
    val loggedIn: Boolean get() = email.isNotEmpty() && password.isNotEmpty()

    private var tokens: Tokens?
        get() {
            val a = p.getString("access", null) ?: return null
            return Tokens(a, p.getLong("expires", 0), p.getString("refresh", null))
        }
        set(t) {
            p.edit().apply {
                if (t == null) { remove("access"); remove("expires"); remove("refresh") }
                else { putString("access", t.access); putLong("expires", t.expiresAt); putString("refresh", t.refresh) }
            }.apply()
        }

    /** À appeler hors du fil principal. */
    fun login(email: String, password: String) {
        val t = ConnectLife.login(email.trim(), password)
        p.edit().putString("email", email.trim()).putString("password", password).apply()
        tokens = t
    }

    fun logout() { p.edit().clear().apply() }

    private fun token(): String {
        val t = tokens
        if (t != null && t.expiresAt > System.currentTimeMillis()) return t.access
        if (t?.refresh != null) {
            runCatching { ConnectLife.refresh(t) }.getOrNull()?.let { tokens = it; return it.access }
        }
        val n = ConnectLife.login(email, password)
        tokens = n
        return n.access
    }

    /** Exécute un appel ; si le jeton est refusé, se reconnecte une fois et recommence. */
    fun <T> call(block: (String) -> T): T = try {
        block(token())
    } catch (e: Exception) {
        if (!ConnectLife.isInvalidToken(e)) throw e
        tokens = null
        val n = ConnectLife.login(email, password)
        tokens = n
        block(n.access)
    }
}
