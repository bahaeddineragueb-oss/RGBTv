package com.rgbtv.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rgbtv.app.data.DiskCacheInit
import com.rgbtv.app.data.Store
import com.rgbtv.app.img.Images

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Images.init(this)
        DiskCacheInit.init(this)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        applyLocale()
    }

    fun applyLocale() {
        val lang = Store.settings().lang
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(if (lang == "system") "" else lang)
        )
    }

    companion object {
        const val VERSION = "5.0.0"
    }
}
