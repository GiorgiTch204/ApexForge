package com.apexforge.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexforge.app.domain.ThemeMode
import com.apexforge.app.ui.intro.IntroOverlay
import com.apexforge.app.ui.navigation.ApexApp
import com.apexforge.app.ui.theme.ApexTheme

@Composable
fun ApexRoot(viewModel: AppViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val init by viewModel.init.collectAsStateWithLifecycle()
    val introFinished by viewModel.introFinished.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(dark, activity) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    ApexTheme(darkTheme = dark, hapticsEnabled = prefs?.haptics ?: true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val current = prefs
            // The app is composed underneath the intro as soon as it is ready, so the exit is seamless.
            if (init.ready && current != null) {
                ApexApp(appViewModel = viewModel, prefs = current)
            }
            if (!introFinished) {
                IntroOverlay(
                    progress = init.progress,
                    ready = init.ready,
                    onFinished = viewModel::onIntroFinished,
                )
            }
        }
    }
}
