package com.rgbtv.app.ui

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
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
    private val icons = mutableMapOf<String, ImageView>()
    private val labels = mutableListOf<TextView>()
    private var anim: ValueAnimator? = null

    private data class NavDef(val key: String, val icon: Int, val label: Int)

    private val defs = listOf(
        NavDef("home", R.drawable.ic_nav_home, R.string.nav_home),
        NavDef("live", R.drawable.ic_nav_live, R.string.live_tv),
        NavDef("movies", R.drawable.ic_nav_movie, R.string.movies),
        NavDef("series", R.drawable.ic_nav_series, R.string.series),
        NavDef("sports", R.drawable.ic_nav_sports, R.string.nav_sports),
        NavDef("guide", R.drawable.ic_nav_guide, R.string.guide),
        NavDef("mylist", R.drawable.ic_nav_fav, R.string.my_list),
        NavDef("search", R.drawable.ic_nav_search, R.string.search),
        NavDef("settings", R.drawable.ic_nav_settings, R.string.settings),
        NavDef("profiles", R.drawable.ic_nav_users, R.string.switch_profile)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Single Electric Blue accent (§2) — no multi-accent themes.
        setTheme(R.style.Overlay_Accent_Blue)
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
        val tint = getColorStateList(R.color.nav_text)
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

            val icon = ImageView(ctx)
            icon.setImageResource(d.icon)
            icon.imageTintList = tint
            icon.layoutParams = LinearLayout.LayoutParams(dp(52), dp(30))
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            row.addView(icon)

            val label = TextView(ctx)
            label.setText(d.label)
            label.setTextColor(tint)
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
            icons[d.key] = icon
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
        for ((k, v) in rows) {
            val sel = k == selKey
            v.isSelected = sel
            icons[k]?.isSelected = sel
        }
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
