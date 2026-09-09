package com.rgbtv.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rgbtv.app.compose.AppShell
import com.rgbtv.app.compose.CinematicTheme
import com.rgbtv.app.compose.Navigator
import com.rgbtv.app.data.CloudStore
import com.rgbtv.app.data.Store
import com.rgbtv.app.repo.Library

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        CloudStore.init(this)
        Library.migrateIfNeeded(this)
        val nav = Navigator()
        setContent {
            CinematicTheme {
                AppShell(nav)
            }
        }
    }
}
