package com.apexforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.apexforge.app.ui.AppViewModel
import com.apexforge.app.ui.ApexRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash (icon on charcoal) hands over to the Compose intro on the first drawn frame.
        // Keeping it on screen any longer would only delay the real, progress-driven intro.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { ApexRoot(appViewModel) }
    }
}
