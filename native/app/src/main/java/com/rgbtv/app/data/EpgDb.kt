package com.rgbtv.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ProgramRow(val ch: String, val start: Long, val stop: Long, val title: String, val descr: String)

/** Per-account XMLTV database. Indexed for instant now/next lookups. */
class EpgDb(ctx: Context, name: String) : SQLiteOpenHelper(ctx.applicationContext, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS programs (ch TEXT, start INTEGER, stop INTEGER, title TEXT, descr TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ch_start ON programs (ch, start)")
        db.execSQL("CREATE TABLE IF NOT EXISTS names (norm TEXT, xmlid TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_norm ON names (norm)")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        db.execSQL("DROP TABLE IF EXISTS programs")
        db.execSQL("DROP TABLE IF EXISTS names")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete("programs", null, null)
        db.delete("names", null, null)
        db.delete("meta", null, null)
    }

    fun setMeta(k: String, v: String) {
        val cv = ContentValues()
        cv.put("k", k); cv.put("v", v)
        writableDatabase.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMeta(k: String): String? {
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(k)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun count(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM programs", null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    fun bulkInsertPrograms(rows: List<ProgramRow>) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement("INSERT INTO programs (ch,start,stop,title,descr) VALUES (?,?,?,?,?)")
            rows.forEach { r ->
                st.bindString(1, r.ch); st.bindLong(2, r.start); st.bindLong(3, r.stop)
                st.bindString(4, r.title); st.bindString(5, r.descr)
                st.executeInsert()
                st.clearBindings()
            }
            st.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun bulkInsertNames(rows: List<Pair<String, String>>) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement("INSERT INTO names (norm,xmlid) VALUES (?,?)")
            rows.forEach { (n, id) ->
                st.bindString(1, n); st.bindString(2, id)
                st.executeInsert()
                st.clearBindings()
            }
            st.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun resolveByName(norm: String): String? {
        readableDatabase.rawQuery("SELECT xmlid FROM names WHERE norm=? LIMIT 1", arrayOf(norm)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun programs(ch: String, fromSec: Long, toSec: Long, limit: Int): List<EpgEvent> {
        val out = mutableListOf<EpgEvent>()
        readableDatabase.rawQuery(
            "SELECT start,stop,title,descr FROM programs WHERE ch=? AND stop>? AND start<? ORDER BY start LIMIT ?",
            arrayOf(ch, fromSec.toString(), toSec.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(EpgEvent(c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(0), c.getLong(1)))
            }
        }
        return out
    }

    fun around(ch: String, nowSec: Long): Pair<EpgEvent?, EpgEvent?> {
        var now: EpgEvent? = null
        var next: EpgEvent? = null
        readableDatabase.rawQuery(
            "SELECT start,stop,title,descr FROM programs WHERE ch=? AND stop>? ORDER BY start LIMIT 2",
            arrayOf(ch, nowSec.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                val first = EpgEvent(c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(0), c.getLong(1))
                if (first.start <= nowSec) {
                    now = first
                    if (c.moveToNext()) next = EpgEvent(c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(0), c.getLong(1))
                } else next = first
            }
        }
        return now to next
    }
}
