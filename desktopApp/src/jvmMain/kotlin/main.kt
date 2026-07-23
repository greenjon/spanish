import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.myapplication.MainView
import com.myapplication.common.data.SettingsRepository

fun main() = application {
    val settingsRepository = remember { SettingsRepository() }
    val (savedWidth, savedHeight) = settingsRepository.getWindowSize()
    val windowState = rememberWindowState(size = DpSize(savedWidth.dp, savedHeight.dp))

    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }
            .collect { size ->
                if (size.width.value > 100 && size.height.value > 100) {
                    settingsRepository.saveWindowSize(size.width.value.toInt(), size.height.value.toInt())
                }
            }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Spanish Practice"
    ) {
        MainView()
    }
}