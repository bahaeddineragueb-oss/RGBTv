package com.rgbtv.app.ui

import android.util.Base64
import com.rgbtv.app.net.StreamKind
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/** Small pure helpers (no Android UI deps except Base64). */
object Kit {
    fun uuid(): String = UUID.randomUUID().toString()

    fun randomMac(): String {
        val h = "0123456789ABCDEF"
        val r = java.util.Random()
        val sb = StringBuilder("00:1A:79")
        repeat(3) { sb.append(':').append(h[r.nextInt(16)]).append(h[r.nextInt(16)]) }
        return sb.toString()
    }

    fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** Xtream EPG titles are usually base64 — decode only when it really is base64. */
    fun b64orRaw(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val t = s.trim()
        if (t.length % 4 != 0 || !Regex("^[A-Za-z0-9+/=\\s]+$").matches(t)) return s
        return try {
            val dec = Base64.decode(t, Base64.DEFAULT)
            val re = Base64.encodeToString(dec, Base64.NO_WRAP)
            if (re == t.replace(Regex("\\s"), "")) String(dec, Charsets.UTF_8) else s
        } catch (e: Exception) { s }
    }

    fun guessExt(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val last = clean.substringAfterLast('/', "")
        val dot = last.lastIndexOf('.')
        if (dot < 0) return ""
        val ext = last.substring(dot + 1).lowercase(Locale.US)
        return if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) ext else ""
    }

    fun streamKind(url: String): StreamKind {
        return when (guessExt(url)) {
            "m3u8", "m3u" -> StreamKind.HLS
            "mpd" -> StreamKind.DASH
            "" -> StreamKind.UNKNOWN
            else -> StreamKind.PROGRESSIVE
        }
    }

    fun isAdult(name: String?): Boolean =
        Regex("adult|xxx|porn|18\\+|erotic|for adults", RegexOption.IGNORE_CASE).containsMatchIn(name ?: "")

    fun parseDateLenient(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        t.toLongOrNull()?.let { return if (it > 100000000000L) it else it * 1000 }
        val fmts = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy", "MM/dd/yyyy")
        for (f in fmts) {
            try {
                val d = SimpleDateFormat(f, Locale.US).parse(t)
                if (d != null) return d.time
            } catch (e: Exception) { }
        }
        return null
    }

    fun normName(s: String): String =
        s.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}
