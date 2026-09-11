package app.dswriter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.dswriter.ui.chat.ChatRoute
import app.dswriter.ui.data.DataSafetyRoute
import app.dswriter.ui.settings.SettingsRoute
import app.dswriter.ui.tasks.TasksRoute

@Composable
fun DSWriterApp(openTasks: Boolean = false, onTasksOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    LaunchedEffect(openTasks) {
        if (openTasks) {
            navController.navigate(TASKS_ROUTE) { launchSingleTop = true }
            onTasksOpened()
        }
    }
    NavHost(navController, startDestination = CHAT_ROUTE) {
        composable(CHAT_ROUTE) { entry ->
            val requestedConversation by entry.savedStateHandle
                .getStateFlow<String?>(REQUESTED_CONVERSATION_KEY, null)
                .collectAsStateWithLifecycle()
            ChatRoute(
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onOpenTasks = { navController.navigate(TASKS_ROUTE) },
                onOpenDataSafety = { navController.navigate(DATA_SAFETY_ROUTE) },
                requestedConversationId = requestedConversation,
                onRequestedConversationOpened = { entry.savedStateHandle[REQUESTED_CONVERSATION_KEY] = null },
            )
        }
        composable(SETTINGS_ROUTE) { SettingsRoute(navController::navigateUp) }
        composable(TASKS_ROUTE) {
            TasksRoute(
                onBack = navController::navigateUp,
                onOpenConversation = { conversationId ->
                    navController.getBackStackEntry(CHAT_ROUTE).savedStateHandle[REQUESTED_CONVERSATION_KEY] = conversationId
                    navController.popBackStack(CHAT_ROUTE, false)
                },
            )
        }
        composable(DATA_SAFETY_ROUTE) { DataSafetyRoute(navController::navigateUp) }
    }
}

private const val CHAT_ROUTE = "chat"
private const val SETTINGS_ROUTE = "settings"
private const val TASKS_ROUTE = "tasks"
private const val DATA_SAFETY_ROUTE = "data-safety"
private const val REQUESTED_CONVERSATION_KEY = "requestedConversationId"
