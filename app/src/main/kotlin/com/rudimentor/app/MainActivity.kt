package com.rudimentor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudimentor.app.data.DataStoreSettingsRepository
import com.rudimentor.app.ui.RudiMentorApp
import com.rudimentor.app.ui.theme.RudiMentorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory(DataStoreSettingsRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            RudiMentorTheme(paletteId = settings.paletteId) {
                RudiMentorApp(
                    buildInfo = BuildInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                    ),
                    prototypeLabEnabled = BuildConfig.DEBUG,
                    settings = settings,
                    onSelectStyle = viewModel::selectTrackerStyle,
                    onSelectPalette = viewModel::selectPalette,
                    onSelectMode = viewModel::selectMode,
                    onToggleBeat = viewModel::toggleBeat,
                    onAddBeat = viewModel::addBeat,
                    onRemoveBeat = viewModel::removeBeat,
                )
            }
        }
    }
}
