package com.rgbtv.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.rgbtv.app.R
import com.rgbtv.app.data.Store
import com.rgbtv.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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
        if (savedInstanceState == null) {
            if (Store.accounts().isEmpty()) open(ProfilesFragment(), false)
            else open(HomeFragment(), false)
        }
    }

    fun open(f: Fragment, back: Boolean = true) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .apply { if (back) addToBackStack(null) }
            .commit()
    }

    fun home() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        open(HomeFragment(), false)
    }

    fun profiles() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        open(ProfilesFragment(), false)
    }
}
