package app.dswriter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import app.dswriter.R
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationMessageStatus
import app.dswriter.domain.model.ConversationRole
import app.dswriter.ui.components.LocalImageThumbnail
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.PillShape
import app.dswriter.ui.theme.Spacing

/**
 * One conversation message.
 *
 * A user message is a compact bubble aligned to the end; an assistant reply is a full-width
 * reading surface, because long-form output is the primary content of this product and a narrow
 * bubble would force excessive line wrapping.
 */
@Composable
internal fun MessageItem(message: ConversationMessage, onRegenerate: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == ConversationRole.USER
    var reasoningExpanded by rememberSaveable(message.id) { mutableStateOf(false) }

    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomEnd = 6.dp,
                        bottomStart = 20.dp,
                    ),
                ) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = Spacing.regular, vertical = Spacing.medium),
                        )
                    }
                }
                message.attachments.forEach {
                    LocalImageThumbnail(it.localUri, it.displayName, Modifier.padding(top = Spacing.small))
                }
            }
        }
        return
    }

    AssistantMessage(
        message = message,
        reasoningExpanded = reasoningExpanded,
        onReasoningExpandedChange = { reasoningExpanded = it },
        onCopy = { clipboard.setText(AnnotatedString(message.content)) },
        onRegenerate = onRegenerate,
    )
}

@Composable
private fun AssistantMessage(
    message: ConversationMessage,
    reasoningExpanded: Boolean,
    onReasoningExpandedChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        AssistantAvatar()
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.role_assistant),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
            if (message.reasoningContent.isNotEmpty()) {
                ReasoningPanel(
                    reasoning = message.reasoningContent,
                    characterCount = message.reasoningCharacterCount,
                    isLivePreview = message.reasoningIsPreview,
                    isStreaming = message.generationStatus == ConversationMessageStatus.STREAMING,
                    expanded = reasoningExpanded,
                    onExpandedChange = onReasoningExpandedChange,
                )
            }
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            message.attachments.forEach {
                LocalImageThumbnail(it.localUri, it.displayName, Modifier.padding(top = Spacing.small))
            }
            MessageActionRow(message, onCopy, onRegenerate)
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "DS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MessageActionRow(
    message: ConversationMessage,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tiny),
        modifier = Modifier.padding(top = Spacing.small),
    ) {
        MessageAction(stringResource(R.string.copy), Icons.Default.ContentCopy, onCopy)
        if (message.generationStatus == ConversationMessageStatus.COMPLETE) {
            MessageAction(stringResource(R.string.regenerate), Icons.Default.Refresh, onRegenerate)
        } else {
            StatusPill(message.generationStatus)
        }
    }
}

/** Low-chrome text action: reads as a quiet affordance rather than a stock Material button. */
@Composable
private fun MessageAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.small,
            vertical = Spacing.tiny,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = Spacing.tiny),
        )
    }
}

@Composable
private fun StatusPill(status: ConversationMessageStatus) {
    val container = when (status) {
        ConversationMessageStatus.STREAMING -> MaterialTheme.colorScheme.primaryContainer
        ConversationMessageStatus.COMPLETE -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (status) {
        ConversationMessageStatus.STREAMING -> MaterialTheme.colorScheme.onPrimaryContainer
        ConversationMessageStatus.COMPLETE -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = PillShape, color = container) {
        Text(
            text = stringResource(status.labelResource()),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.small, vertical = 3.dp),
        )
    }
}

internal fun ConversationMessageStatus.labelResource(): Int = when (this) {
    ConversationMessageStatus.STREAMING -> R.string.status_streaming
    ConversationMessageStatus.COMPLETE -> R.string.status_complete
    ConversationMessageStatus.FAILED -> R.string.status_failed
    ConversationMessageStatus.CANCELLED -> R.string.status_cancelled
    ConversationMessageStatus.INTERRUPTED -> R.string.status_interrupted
}

@Composable
private fun ReasoningPanel(
    reasoning: String,
    characterCount: Int,
    isLivePreview: Boolean,
    isStreaming: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.medium),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .selectable(selected = expanded, onClick = { onExpandedChange(!expanded) })
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isStreaming) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                )
                Column(Modifier.weight(1f).padding(start = Spacing.small)) {
                    Text(stringResource(R.string.reasoning_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            if (isStreaming) R.string.reasoning_receiving else R.string.reasoning_complete_count,
                            characterCount,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    stringResource(if (expanded) R.string.hide_reasoning else R.string.show_reasoning),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (expanded) ReasoningPages(reasoning, isLivePreview)
        }
    }
}

@Composable
private fun ReasoningPages(reasoning: String, isLivePreview: Boolean) {
    val pages = remember(reasoning) { reasoning.safePages(REASONING_PAGE_CHARACTERS) }
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(pages.size) {
        pageIndex = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    }
    val scrollState = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(pageIndex) { scrollState.scrollTo(0) }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.medium)) {
        if (isLivePreview) {
            Text(
                stringResource(R.string.reasoning_live_preview),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
        }
        SelectionContainer {
            Text(
                pages.getOrElse(pageIndex) { "" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) {
                    Text(stringResource(R.string.previous_reasoning_segment))
                }
                Text(
                    stringResource(R.string.reasoning_segment, pageIndex + 1, pages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { pageIndex++ }, enabled = pageIndex < pages.lastIndex) {
                    Text(stringResource(R.string.next_reasoning_segment))
                }
            }
        }
    }
}

/** Splits stored reasoning into pages without breaking a surrogate pair. */
private fun String.safePages(maxCharacters: Int): List<String> {
    if (isEmpty()) return listOf("")
    val result = mutableListOf<String>()
    var start = 0
    while (start < length) {
        var end = (start + maxCharacters).coerceAtMost(length)
        if (end < length && this[end - 1].isHighSurrogate() && this[end].isLowSurrogate()) end--
        result += substring(start, end)
        start = end
    }
    return result
}

private const val REASONING_PAGE_CHARACTERS = 6_000
