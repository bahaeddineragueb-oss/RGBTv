package com.rgbtv.app.data

import android.content.Context
import android.content.SharedPreferences

/** Cloud account state: server URL, JWT, profile id mapping. Local-first: app works without it. */
object CloudStore {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("rgbtv_cloud", Context.MODE_PRIVATE)
    }

    fun url(): String = prefs.getString("url", "") ?: ""
    fun setUrl(u: String) = prefs.edit().putString("url", u.trim().trimEnd('/')).apply()

    fun token(): String = prefs.getString("token", "") ?: ""
    fun email(): String = prefs.getString("email", "") ?: ""
    fun isAdmin(): Boolean = prefs.getBoolean("admin", false)

    val loggedIn: Boolean get() = url().isNotEmpty() && token().isNotEmpty()

    fun login(url: String, token: String, email: String, admin: Boolean) {
        prefs.edit().putString("url", url.trim().trimEnd('/'))
            .putString("token", token).putString("email", email)
            .putBoolean("admin", admin).apply()
    }

    fun logout() {
        prefs.edit().remove("token").remove("email").remove("admin").remove("lastSync").apply()
    }

    fun lastSync(): Long = prefs.getLong("lastSync", 0)
    fun setLastSync(ts: Long) = prefs.edit().putLong("lastSync", ts).apply()

    /** local account uuid <-> server profile id */
    fun serverId(localId: String): Int = prefs.getInt("srv_$localId", -1)
    fun setServerId(localId: String, srv: Int) = prefs.edit().putInt("srv_$localId", srv).apply()
    fun localId(srv: Int): String? = prefs.getString("loc_$srv", null)
    fun setLocalId(srv: Int, localId: String) = prefs.edit().putString("loc_$srv", localId).apply()
}
