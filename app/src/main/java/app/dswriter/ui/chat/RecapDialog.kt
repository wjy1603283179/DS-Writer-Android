package app.dswriter.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dswriter.R
import app.dswriter.ui.theme.Spacing

/**
 * Review step for a recap.
 *
 * The text is editable on purpose. Nothing here is submitted automatically: the user reads the
 * condensed text, can correct or shorten it, and only then decides whether it becomes the first
 * real message of a new conversation. That is what keeps a recap from being a hidden summary.
 */
@Composable
internal fun RecapDialog(
    recap: RecapState.Ready,
    onDismiss: () -> Unit,
    onUseAsNewConversation: (String) -> Unit,
) {
    var text by remember(recap.text) { mutableStateOf(recap.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recap_dialog_title)) },
        text = {
            Column {
                if (recap.sourceConversationTitle.isNotBlank()) {
                    Text(
                        text = recap.sourceConversationTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.recap_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 0.dp).fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.recap_source_count, recap.condensedMessageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recap.remainingMessageCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.recap_remaining_warning,
                            recap.remainingMessageCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.tiny),
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp),
                    label = { Text(stringResource(R.string.recap_label)) },
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUseAsNewConversation(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.recap_use_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Confirmation shown before the one request that is not a plain conversation turn. */
@Composable
internal fun RecapConsentDialog(
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text(stringResource(R.string.recap_confirm_title)) },
        text = { Text(stringResource(R.string.recap_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isGenerating) {
                Text(stringResource(R.string.recap_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
