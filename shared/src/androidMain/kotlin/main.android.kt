package com.myapplication

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.myapplication.common.ai.GeminiService
import com.myapplication.common.audio.AndroidAudioController
import com.myapplication.common.data.AndroidDatabaseDriverFactory
import com.myapplication.common.data.SettingsRepository
import com.myapplication.common.data.VocabRepository
import com.myapplication.common.ui.DrillViewModel

@Composable fun MainView(context: Context) {
    val audioController = remember { AndroidAudioController(context) }
    val dbDriverFactory = remember { AndroidDatabaseDriverFactory(context) }
    val repository = remember { VocabRepository(dbDriverFactory) }
    val settingsRepository = remember { SettingsRepository() }
    val geminiService = remember { GeminiService(settingsRepository) }
    val drillViewModel = remember { DrillViewModel(repository, audioController, geminiService) }

    App(drillViewModel, settingsRepository, repository)
}
