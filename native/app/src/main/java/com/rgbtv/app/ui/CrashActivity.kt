package com.rgbtv.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rgbtv.app.compose.Cine
import com.rgbtv.app.compose.CinematicTheme
import com.rgbtv.app.compose.TvButton

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "Error"
        val trace = intent.getStringExtra("trace") ?: ""
        val head = "RGBTv 5.0.1 · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n$title\n\n$trace"
        setContent {
            CinematicTheme {
                CrashView(head) { finishAffinity() }
            }
        }
    }
}

@Composable
private fun CrashView(report: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Cine.bg)
            .padding(20.dp)
    ) {
        Text("⚠ حدث خطأ — Crash report", style = Cine.h1)
        Spacer(Modifier.height(4.dp))
        Text("صوّر هذه الشاشة وأرسلها — Screenshot & send", style = Cine.body)
        Spacer(Modifier.height(10.dp))
        Text(
            report,
            style = Cine.small,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .background(Cine.card, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        )
        Spacer(Modifier.height(10.dp))
        Row {
            TvButton("📋 Copy / نسخ") {
                try {
                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("crash", report))
                    Ui.toast(ctx, "Copied")
                } catch (e: Exception) {
                }
            }
            Spacer(Modifier.width(8.dp))
            TvButton("↻ Restart / إعادة", ghost = true) {
                try {
                    ctx.startActivity(Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
                } catch (e: Exception) {
                }
                onClose()
            }
        }
    }
}
