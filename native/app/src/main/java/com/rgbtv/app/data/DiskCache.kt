package com.rgbtv.app.data

import android.content.Context
import java.io.File

/** Tiny file cache with TTL. Keys look like "accountId:kind". Not for huge blobs. */
object DiskCache {
    private lateinit var dir: File
    const val MAX_BYTES: Long = 150L * 1024 * 1024

    fun init(ctx: Context) {
        dir = File(ctx.applicationContext.cacheDir, "rgbc")
        dir.mkdirs()
    }

    private fun file(key: String): File =
        File(dir, key.replace('/', '_').replace('\\', '_') + ".bin")

    @Synchronized
    fun put(key: String, bytes: ByteArray) {
        try {
            if (!dir.exists()) dir.mkdirs()
            file(key).writeBytes(bytes)
            trim()
        } catch (e: Exception) { }
    }

    @Synchronized
    fun get(key: String, maxAgeMs: Long): ByteArray? {
        return try {
            val f = file(key)
            if (!f.exists()) return null
            if (maxAgeMs > 0 && System.currentTimeMillis() - f.lastModified() > maxAgeMs) return null
            f.readBytes()
        } catch (e: Exception) { null }
    }

    fun putJson(key: String, json: String) = put(key, json.toByteArray(Charsets.UTF_8))
    fun getJson(key: String, maxAgeMs: Long): String? =
        get(key, maxAgeMs)?.toString(Charsets.UTF_8)

    /** Direct file access for provider-managed raw downloads. */
    @Synchronized
    fun rawFile(key: String): File {
        if (!dir.exists()) dir.mkdirs()
        return File(dir, key.replace('/', '_').replace('\\', '_') + ".bin")
    }

    @Synchronized
    fun clearAccount(accId: String) {
        try {
            dir.listFiles()?.forEach { if (it.name.startsWith("$accId:")) it.delete() }
        } catch (e: Exception) { }
    }

    @Synchronized
    fun clearAll() {
        try { dir.listFiles()?.forEach { it.delete() } } catch (e: Exception) { }
    }

    private fun trim() {
        try {
            val files = dir.listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) return
            files.sortedBy { it.lastModified() }.forEach {
                if (total <= MAX_BYTES) return
                total -= it.length()
                it.delete()
            }
        } catch (e: Exception) { }
    }
}

/** Initialized from App (kept separate so previews/tests don't need App). */
object DiskCacheInit {
    fun init(ctx: Context) = DiskCache.init(ctx)
}
