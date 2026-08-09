package com.rudimentor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rudimentor.app.ui.RudiMentorApp
import com.rudimentor.app.ui.theme.RudiMentorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RudiMentorTheme {
                RudiMentorApp(
                    buildInfo = BuildInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                    ),
                )
            }
        }
    }
}
