package app.dswriter.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dswriter.R
import app.dswriter.domain.generation.GenerationRequestSnapshot
import app.dswriter.domain.generation.RequestRole

@Composable
internal fun DebugRequestInspector(snapshot: GenerationRequestSnapshot?) {
    if (snapshot == null) return
    var shown by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { shown = true }) {
        Text(stringResource(R.string.inspect_request))
    }
    if (shown) {
        RequestInspectorDialog(snapshot) { shown = false }
    }
}

@Composable
private fun RequestInspectorDialog(
    snapshot: GenerationRequestSnapshot,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_inspector_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.request_inspector_debug_only))
                Text(stringResource(R.string.request_inspector_model, snapshot.modelId))
                Text(stringResource(R.string.request_inspector_base_url, snapshot.baseUrl))
                Text(stringResource(R.string.request_inspector_key_redacted))
                Text(stringResource(R.string.request_inspector_estimate, snapshot.estimatedInputTokens))
                Text(stringResource(R.string.request_inspector_dropped, snapshot.droppedMessageCount))
                snapshot.messages.forEachIndexed { index, message ->
                    Text(
                        stringResource(
                            R.string.request_inspector_message,
                            index + 1,
                            stringResource(
                                if (message.role == RequestRole.USER) {
                                    R.string.request_role_user
                                } else {
                                    R.string.request_role_assistant
                                },
                            ),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    SelectionContainer { Text(message.content) }
                    message.attachments.forEach { attachment ->
                        Text(
                            stringResource(
                                R.string.request_inspector_attachment,
                                attachment.id,
                                attachment.displayName,
                                attachment.mimeType,
                                attachment.width,
                                attachment.height,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
