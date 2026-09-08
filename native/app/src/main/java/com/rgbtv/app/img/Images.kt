package com.rgbtv.app.img

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.rgbtv.app.R
import com.rgbtv.app.net.Net
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Zero-dependency image loader: memory LRU + disk + pooled decode, RecyclerView-safe via tags. */
object Images {
    enum class Kind(val maxDim: Int) { POSTER(512), LOGO(256), BACKDROP(960) }

    private lateinit var appCtx: Context
    private lateinit var mem: LruCache<String, Bitmap>
    private lateinit var disk: File
    private val pool = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())
    private var phColor = 0xFF1B2334.toInt()
    const val DISK_MAX: Long = 200L * 1024 * 1024

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
        mem = object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(k: String, v: Bitmap): Int = v.byteCount / 1024
        }
        disk = File(appCtx.cacheDir, "img")
        disk.mkdirs()
        try { phColor = appCtx.getColor(R.color.placeholder) } catch (e: Exception) { }
        pool.submit { trimDisk() }
    }

    fun load(iv: ImageView, url: String?, kind: Kind) {
        val u = url?.trim() ?: ""
        iv.tag = u
        if (u.isEmpty() || u == "null") {
            iv.setImageDrawable(ColorDrawable(phColor))
            return
        }
        mem.get(u)?.let {
            iv.setImageBitmap(it)
            return
        }
        iv.setImageDrawable(ColorDrawable(phColor))
        pool.submit {
            try {
                val bmp = diskGet(u, kind) ?: netGet(u, kind)
                if (bmp != null) {
                    mem.put(u, bmp)
                    ui.post {
                        if (iv.tag == u) {
                            val td = TransitionDrawable(arrayOf<Drawable>(ColorDrawable(phColor), BitmapDrawable(appCtx.resources, bmp)))
                            td.isCrossFadeEnabled = true
                            iv.setImageDrawable(td)
                            td.startTransition(140)
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun hash(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun diskGet(url: String, kind: Kind): Bitmap? {
        return try {
            val f = File(disk, hash(url))
            if (!f.exists()) return null
            f.setLastModified(System.currentTimeMillis())
            decode(f.readBytes(), kind)
        } catch (e: Exception) { null }
    }

    private fun netGet(url: String, kind: Kind): Bitmap? {
        val bytes = try { Net.getBytes(url, timeoutMs = 15000) } catch (e: Exception) { return null }
        if (bytes.size > 8 * 1024 * 1024) return null
        val bmp = decode(bytes, kind) ?: return null
        try {
            File(disk, hash(url)).writeBytes(bytes)
            trimDisk()
        } catch (e: Exception) { }
        return bmp
    }

    private fun decode(bytes: ByteArray, kind: Kind): Bitmap? {
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, b)
            if (b.outWidth <= 0) return null
            var sample = 1
            val longest = maxOf(b.outWidth, b.outHeight)
            while (longest / (sample * 2) > kind.maxDim) sample *= 2
            val o = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        } catch (e: Exception) { null }
    }

    private fun trimDisk() {
        try {
            val files = disk.listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= DISK_MAX) return
            files.sortedBy { it.lastModified() }.forEach {
                if (total <= DISK_MAX) return
                total -= it.length()
                it.delete()
            }
        } catch (e: Exception) { }
    }

    fun clearMemory() {
        try { mem.evictAll() } catch (e: Exception) { }
    }
}
