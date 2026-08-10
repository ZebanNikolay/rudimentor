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
            RudiMentorTheme {
                RudiMentorApp(
                    buildInfo = BuildInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                    ),
                    settings = settings,
                    onCycleBeat = viewModel::cycleBeat,
                    onToggleHand = viewModel::toggleHand,
                    onAddBeat = viewModel::addBeat,
                    onRemoveBeat = viewModel::removeBeat,
                    onAddRow = viewModel::addRow,
                    onRemoveRow = viewModel::removeRow,
                    onSelectRow = viewModel::selectRow,
                    onBpmDelta = viewModel::adjustBpm,
                    onShowLettersChange = viewModel::setShowHandLetters,
                )
            }
        }
    }
}
