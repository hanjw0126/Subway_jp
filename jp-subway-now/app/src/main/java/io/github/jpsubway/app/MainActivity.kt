package io.github.jpsubway.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.jpsubway.app.ui.navigation.AppNavHost
import io.github.jpsubway.app.ui.theme.JpSubwayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = (application as JpSubwayApp).container.session
        setContent { JpSubwayTheme { AppNavHost(session) } }
    }
}
