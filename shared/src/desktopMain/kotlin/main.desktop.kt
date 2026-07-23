package com.myapplication

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.myapplication.common.ai.GeminiService
import com.myapplication.common.audio.DesktopAudioController
import com.myapplication.common.data.DesktopDatabaseDriverFactory
import com.myapplication.common.data.SettingsRepository
import com.myapplication.common.data.VocabRepository
import com.myapplication.common.ui.DrillViewModel

@Composable fun MainView() {
    val audioController = remember { DesktopAudioController() }
    val dbDriverFactory = remember { DesktopDatabaseDriverFactory() }
    val repository = remember { VocabRepository(dbDriverFactory) }
    val settingsRepository = remember { SettingsRepository() }
    val geminiService = remember { GeminiService(settingsRepository) }
    val drillViewModel = remember { DrillViewModel(repository, audioController, geminiService) }

    App(drillViewModel, settingsRepository, repository)
}

@Preview
@Composable
fun AppPreview() {
    MainView()
}