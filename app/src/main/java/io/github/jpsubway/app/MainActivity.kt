package io.github.jpsubway.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.jpsubway.app.di.RegionSession
import io.github.jpsubway.app.ui.navigation.AppNavHost
import io.github.jpsubway.app.ui.splash.SplashScreen
import io.github.jpsubway.app.ui.theme.JpSubwayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = (application as JpSubwayApp).container.session
        setContent {
            JpSubwayTheme {
                var splash by rememberSaveable { mutableStateOf(true) }
                if (splash) {
                    // 최소 1.2초 보여 주고, 노선 데이터가 준비되면(최대 2초) 닫는다
                    LaunchedEffect(Unit) {
                        delay(SPLASH_MIN_MS)
                        withTimeoutOrNull(SPLASH_MAX_MS - SPLASH_MIN_MS) {
                            session.state.first { it !is RegionSession.State.Loading }
                        }
                        splash = false
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    AppNavHost(session)
                    AnimatedVisibility(visible = splash, enter = EnterTransition.None, exit = fadeOut(tween(400))) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MIN_MS = 1200L
        const val SPLASH_MAX_MS = 2000L
    }
}
