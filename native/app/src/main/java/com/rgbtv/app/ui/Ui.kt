package com.rgbtv.app.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ui {
    fun toast(ctx: Context?, msg: String) {
        if (ctx == null) return
        Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    /** TV-style focus zoom for cards/rows. Call on the focusable root view. */
    fun focusScale(v: View, s: Float = 1.05f) {
        v.setOnFocusChangeListener { vv, has ->
            vv.animate().cancel()
            vv.animate().scaleX(if (has) s else 1f).scaleY(if (has) s else 1f)
                .translationZ(if (has) 8f else 0f).setDuration(180).start()
        }
    }

    fun autoSpan(rv: RecyclerView, itemDp: Int, landscapeBoost: Int = 0) {
        val w = rv.resources.configuration.screenWidthDp
        val land = rv.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val span = (w / itemDp + if (land) landscapeBoost else 0).coerceAtLeast(2)
        (rv.layoutManager as? GridLayoutManager)?.spanCount = span
    }

    fun fmtDur(ms: Long): String {
        if (ms <= 0) return "0:00"
        var s = ms / 1000
        val h = s / 3600; s %= 3600
        val m = s / 60; s %= 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun clock(sec: Long): String {
        if (sec <= 0) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))
    }

    fun dateFull(ms: Long?): String {
        if (ms == null || ms <= 0) return ""
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
    }

    /** Normalizes provider ratings ("62." -> "6.2", "9.0" -> "9"). */
    fun fmtRating(r: String): String {
        val clean = r.trim().trimEnd('.')
        if (clean.isEmpty()) return ""
        val d = clean.toDoubleOrNull() ?: return clean
        if (d <= 0) return ""
        val v = if (d > 10) d / 10 else d
        return "%.1f".format(v).trimEnd('0').trimEnd('.')
    }

    fun Fragment.main(): MainActivity = requireActivity() as MainActivity
}
