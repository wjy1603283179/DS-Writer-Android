package app.dswriter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dswriter.ui.DSWriterApp
import app.dswriter.ui.theme.DSWriterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openTasks by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTasks = intent?.action == ACTION_OPEN_TASKS
        enableEdgeToEdge()
        setContent {
            DSWriterTheme {
                DSWriterApp(
                    openTasks = openTasks,
                    onTasksOpened = { openTasks = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_TASKS) openTasks = true
    }

    companion object {
        const val ACTION_OPEN_TASKS = "app.dswriter.action.OPEN_TASKS"
    }
}
