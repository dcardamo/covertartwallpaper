package ca.hld.covertart

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ca.hld.covertart.data.AppState
import kotlinx.coroutines.launch

/** Minimal status screen — no style configuration, just grant + toggle + status. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appState = AppState(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatusScreen(
                        appState = appState,
                        onGrantAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onToggleMaster = { enabled ->
                            lifecycleScope.launch { appState.setMasterEnabled(enabled) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(
    appState: AppState,
    onGrantAccess: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
) {
    val masterEnabled by appState.masterEnabled.collectAsStateWithLifecycle(initialValue = true)
    val status by appState.status.collectAsStateWithLifecycle(initialValue = "Not started")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Covert Art Wallpaper", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = onGrantAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Grant notification access")
        }

        Row(
            modifier = Modifier.toggleable(
                value = masterEnabled,
                role = Role.Switch,
                onValueChange = onToggleMaster,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = masterEnabled, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(if (masterEnabled) "Wallpaper updates on" else "Wallpaper updates off")
        }

        HorizontalDivider()
        Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}
