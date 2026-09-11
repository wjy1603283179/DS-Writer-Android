package app.dswriter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dswriter.R
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.PillShape
import app.dswriter.ui.theme.Spacing

/**
 * Shared building blocks for every screen.
 *
 * These exist so the panels, capsules, and empty states stay visually identical across the
 * chat, settings, tasks, and data screens instead of each screen re-inventing its own surface.
 */

/** A grouped content panel used to separate sections on settings-style screens. */
@Composable
fun SectionCard(
    title: String? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.tiny, bottom = Spacing.small),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.regular),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                content()
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.tiny, top = Spacing.small),
            )
        }
    }
}

/**
 * Standard secondary-screen frame: a soft header with a labelled back affordance and a title,
 * then page content in a readable column.
 *
 * [footer] is pinned below the scrolling area rather than placed inside it. A form taller than the
 * screen would otherwise push its own submit button past the bottom edge, where it cannot be
 * reached at all: a surface this app actually shipped, so the action row is structurally outside
 * the scroll region now.
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.back),
                        modifier = Modifier.padding(start = Spacing.tiny),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.small),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollable) {
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier.weight(1f)
                        },
                    )
                    .padding(horizontal = Spacing.regular)
                    .padding(top = Spacing.regular, bottom = Spacing.xxlarge),
                verticalArrangement = Arrangement.spacedBy(Spacing.regular),
                content = content,
            )
            if (footer != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 3.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = Spacing.regular, vertical = Spacing.medium),
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

/** Small status capsule. [container] and [contentColor] are supplied by the caller's semantics. */
@Composable
fun StatusChip(
    text: String,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false,
) {
    Surface(shape = PillShape, color = container, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.tiny + 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            if (leadingDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(contentColor, CircleShape),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

/** Centered placeholder for an empty list or an unconfigured state. */
@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xlarge, vertical = Spacing.xxlarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
