package app.dswriter.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dswriter.R
import app.dswriter.domain.model.Provider
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.PillShape
import app.dswriter.ui.theme.Spacing

/**
 * Provider chooser.
 *
 * One entry per configured service rather than a flat list of model names, because a model id only
 * means something together with the endpoint that serves it. Tapping a row makes that service
 * active for the whole app, so switching models is one tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderPickerSheet(
    providers: List<Provider>,
    activeProviderId: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.regular, vertical = Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(
                text = stringResource(R.string.select_model),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    start = Spacing.small,
                    top = Spacing.small,
                    bottom = Spacing.medium,
                ),
            )

            if (providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.provider_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.small),
                )
            }

            providers.forEach { provider ->
                ProviderPickerRow(
                    provider = provider,
                    selected = provider.id == activeProviderId,
                    enabled = provider.isConfigured,
                    onSelect = { onSelect(provider.id) },
                )
            }

            Surface(
                onClick = onManage,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget),
            ) {
                Text(
                    text = stringResource(R.string.provider_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(Spacing.regular),
                )
            }
            Box(Modifier.padding(bottom = Spacing.small))
        }
    }
}

@Composable
private fun ProviderPickerRow(
    provider: Provider,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget)
                .selectable(selected = selected, enabled = enabled, onClick = onSelect)
                .padding(horizontal = Spacing.regular, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.modelId.ifBlank {
                        stringResource(R.string.provider_incomplete)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (provider.supportsVision) {
                Surface(shape = PillShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = stringResource(R.string.model_picker_vision_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.small, vertical = 3.dp),
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.provider_active),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(Modifier.size(18.dp))
            }
        }
    }
}
