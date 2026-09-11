package app.dswriter.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dswriter.R
import app.dswriter.domain.generation.GenerationTaskStatus
import app.dswriter.domain.generation.GenerationTaskSummary
import app.dswriter.ui.components.EmptyState
import app.dswriter.ui.components.PageScaffold
import app.dswriter.ui.components.StatusChip
import app.dswriter.ui.theme.DSTheme
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.PillShape
import app.dswriter.ui.theme.Spacing

@Composable
fun TasksRoute(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    TasksScreen(tasks, onBack, onOpenConversation, viewModel::cancel)
}

@Composable
fun TasksScreen(
    tasks: List<GenerationTaskSummary>,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.task_center),
        onBack = onBack,
        scrollable = false,
    ) {
        if (tasks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.tasks_empty),
                description = stringResource(R.string.generation_notification_title),
                icon = Icons.Default.Inbox,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                items(tasks, key = GenerationTaskSummary::id) { task ->
                    TaskCard(
                        task = task,
                        onOpen = { onOpenConversation(task.conversationId) },
                        onCancel = { onCancel(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: GenerationTaskSummary,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    val accents = DSTheme.accents
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.regular),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                if (task.isUnread) {
                    val unreadDescription = stringResource(R.string.unread_generation_result)
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = unreadDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = task.conversationTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TaskStatusChip(task.status, accents)
            }
            if (task.status in ACTIVE_STATUSES) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Text(stringResource(R.string.cancel_task))
                }
            }
        }
    }
}

@Composable
private fun TaskStatusChip(status: GenerationTaskStatus, accents: app.dswriter.ui.theme.DSAccentColors) {
    val (container, content) = when (status) {
        GenerationTaskStatus.QUEUED -> accents.queuedContainer to accents.onQueuedContainer
        GenerationTaskStatus.RUNNING ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        GenerationTaskStatus.COMPLETED ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        GenerationTaskStatus.FAILED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        GenerationTaskStatus.CANCELLED,
        GenerationTaskStatus.INTERRUPTED,
        -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusChip(
        text = stringResource(status.labelResource()),
        container = container,
        contentColor = content,
        leadingDot = status in ACTIVE_STATUSES,
    )
}

internal fun GenerationTaskStatus.labelResource(): Int = when (this) {
    GenerationTaskStatus.QUEUED -> R.string.status_queued
    GenerationTaskStatus.RUNNING -> R.string.status_streaming
    GenerationTaskStatus.COMPLETED -> R.string.status_complete
    GenerationTaskStatus.FAILED -> R.string.status_failed
    GenerationTaskStatus.CANCELLED -> R.string.status_cancelled
    GenerationTaskStatus.INTERRUPTED -> R.string.status_interrupted
}

private val ACTIVE_STATUSES = setOf(GenerationTaskStatus.QUEUED, GenerationTaskStatus.RUNNING)
