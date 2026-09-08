package com.rgbtv.app.net

import android.content.Context
import android.util.Xml
import com.rgbtv.app.data.EpgDb
import com.rgbtv.app.data.EpgEvent
import com.rgbtv.app.data.LiveCh
import com.rgbtv.app.data.ProgramRow
import com.rgbtv.app.ui.Kit
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/** XMLTV guide: streamed download → pull-parse → SQLite. Refreshed every 12 h. */
class Xmltv(private val ctx: Context, private val accountId: String, private val url: String) {
    private val db = EpgDb(ctx, "epg_$accountId.db")
    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    /** True when usable data exists (downloads/parses when stale). Call on IO thread. */
    fun ensureLoaded(): Boolean {
        try {
            val at = db.getMeta("at")?.toLongOrNull() ?: 0
            if (System.currentTimeMillis() - at < 12 * 3600_000 && db.count() > 0) return true
        } catch (e: Exception) { }
        return try {
            refresh()
            db.count() > 0
        } catch (e: Exception) {
            try { db.count() > 0 } catch (e2: Exception) { false }
        }
    }

    fun clear() {
        try { db.clearAll() } catch (e: Exception) { }
    }

    private fun channelsOf(ch: LiveCh): List<String> {
        val ids = mutableListOf<String>()
        if (ch.epgId.isNotEmpty()) ids.add(ch.epgId)
        if (ch.id.isNotEmpty()) ids.add(ch.id)
        return ids
    }

    private fun resolve(ch: LiveCh): String? {
        channelsOf(ch).forEach { return it } // direct ids tried first by callers
        return null
    }

    fun nowNext(ch: LiveCh): Pair<EpgEvent?, EpgEvent?> {
        if (!ensureLoaded()) return null to null
        val now = System.currentTimeMillis() / 1000
        channelsOf(ch).forEach { id ->
            val r = db.around(id, now)
            if (r.first != null || r.second != null) return r
        }
        db.resolveByName(Kit.normName(ch.name))?.let { return db.around(it, now) }
        return null to null
    }

    fun programs(ch: LiveCh, fromSec: Long, toSec: Long, limit: Int): List<EpgEvent> {
        if (!ensureLoaded()) return emptyList()
        channelsOf(ch).forEach { id ->
            val l = db.programs(id, fromSec, toSec, limit)
            if (l.isNotEmpty()) return l
        }
        db.resolveByName(Kit.normName(ch.name))?.let { return db.programs(it, fromSec, toSec, limit) }
        return emptyList()
    }

    private fun refresh() {
        val tmp = File(ctx.cacheDir, "xmltv_$accountId.tmp")
        Net.download(url, timeoutMs = 180000, maxBytes = 400L * 1024 * 1024, dest = tmp)
        val inp: InputStream = if (url.lowercase().endsWith(".gz")) GZIPInputStream(FileInputStream(tmp), 65536)
        else FileInputStream(tmp)
        inp.use { parse(it) }
        try { tmp.delete() } catch (e: Exception) { }
    }

    private fun parse(inp: InputStream) {
        db.clearAll()
        val p: XmlPullParser = Xml.newPullParser()
        p.setInput(inp, "UTF-8")
        val chanNames = mutableMapOf<String, MutableList<String>>()
        var rows = mutableListOf<ProgramRow>()
        var names = mutableListOf<Pair<String, String>>()
        var ev = p.eventType
        var curChan = ""
        var curNames = mutableListOf<String>()
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> {
                        curChan = p.getAttributeValue(null, "id") ?: ""
                        curNames = mutableListOf()
                    }
                    "display-name" -> if (curChan.isNotEmpty()) {
                        val t = p.nextText()
                        if (t.isNotEmpty()) curNames.add(t)
                    }
                    "programme" -> {
                        val ch = p.getAttributeValue(null, "channel") ?: ""
                        val start = parseTime(p.getAttributeValue(null, "start"))
                        val stop = parseTime(p.getAttributeValue(null, "stop"))
                        var title = ""; var desc = ""
                        var inner = p.next()
                        while (!(inner == XmlPullParser.END_TAG && p.name == "programme")) {
                            if (inner == XmlPullParser.START_TAG) {
                                when (p.name) {
                                    "title" -> { val t = safeText(p); if (title.isEmpty()) title = t }
                                    "desc" -> { val t = safeText(p); if (desc.isEmpty()) desc = t }
                                    else -> skip(p)
                                }
                            }
                            if (inner == XmlPullParser.END_DOCUMENT) break
                            inner = p.next()
                        }
                        if (ch.isNotEmpty() && stop > 0) {
                            rows.add(ProgramRow(ch, start, stop, title, desc))
                            if (rows.size >= 4000) {
                                db.bulkInsertPrograms(rows); rows = mutableListOf()
                            }
                        }
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "channel" && curChan.isNotEmpty()) {
                curNames.forEach { n ->
                    val norm = Kit.normName(n)
                    if (norm.isNotEmpty()) {
                        names.add(norm to curChan)
                        if (names.size >= 4000) { db.bulkInsertNames(names); names = mutableListOf() }
                    }
                }
                chanNames[curChan] = curNames
                curChan = ""
            }
            ev = p.next()
        }
        if (rows.isNotEmpty()) db.bulkInsertPrograms(rows)
        if (names.isNotEmpty()) db.bulkInsertNames(names)
        db.setMeta("at", System.currentTimeMillis().toString())
    }

    private fun safeText(p: XmlPullParser): String {
        return try { p.nextText() ?: "" } catch (e: Exception) { "" }
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return try {
            val m = Regex("^(\\d{14})(?:\\s*([+-]\\d{4}))?").find(s.trim()) ?: return 0
            val zone = m.groupValues[2].ifEmpty { "+0000" }
            (fmt.parse("${m.groupValues[1]} $zone")?.time ?: 0) / 1000
        } catch (e: Exception) { 0 }
    }
}
