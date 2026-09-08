package com.rgbtv.app.data

import android.content.Context
import android.content.SharedPreferences
import com.rgbtv.app.ui.Kit
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences-backed store: accounts, settings, favs, history, positions, locks, device id. */
object Store {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("rgbtv", Context.MODE_PRIVATE)
    }

    /* ---- device identity (Stalker) ---- */
    fun deviceMac(): String = device().optString("mac")
    fun deviceSn(): String = device().optString("sn")
    fun deviceId(): String = device().optString("d1")
    fun deviceId2(): String = device().optString("d2")

    private fun device(): JSONObject {
        val raw = prefs.getString("device", null)
        if (!raw.isNullOrEmpty()) return try { JSONObject(raw) } catch (e: Exception) { newDevice() }
        return newDevice()
    }
    private fun newDevice(): JSONObject {
        val o = JSONObject().put("mac", Kit.randomMac()).put("sn", Kit.sha1(Kit.uuid()).substring(0, 13))
            .put("d1", Kit.sha1(Kit.uuid())).put("d2", Kit.sha1(Kit.uuid()))
        prefs.edit().putString("device", o.toString()).apply()
        return o
    }

    /* ---- accounts ---- */
    fun accounts(): MutableList<Account> {
        val out = mutableListOf<Account>()
        val raw = prefs.getString("accounts", null) ?: return out
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) out.add(Account.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) { }
        return out
    }
    fun saveAccounts(list: List<Account>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString("accounts", a.toString()).apply()
    }
    fun addAccount(a: Account): Account {
        if (a.id.isEmpty()) a.id = Kit.uuid()
        a.createdAt = System.currentTimeMillis()
        val l = accounts(); l.add(a); saveAccounts(l)
        return a
    }
    fun updateAccount(a: Account) = saveAccounts(accounts().map { if (it.id == a.id) a else it })
    fun removeAccount(id: String) {
        saveAccounts(accounts().filter { it.id != id })
        prefs.edit().apply {
            remove("fav_$id"); remove("hist_$id"); remove("pos_$id"); remove("lock_$id"); remove("sess_$id")
            if (prefs.getString("lastAccount", null) == id) remove("lastAccount")
            apply()
        }
    }
    fun getAccount(id: String?): Account? = accounts().firstOrNull { it.id == id }
    fun lastAccount(): String? = prefs.getString("lastAccount", null)
    fun setLastAccount(id: String?) = prefs.edit().putString("lastAccount", id).apply()
    fun lastSession(id: String): SessionSummary? = SessionSummary.fromJson(prefs.getString("sess_$id", null))
    fun setLastSession(id: String, s: SessionSummary) =
        prefs.edit().putString("sess_$id", s.toJson().toString()).apply()

    /* ---- settings ---- */
    fun settings(): Settings = Settings.fromJson(prefs.getString("settings", null))
    fun updateSettings(mut: (Settings) -> Unit) {
        val s = settings(); mut(s)
        prefs.edit().putString("settings", s.toJson().toString()).apply()
    }

    /* ---- favorites ---- */
    fun favs(accId: String): MutableList<FavItem> = FavItem.fromArray(prefs.getString("fav_$accId", null))
    fun isFav(accId: String, type: String, id: String): Boolean =
        favs(accId).any { it.type == type && it.id == id }
    /** Returns true if the item is now a favorite. */
    fun toggleFav(accId: String, item: FavItem): Boolean {
        val l = favs(accId)
        val idx = l.indexOfFirst { it.type == item.type && it.id == item.id }
        val now: Boolean
        if (idx >= 0) { l.removeAt(idx); now = false } else { l.add(0, item); now = true }
        val a = JSONArray()
        l.take(300).forEach { a.put(it.toJson()) }
        prefs.edit().putString("fav_$accId", a.toString()).apply()
        return now
    }

    /* ---- history ---- */
    fun history(accId: String): MutableList<HistItem> = HistItem.fromArray(prefs.getString("hist_$accId", null))
    fun pushHistory(accId: String, item: HistItem) {
        val l = history(accId).filter { !(it.type == item.type && it.id == item.id) }.toMutableList()
        l.add(0, item.copy(at = System.currentTimeMillis()))
        val a = JSONArray()
        l.take(60).forEach { a.put(it.toJson()) }
        prefs.edit().putString("hist_$accId", a.toString()).apply()
    }
    fun clearHistory(accId: String) { prefs.edit().remove("hist_$accId").apply() }

    /* ---- resume positions ---- */
    private fun posMap(accId: String): JSONObject {
        return try { JSONObject(prefs.getString("pos_$accId", null) ?: "{}") } catch (e: Exception) { JSONObject() }
    }
    fun getPos(accId: String, key: String): Pos? {
        val o = posMap(accId).optJSONObject(key) ?: return null
        return Pos(o.optLong("pos"), o.optLong("dur"), o.optLong("at"))
    }
    fun setPos(accId: String, key: String, pos: Long, dur: Long) {
        val m = posMap(accId)
        if (dur > 0 && pos.toDouble() / dur > 0.96) m.remove(key)
        else m.put(key, JSONObject().put("pos", pos).put("dur", dur).put("at", System.currentTimeMillis()))
        // cap 200 entries (drop oldest)
        val keys = m.keys().asSequence().toList()
        if (keys.size > 200) {
            val oldest = keys.minByOrNull { m.optJSONObject(it)?.optLong("at") ?: 0 }
            if (oldest != null) m.remove(oldest)
        }
        prefs.edit().putString("pos_$accId", m.toString()).apply()
    }
    fun clearPos(accId: String, key: String) {
        val m = posMap(accId); m.remove(key)
        prefs.edit().putString("pos_$accId", m.toString()).apply()
    }

    /* ---- parental locks ("type:id") ---- */
    fun locks(accId: String): MutableSet<String> =
        prefs.getStringSet("lock_$accId", emptySet())?.toMutableSet() ?: mutableSetOf()
    fun isLocked(accId: String, key: String): Boolean = locks(accId).contains(key)
    fun toggleLock(accId: String, key: String): Boolean {
        val s = locks(accId)
        val now = if (s.contains(key)) { s.remove(key); false } else { s.add(key); true }
        prefs.edit().putStringSet("lock_$accId", s).apply()
        return now
    }
    fun clearLocks(accId: String) { prefs.edit().remove("lock_$accId").apply() }

    /* ---- finished episodes ---- */
    fun doneSet(accId: String): MutableSet<String> =
        prefs.getStringSet("done_$accId", emptySet())?.toMutableSet() ?: mutableSetOf()
    fun isDone(accId: String, key: String): Boolean = doneSet(accId).contains(key)
    fun markDone(accId: String, key: String) {
        val s = doneSet(accId)
        if (s.size > 2000) s.clear()
        s.add(key)
        prefs.edit().putStringSet("done_$accId", s).apply()
    }

    /* ---- PIN ---- */
    fun pin(): String? = prefs.getString("pin", null)
    fun setPin(p: String?) = prefs.edit().putString("pin", p).apply()
}
