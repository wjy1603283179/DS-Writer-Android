package app.dswriter.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dswriter.R
import app.dswriter.ui.components.EmptyState
import app.dswriter.ui.components.PageScaffold
import app.dswriter.ui.components.SectionCard
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.Spacing

@Composable
fun DataSafetyRoute(onBack: () -> Unit, viewModel: DataSafetyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        uri -> uri?.let { viewModel.export(it.toString(), DataOperation.MARKDOWN_EXPORT) }
    }
    val textLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        uri -> uri?.let { viewModel.export(it.toString(), DataOperation.TEXT_EXPORT) }
    }
    DataSafetyScreen(
        state, onBack,
        { markdownLauncher.launch("ds-writer-export.md") },
        { textLauncher.launch("ds-writer-export.txt") },
        viewModel::restoreConversation,
        viewModel::deleteConversationPermanently,
        viewModel::clearNotice,
    )
}

@Composable
internal fun DataSafetyScreen(
    state: DataSafetyUiState,
    onBack: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportText: () -> Unit,
    onRestoreConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDismissNotice: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    PageScaffold(
        title = stringResource(R.string.data_safety_title),
        onBack = onBack,
        scrollable = false,
    ) {
        SectionCard(
            title = stringResource(R.string.export_section_title),
            description = stringResource(R.string.export_security_help),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                OutlinedButton(
                    onClick = onExportMarkdown,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.touchTarget),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.export_markdown),
                        modifier = Modifier.padding(start = Spacing.small),
                    )
                }
                OutlinedButton(
                    onClick = onExportText,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.touchTarget),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.export_text),
                        modifier = Modifier.padding(start = Spacing.small),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.trash_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.tiny),
        )
        if (state.trashedConversations.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.trash_empty),
                icon = Icons.Default.DeleteOutline,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                items(state.trashedConversations, key = { it.id }) { conversation ->
                    TrashedConversationRow(
                        title = conversation.title,
                        onRestore = { onRestoreConversation(conversation.id) },
                        onDelete = { deleteTarget = conversation.id to conversation.title },
                    )
                }
            }
        }
    }
    state.notice?.let { notice ->
        val message = when (notice) {
            is DataNotice.Exported -> stringResource(R.string.export_created)
            DataNotice.Failed -> stringResource(R.string.document_io_failed)
            DataNotice.DeletionBlocked -> stringResource(R.string.permanent_delete_active_generation)
        }
        AlertDialog(
            onDismissRequest = onDismissNotice,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.close)) } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_permanent_delete)) },
            text = { Text(stringResource(R.string.permanent_delete_warning, target.second)) },
            confirmButton = {
                TextButton(onClick = { onDeleteConversation(target.first); deleteTarget = null }) {
                    Text(stringResource(R.string.delete_permanently))
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrashedConversationRow(
    title: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.regular, end = Spacing.small, top = Spacing.small, bottom = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.trashed_conversation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestore, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.restore),
                    modifier = Modifier.padding(start = Spacing.tiny),
                )
            }
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                Text(
                    text = stringResource(R.string.delete_permanently),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
