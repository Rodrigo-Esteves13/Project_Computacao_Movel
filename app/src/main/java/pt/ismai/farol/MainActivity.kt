package pt.ismai.farol

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.ismai.farol.domain.SharedText
import pt.ismai.farol.domain.model.ThemePref
import pt.ismai.farol.ui.navigation.FarolNavHost
import pt.ismai.farol.ui.theme.FarolTheme
import pt.ismai.farol.ui.theme.isDark

class MainActivity : ComponentActivity() {
    /** A domain or IP received through the share sheet, waiting for the navigation to open it. */
    private var sharedTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch reads the intent: after a rotation or a restore it would open the link again.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) readShared(intent)
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
                FarolTheme(preferred) {
                    FarolNavHost(container, sharedTarget = sharedTarget, onSharedHandled = { sharedTarget = null })
                }
            }
        }
    }

    /** The app is already open and the user shares another link to it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShared(intent)
    }

    private fun readShared(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val target = SharedText.extractTarget(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (target == null) {
            Toast.makeText(this, R.string.share_no_target, Toast.LENGTH_LONG).show()
        } else {
            sharedTarget = target
        }
    }
}
