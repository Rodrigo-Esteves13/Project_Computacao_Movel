package pt.ismai.farol

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.ismai.farol.domain.model.ThemePref
import pt.ismai.farol.ui.navigation.FarolNavHost
import pt.ismai.farol.ui.theme.FarolTheme
import pt.ismai.farol.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (applicationContext as FarolApp).container
        setContent {
            // Draws nothing until the saved preference arrives: avoids a flash of the wrong theme.
            val theme by container.settings.theme.collectAsStateWithLifecycle<ThemePref?>(initialValue = null)
            theme?.let { preferred ->
                val dark = preferred.isDark()
                DisposableEffect(dark) {
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                FarolTheme(preferred) { FarolNavHost(container) }
            }
        }
    }
}
