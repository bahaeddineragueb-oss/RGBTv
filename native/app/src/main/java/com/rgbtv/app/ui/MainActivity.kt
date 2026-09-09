package com.rgbtv.app.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
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
    private var expanded = false
    private val rows = mutableMapOf<String, LinearLayout>()
    private val labels = mutableListOf<TextView>()
    private var anim: ValueAnimator? = null

    private data class NavDef(val key: String, val glyph: String, val label: Int)

    private val defs = listOf(
        NavDef("home", "\uD83C\uDFE0", R.string.nav_home),
        NavDef("live", "\uD83D\uDCFA", R.string.live_tv),
        NavDef("movies", "\uD83C\uDFAC", R.string.movies),
        NavDef("series", "\uD83D\uDCDA", R.string.series),
        NavDef("sports", "⚽", R.string.nav_sports),
        NavDef("guide", "\uD83D\uDCC5", R.string.guide),
        NavDef("mylist", "⭐", R.string.my_list),
        NavDef("search", "\uD83D\uDD0D", R.string.search),
        NavDef("settings", "⚙", R.string.settings),
        NavDef("profiles", "\uD83D\uDC65", R.string.switch_profile)
    )

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
        buildRows()
        b.root.viewTreeObserver.addOnGlobalFocusChangeListener(
            object : ViewTreeObserver.OnGlobalFocusChangeListener {
                override fun onGlobalFocusChanged(old: View?, now: View?) {
                    if (now != null && isInSidebar(now)) expand(true)
                    else expand(false)
                }
            }
        )
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildRows() {
        val ctx = this
        for (d in defs) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.isClickable = true
            row.isFocusable = true
            row.setBackgroundResource(R.drawable.nav_bg)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(2)
            row.layoutParams = lp
            row.setPadding(dp(8), dp(10), dp(8), dp(10))

            val icon = TextView(ctx)
            icon.text = d.glyph
            icon.textSize = 24f
            icon.gravity = Gravity.CENTER
            icon.layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT)
            row.addView(icon)

            val label = TextView(ctx)
            label.setText(d.label)
            label.setTextColor(getColorStateList(R.color.nav_text))
            label.textSize = 16f
            label.maxLines = 1
            val llp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            llp.marginStart = dp(4)
            label.layoutParams = llp
            label.visibility = View.GONE
            row.addView(label)
            labels.add(label)

            row.setOnClickListener { nav(d.key) }
            Ui.focusScale(row, 1.05f)
            b.navList.addView(row)
            rows[d.key] = row
        }
    }

    private fun isInSidebar(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p == b.sidebar) return true
            val parent = p.parent
            p = if (parent is View) parent else null
        }
        return false
    }

    private fun expand(want: Boolean) {
        if (want == expanded) return
        expanded = want
        anim?.cancel()
        val startW = b.sidebar.width
        val endW = dp(if (want) 280 else 72)
        if (want) {
            for (l in labels) l.visibility = View.VISIBLE
            b.sideLogo.visibility = View.VISIBLE
        }
        anim = ValueAnimator.ofInt(startW, endW)
        anim?.duration = 220
        anim?.addUpdateListener { a ->
            val w = a.animatedValue as Int
            val lp = b.sidebar.layoutParams
            lp.width = w
            b.sidebar.layoutParams = lp
        }
        anim?.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                if (!expanded) {
                    for (l in labels) l.visibility = View.GONE
                    b.sideLogo.visibility = View.GONE
                }
            }
        })
        anim?.start()
    }

    /** Top-level navigation: clears the stack, swaps content, marks the sidebar. */
    fun nav(key: String) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val f: Fragment = when (key) {
            "live" -> BrowseFragment.forMode(BrowseFragment.MODE_LIVE)
            "movies" -> BrowseFragment.forMode(BrowseFragment.MODE_VOD)
            "series" -> BrowseFragment.forMode(BrowseFragment.MODE_SERIES)
            "sports" -> SportsFragment()
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
        for ((k, v) in rows) v.isSelected = (k == selKey)
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
