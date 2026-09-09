package com.rgbtv.app.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var selKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        when (Store.settings().accent) {
            "green" -> setTheme(R.style.Overlay_Accent_Green)
            "red" -> setTheme(R.style.Overlay_Accent_Red)
            "purple" -> setTheme(R.style.Overlay_Accent_Purple)
            "gold" -> setTheme(R.style.Overlay_Accent_Gold)
            else -> setTheme(R.style.Overlay_Accent_Blue)
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        navItems().forEach { (k, v) -> v.setOnClickListener { nav(k) } }
        selKey = savedInstanceState?.getString("nav") ?: ""
        if (savedInstanceState == null) {
            if (Store.accounts().isEmpty()) nav("profiles")
            else nav("home")
        } else paintNav()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("nav", selKey)
    }

    private fun navItems(): Map<String, TextView> = mapOf(
        "home" to b.navHome, "live" to b.navLive, "movies" to b.navMovies,
        "series" to b.navSeries, "guide" to b.navGuide, "search" to b.navSearch,
        "mylist" to b.navMylist, "settings" to b.navSettings, "profiles" to b.navProfiles
    )

    /** Top-level navigation: clears the stack, swaps content, marks the sidebar. */
    fun nav(key: String) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val f: Fragment = when (key) {
            "live" -> BrowseFragment.forMode(BrowseFragment.MODE_LIVE)
            "movies" -> BrowseFragment.forMode(BrowseFragment.MODE_VOD)
            "series" -> BrowseFragment.forMode(BrowseFragment.MODE_SERIES)
            "guide" -> GuideFragment()
            "search" -> SearchFragment()
            "mylist" -> MyListFragment()
            "settings" -> SettingsFragment()
            "profiles" -> ProfilesFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .commit()
        select(key)
    }

    fun select(key: String) {
        selKey = key
        if (::b.isInitialized) paintNav()
    }

    private fun paintNav() {
        navItems().forEach { (k, v) -> v.isSelected = (k == selKey) }
    }

    fun open(f: Fragment, back: Boolean = true) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .apply { if (back) addToBackStack(null) }
            .commit()
    }

    fun home() = nav("home")

    fun profiles() = nav("profiles")
}
