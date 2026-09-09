package com.rgbtv.app.repo

import android.content.Context
import com.rgbtv.app.data.FavItem
import com.rgbtv.app.data.HistItem
import com.rgbtv.app.data.Pos
import com.rgbtv.app.data.Store
import com.rgbtv.app.data.db.AppDb
import com.rgbtv.app.data.db.FavEntity
import com.rgbtv.app.data.db.HistEntity
import com.rgbtv.app.data.db.PosEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Room-backed library: favorites, history, resume positions. Replaces Store JSON lists. */
object Library {
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /* ---- favorites ---- */
    suspend fun favs(ctx: Context, acc: String): List<FavItem> =
        AppDb.get(ctx).favs().list(acc).map {
            FavItem(it.type, it.itemId, it.name, it.img, it.data)
        }

    suspend fun isFav(ctx: Context, acc: String, type: String, id: String): Boolean =
        AppDb.get(ctx).favs().exists(acc, type, id) > 0

    /** Returns true if the item is now a favorite. */
    suspend fun toggleFav(ctx: Context, acc: String, item: FavItem): Boolean {
        val db = AppDb.get(ctx).favs()
        val now: Boolean
        if (db.exists(acc, item.type, item.id) > 0) {
            db.delete(acc, item.type, item.id)
            now = false
        } else {
            db.upsert(FavEntity(acc, item.type, item.id, item.name, item.img, item.data, System.currentTimeMillis()))
            now = true
        }
        Sync.pushSoon(ctx)
        return now
    }

    /* ---- history ---- */
    suspend fun history(ctx: Context, acc: String): List<HistItem> =
        AppDb.get(ctx).hist().list(acc).map {
            HistItem(it.type, it.itemId, it.name, it.img, it.data, it.at)
        }

    suspend fun pushHistory(ctx: Context, acc: String, item: HistItem) {
        val db = AppDb.get(ctx).hist()
        db.upsert(HistEntity(acc, item.type, item.id, item.name, item.img, item.data, System.currentTimeMillis()))
        db.trim(acc)
        Sync.pushSoon(ctx)
    }

    suspend fun clearHistory(ctx: Context, acc: String) {
        AppDb.get(ctx).hist().clearAcc(acc)
    }

    /* ---- positions ---- */
    suspend fun getPos(ctx: Context, acc: String, key: String): Pos? {
        val e = AppDb.get(ctx).pos().get(acc, key) ?: return null
        return Pos(e.pos, e.dur, e.at)
    }

    suspend fun setPos(ctx: Context, acc: String, key: String, pos: Long, dur: Long) {
        val db = AppDb.get(ctx).pos()
        if (dur > 0 && pos.toDouble() / dur > 0.96) db.delete(acc, key)
        else db.upsert(PosEntity(acc, key, pos, dur, System.currentTimeMillis()))
        Sync.pushSoon(ctx)
    }

    suspend fun clearPos(ctx: Context, acc: String, key: String) {
        AppDb.get(ctx).pos().delete(acc, key)
    }

    fun clearAccount(ctx: Context, acc: String) {
        io.launch {
            val db = AppDb.get(ctx)
            db.favs().clearAcc(acc)
            db.hist().clearAcc(acc)
            db.pos().clearAcc(acc)
        }
    }

    /** One-time copy of legacy Store lists into Room. */
    fun migrateIfNeeded(ctx: Context) {
        io.launch {
            try {
                val p = ctx.getSharedPreferences("rgbtv", Context.MODE_PRIVATE)
                if (p.getBoolean("room_migrated", false)) return@launch
                val db = AppDb.get(ctx)
                for (a in Store.accounts()) {
                    for (f in Store.favs(a.id)) {
                        db.favs().upsert(FavEntity(a.id, f.type, f.id, f.name, f.img, f.data, System.currentTimeMillis()))
                    }
                    for (h in Store.history(a.id)) {
                        db.hist().upsert(HistEntity(a.id, h.type, h.id, h.name, h.img, h.data, h.at))
                    }
                }
                p.edit().putBoolean("room_migrated", true).apply()
            } catch (e: Exception) { }
        }
    }
}
