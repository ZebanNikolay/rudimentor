package com.rudimentor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudimentor.app.data.DataStoreSettingsRepository
import com.rudimentor.app.ui.RudiMentorApp
import com.rudimentor.app.ui.metronome.MetronomeActions
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
            // The action holder is stable for the lifetime of the view model,
            // so composables that only close over `actions` skip recomposition
            // when unrelated settings change.
            val actions = remember(viewModel) {
                MetronomeActions(
                    cycleBeat = viewModel::cycleBeat,
                    toggleHand = viewModel::toggleHand,
                    addBeat = viewModel::addBeat,
                    removeBeat = viewModel::removeBeat,
                    addRow = viewModel::addRow,
                    removeRow = viewModel::removeRow,
                    selectRow = viewModel::selectRow,
                    bpmDelta = viewModel::adjustBpm,
                    showLettersChange = viewModel::setShowHandLetters,
                )
            }
            RudiMentorTheme {
                RudiMentorApp(
                    buildInfo = BuildInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                    ),
                    settings = settings,
                    actions = actions,
                )
            }
        }
    }
}
