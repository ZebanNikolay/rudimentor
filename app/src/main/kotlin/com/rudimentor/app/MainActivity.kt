package com.rudimentor.app

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudimentor.app.data.DataStoreSettingsRepository
import com.rudimentor.app.data.levels.AssetCourseLoader
import com.rudimentor.app.data.levels.DataStoreLevelProgressRepository
import com.rudimentor.app.ui.RudiMentorApp
import com.rudimentor.app.ui.metronome.MetronomeActions
import com.rudimentor.app.ui.theme.RudiMentorTheme
import com.rudimentor.app.util.DevLog

class MainActivity : ComponentActivity() {
    // The whole course: the curriculum and every family package that ships with it.
    private val course by lazy { AssetCourseLoader(assets).load() }

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory(
            repository = DataStoreSettingsRepository(applicationContext),
            progressRepository = DataStoreLevelProgressRepository(applicationContext, course),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevLog.install(
            context = applicationContext,
            sessionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}",
        )
        // A non-null bundle means the activity was recreated. The practice flow is
        // built on the assumption that it is not, so it is worth seeing in the log.
        DevLog.log("activity", "onCreate restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        // Draw into the cutout area: the landscape stage paints its own background
        // there and insets the content instead of leaving a black band.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val learningProgress by viewModel.learningProgress.collectAsStateWithLifecycle()
            val levelsUi by viewModel.levelsUi.collectAsStateWithLifecycle()
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
                    course = course,
                    learningProgress = learningProgress,
                    levelsUi = levelsUi,
                    actions = actions,
                    onSelectTab = viewModel::selectFamily,
                    onSelectRank = viewModel::selectRank,
                    onClickAudible = viewModel::setClickAudible,
                    onInputLatencyMs = viewModel::setInputLatencyMs,
                    onAttemptFinished = { level, rank, bpm, result ->
                        viewModel.recordAttempt(
                            levelId = level.id,
                            rank = rank,
                            bpm = bpm,
                            score = result.score,
                            stars = result.stars,
                            passed = result.passed,
                        )
                    },
                )
            }
        }
    }

    /**
     * The manifest declares the configuration changes this activity handles, so
     * flipping to landscape lands here instead of recreating the activity. Logged
     * to make the difference visible in a field report.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }
        DevLog.log(
            "activity",
            "config $orientation ${newConfig.screenWidthDp}x${newConfig.screenHeightDp} dp",
        )
    }
}
