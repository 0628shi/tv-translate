package com.tvtranslate.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tvtranslate.app.ui.HomeScreen
import com.tvtranslate.app.ui.PlayerScreen
import com.tvtranslate.app.ui.ProcessingScreen
import com.tvtranslate.app.ui.SettingsScreen
import com.tvtranslate.app.ui.TvTranslateTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TvTranslateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (vm.screen) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.PROCESSING -> ProcessingScreen(vm)
                        Screen.PLAYER -> PlayerScreen(vm)
                    }
                }
            }
        }
    }
}
