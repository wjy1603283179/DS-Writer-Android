package app.dswriter.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dswriter.R
import app.dswriter.domain.generation.GenerationTaskStatus
import app.dswriter.domain.model.ConversationMessage
import app.dswriter.domain.model.ConversationMessageStatus
import app.dswriter.domain.model.ConversationRole
import app.dswriter.service.NotificationPermissionPolicy
import app.dswriter.ui.components.EmptyState
import app.dswriter.ui.components.LocalImageThumbnail
import app.dswriter.ui.theme.DSTheme
import app.dswriter.ui.theme.Dimens
import app.dswriter.ui.theme.PillShape
import app.dswriter.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenDataSafety: () -> Unit,
    requestedConversationId: String? = null,
    onRequestedConversationOpened: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(requestedConversationId) {
        requestedConversationId?.let {
            viewModel.selectConversation(it)
            onRequestedConversationOpened()
        }
    }
    var pendingSubmission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingSubmission?.invoke(); pendingSubmission = null
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        uri -> uri?.let { viewModel.selectImage(it.toString()) }
    }
    fun submitWithPermission(action: () -> Unit) {
        if (NotificationPermissionPolicy.requiresRuntimePermission(Build.VERSION.SDK_INT) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSubmission = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
    DisposableEffect(viewModel, state.selectedConversationId) {
        viewModel.setVisible(true)
        onDispose { viewModel.setVisible(false) }
    }
    ChatScreen(
        state = state,
        onNewConversation = viewModel::newConversation,
        onSelectConversation = viewModel::selectConversation,
        onRenameConversation = viewModel::renameConversation,
        onTrashConversation = viewModel::moveConversationToTrash,
        onSearchChanged = viewModel::updateSearchQuery,
        onOpenSettings = onOpenSettings,
        onOpenTasks = onOpenTasks,
        onOpenDataSafety = onOpenDataSafety,
        onDraftChanged = viewModel::updateDraft,
        onSend = { submitWithPermission(viewModel::send) },
        onStop = viewModel::stop,
        onLoadOlder = viewModel::loadOlder,
        onRegenerate = { submitWithPermission(viewModel::regenerate) },
        onProviderSelected = viewModel::selectProvider,
        onDismissError = viewModel::clearError,
        onPickImage = {
            imagePickerLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onRemoveImage = viewModel::removePendingImage,
        onSwitchToVision = {},
        onDismissMigrationNotice = viewModel::dismissMigrationNotice,
        onRequestRecap = viewModel::generateRecap,
        onUseRecap = viewModel::startNewConversationFromRecap,
        onDismissRecap = viewModel::dismissRecap,
        onDismissContextSuggestion = viewModel::dismissContextSuggestion,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onNewConversation: () -> Unit = {},
    onSelectConversation: (String) -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onTrashConversation: (String) -> Unit = {},
    onSearchChanged: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenDataSafety: () -> Unit = {},
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onLoadOlder: () -> Unit,
    onRegenerate: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onDismissError: () -> Unit,
    onPickImage: () -> Unit = {},
    onRemoveImage: () -> Unit = {},
    onSwitchToVision: () -> Unit = {},
    onDismissMigrationNotice: () -> Unit = {},
    onRequestRecap: () -> Unit = {},
    onUseRecap: (String) -> Unit = {},
    onDismissRecap: () -> Unit = {},
    onDismissContextSuggestion: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showRecapConsent by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                state = state,
                onNewConversation = { onNewConversation(); scope.launch { drawerState.close() } },
                onSelectConversation = { onSelectConversation(it); scope.launch { drawerState.close() } },
                onRenameConversation = { id, title -> renameTarget = id to title },
                onTrashConversation = onTrashConversation,
                onSearchChanged = onSearchChanged,
                onOpenSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
                onOpenTasks = { scope.launch { drawerState.close() }; onOpenTasks() },
                onOpenDataSafety = { scope.launch { drawerState.close() }; onOpenDataSafety() },
            )
        },
    ) {
        ChatWorkspace(
            state = state,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onDraftChanged = onDraftChanged,
            onSend = onSend,
            onStop = onStop,
            onLoadOlder = onLoadOlder,
            onRegenerate = onRegenerate,
            onProviderSelected = onProviderSelected,
            onDismissError = onDismissError,
            onPickImage = onPickImage,
            onRemoveImage = onRemoveImage,
            onSwitchToVision = onSwitchToVision,
            onTrashConversation = onTrashConversation,
            onRequestRename = { id, title -> renameTarget = id to title },
            onRequestRecap = { showRecapConsent = true },
            onDismissContextSuggestion = onDismissContextSuggestion,
            onOpenSettings = onOpenSettings,
        )
    }
    renameTarget?.let { target ->
        RenameDialog(target.second, { renameTarget = null }) { value ->
            onRenameConversation(target.first, value); renameTarget = null
        }
    }
    if (state.migrationNoticeVisible) {
        AlertDialog(
            onDismissRequest = onDismissMigrationNotice,
            title = { Text(stringResource(R.string.data_reset_title)) },
            text = { Text(stringResource(R.string.data_reset_message)) },
            confirmButton = { TextButton(onClick = onDismissMigrationNotice) { Text(stringResource(R.string.confirm)) } },
        )
    }
    if (showRecapConsent) {
        RecapConsentDialog(
            isGenerating = state.recap is RecapState.Generating,
            onDismiss = { showRecapConsent = false },
            onConfirm = { showRecapConsent = false; onRequestRecap() },
        )
    }
    (state.recap as? RecapState.Ready)?.let { ready ->
        RecapDialog(
            recap = ready,
            onDismiss = onDismissRecap,
            onUseAsNewConversation = onUseRecap,
        )
    }
}

@Composable
private fun ConversationDrawer(
    state: ChatUiState,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTrashConversation: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenDataSafety: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 340.dp).fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = Spacing.regular)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(
                        top = Spacing.large,
                        bottom = Spacing.regular,
                    ),
                )
                Surface(
                    onClick = onNewConversation,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.regular, vertical = Spacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.create_conversation),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = Spacing.small),
                        )
                    }
                }
                SearchField(state.searchQuery, onSearchChanged)
                DrawerSectionLabel(stringResource(R.string.drawer_section_conversations))
            }

            if (state.conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.conversations_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.medium),
                )
            }

            LazyColumn(Modifier.weight(1f)) {
                items(state.conversations, key = { it.id }) { conversation ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    ConversationDrawerRow(
                        title = conversation.title,
                        selected = conversation.id == state.selectedConversationId,
                        unread = conversation.id in state.unreadConversationIds,
                        onClick = { onSelectConversation(conversation.id) },
                        trailing = {
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(Dimens.touchTarget),
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        stringResource(R.string.conversation_menu),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                DropdownMenu(menuExpanded, { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rename)) },
                                        onClick = {
                                            menuExpanded = false
                                            onRenameConversation(conversation.id, conversation.title)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.move_to_trash)) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onTrashConversation(conversation.id)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(vertical = Spacing.small)) {
                DrawerSectionLabel(stringResource(R.string.drawer_section_tools))
                DrawerActionRow(
                    label = stringResource(R.string.task_center),
                    badge = state.unreadConversationIds.size.takeIf { it > 0 },
                    onClick = onOpenTasks,
                )
                DrawerActionRow(
                    label = stringResource(R.string.data_safety_title),
                    onClick = onOpenDataSafety,
                )
                DrawerActionRow(
                    label = stringResource(R.string.settings),
                    icon = Icons.Default.Settings,
                    onClick = onOpenSettings,
                )
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
        singleLine = true,
        shape = PillShape,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        placeholder = {
            Text(
                text = stringResource(R.string.search_conversations),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Spacing.regular,
            top = Spacing.small,
            bottom = Spacing.small,
        ),
    )
}

@Composable
private fun ConversationDrawerRow(
    title: String,
    selected: Boolean,
    unread: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.small, vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Spacing.medium),
            )
            if (unread) {
                Box(
                    Modifier
                        .padding(start = Spacing.small)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            trailing()
        }
    }
}

@Composable
private fun DrawerActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badge: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.regular),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = if (icon != null) Spacing.medium else 0.dp),
        )
        if (badge != null) {
            Surface(shape = PillShape, color = MaterialTheme.colorScheme.primary) {
                Text(
                    text = badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.small, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatWorkspace(
    state: ChatUiState,
    onOpenDrawer: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onLoadOlder: () -> Unit,
    onRegenerate: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onDismissError: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSwitchToVision: () -> Unit,
    onTrashConversation: (String) -> Unit,
    onRequestRename: (String, String) -> Unit,
    onRequestRecap: () -> Unit,
    onDismissContextSuggestion: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var providerPickerExpanded by remember { mutableStateOf(false) }
    var conversationMenuExpanded by remember { mutableStateOf(false) }
    val activeProvider = state.activeProvider

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(
                title = state.conversation?.title.orEmpty(),
                modelLabel = activeProvider?.let { provider ->
                    listOfNotNull(
                        provider.name.takeIf { it.isNotBlank() },
                        provider.modelId.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                }.orEmpty(),
                isLocalModel = false,
                onOpenDrawer = onOpenDrawer,
                onOpenModelPicker = { providerPickerExpanded = true },
                conversationMenuExpanded = conversationMenuExpanded,
                onConversationMenuExpandedChange = { conversationMenuExpanded = it },
                onRename = {
                    state.conversation?.let { onRequestRename(it.id, it.title) }
                },
                onTrash = { state.selectedConversationId?.let(onTrashConversation) },
                onRequestRecap = onRequestRecap,
            )
            MessageList(state, onLoadOlder, onRegenerate, Modifier.weight(1f))
            DebugRequestInspector(state.requestSnapshot)
            Composer(
                state, onDraftChanged, onSend, onStop, onDismissError, onPickImage, onRemoveImage, onSwitchToVision,
                onRequestRecap, onDismissContextSuggestion,
            )
        }
    }

    if (providerPickerExpanded) {
        ProviderPickerSheet(
            providers = state.providers,
            activeProviderId = state.activeProvider?.id,
            onSelect = { onProviderSelected(it); providerPickerExpanded = false },
            onManage = { providerPickerExpanded = false; onOpenSettings() },
            onDismiss = { providerPickerExpanded = false },
        )
    }
}

@Composable
private fun ChatHeader(
    title: String,
    modelLabel: String,
    isLocalModel: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenModelPicker: () -> Unit,
    conversationMenuExpanded: Boolean,
    onConversationMenuExpandedChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onTrash: () -> Unit,
    onRequestRecap: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(Dimens.touchTarget)) {
                    Icon(Icons.Default.Menu, stringResource(R.string.open_navigation))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.small),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ModelChip(
                        label = modelLabel,
                        isLocal = isLocalModel,
                        onClick = onOpenModelPicker,
                        modifier = Modifier.padding(top = Spacing.tiny),
                    )
                }
                Box {
                    IconButton(
                        onClick = { onConversationMenuExpandedChange(true) },
                        modifier = Modifier.size(Dimens.touchTarget),
                    ) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.conversation_menu))
                    }
                    DropdownMenu(
                        conversationMenuExpanded,
                        { onConversationMenuExpandedChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { onConversationMenuExpandedChange(false); onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recap_menu_item)) },
                            leadingIcon = { Icon(Icons.Default.Compress, null) },
                            onClick = { onConversationMenuExpandedChange(false); onRequestRecap() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_trash)) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                            onClick = { onConversationMenuExpandedChange(false); onTrash() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tappable model indicator.
 *
 * This is the deliberate affordance the previous build lacked: the model is shown as a control
 * with a disclosure arrow next to the conversation title, so pickers are findable without
 * guessing that the title area is a menu.
 */
@Composable
private fun ModelChip(
    label: String,
    isLocal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (label.isEmpty()) return
    val accents = DSTheme.accents
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (isLocal) accents.localContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 26.dp),
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.medium,
                end = Spacing.small,
                top = Spacing.tiny,
                bottom = Spacing.tiny,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.tiny),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isLocal) accents.onLocalContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.select_model),
                tint = if (isLocal) accents.onLocalContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun MessageList(
    state: ChatUiState,
    onLoadOlder: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var autoFollow by rememberSaveable(state.selectedConversationId) { mutableStateOf(true) }
    val last = state.messages.lastOrNull()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 2) }
            .distinctUntilChanged().collect { (scrolling, nearBottom) -> if (scrolling) autoFollow = nearBottom }
    }
    LaunchedEffect(last?.id, last?.content?.length, last?.reasoningContent?.length) {
        if (autoFollow && state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = Spacing.regular,
                vertical = Spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xlarge),
        ) {
            if (state.canLoadOlder) item("load") {
                TextButton(onClick = onLoadOlder, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.load_older_messages))
                }
            }
            if (state.messages.isEmpty()) item("empty") {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.app_name),
                        description = stringResource(R.string.empty_conversation_hint),
                    )
                }
            }
            items(state.messages, key = ConversationMessage::id) { MessageItem(it, onRegenerate) }
        }
        if (!autoFollow && state.messages.isNotEmpty()) {
            Surface(
                onClick = {
                    autoFollow = true
                },
                shape = PillShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.regular),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.regular, vertical = Spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.back_to_bottom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSwitchToVision: () -> Unit,
    onRequestRecap: () -> Unit,
    onDismissContextSuggestion: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            // Optional, opt-in prompt. It only appears when the user switched it on in Settings,
            // and it only offers an action: nothing is sent from here automatically.
            state.contextPressurePercent?.let { percent ->
                ContextSuggestionCard(
                    percent = percent,
                    onRecap = onRequestRecap,
                    onDismiss = onDismissContextSuggestion,
                )
            }
            state.error?.let { error ->
                ComposerBanner(
                    text = stringResource(error.stringResource()),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onDismiss = onDismissError,
                )
            }
            when (state.generationStatus) {
                GenerationTaskStatus.QUEUED -> ComposerBanner(
                    text = stringResource(R.string.status_queued),
                    container = DSTheme.accents.queuedContainer,
                    content = DSTheme.accents.onQueuedContainer,
                )
                GenerationTaskStatus.RUNNING -> ComposerBanner(
                    text = stringResource(R.string.status_streaming),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                else -> Unit
            }
            when (val pending = state.pendingImage) {
                PendingImageState.Preparing -> Text(
                    text = stringResource(R.string.image_preparing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.small),
                )
                is PendingImageState.Ready -> PendingImagePreview(pending.image, onRemoveImage)
                null -> Unit
            }
            if (state.needsVisionModel) {
                ComposerBanner(
                    text = stringResource(R.string.vision_model_required),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionLabel = stringResource(R.string.select_model),
                    onAction = onSwitchToVision,
                )
            }
            ComposerField(state, onDraftChanged, onSend, onStop, onPickImage)
        }
    }
}

/** Prominent, opt-in prompt shown when a conversation nears its context limit. */
@Composable
private fun ContextSuggestionCard(
    percent: Int,
    onRecap: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.regular),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.recap_suggestion_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.recap_suggestion_message, percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onRecap,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Text(stringResource(R.string.recap_suggestion_action))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Text(
                        text = stringResource(R.string.recap_suggestion_dismiss),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerBanner(
    text: String,
    container: Color,
    content: Color,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Spacing.medium, top = Spacing.small, bottom = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.tiny),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = content)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(Dimens.touchTarget)) {
                    Icon(Icons.Default.Close, stringResource(R.string.dismiss), tint = content)
                }
            }
        }
    }
}

@Composable
private fun ComposerField(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
) {
    val canSend = (state.draft.isNotBlank() || state.pendingImage is PendingImageState.Ready) &&
        !state.needsVisionModel
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.small),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = onPickImage,
                enabled = state.pendingImage !is PendingImageState.Preparing,
                modifier = Modifier.size(Dimens.touchTarget),
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    stringResource(R.string.choose_image),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextField(
                value = state.draft,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(R.string.message_input),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                minLines = 1,
                maxLines = 7,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            FilledIconButton(
                onClick = if (state.isGenerating) onStop else onSend,
                enabled = state.isGenerating || canSend,
                modifier = Modifier.size(Dimens.touchTarget),
            ) {
                Icon(
                    if (state.isGenerating) Icons.Default.Stop else Icons.Default.Send,
                    stringResource(if (state.isGenerating) R.string.stop_generation else R.string.send_message),
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_conversation)) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PendingImagePreview(image: app.dswriter.domain.model.PreparedImageAttachment, onRemove: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocalImageThumbnail(image.localUri, image.displayName)
            Text(
                text = image.displayName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_image)) }
        }
    }
}

private fun ChatError.stringResource(): Int = when (this) {
    ChatError.NO_PROVIDER -> R.string.no_provider_configured
    ChatError.API_KEY_REQUIRED -> R.string.api_key_required
    ChatError.UNAUTHORIZED -> R.string.connection_unauthorized
    ChatError.RATE_LIMITED -> R.string.connection_rate_limited
    ChatError.TIMEOUT -> R.string.connection_timeout
    ChatError.NETWORK -> R.string.connection_network_error
    ChatError.SERVER -> R.string.connection_server_error
    ChatError.BUSY -> R.string.generation_busy
    ChatError.IMAGE_INVALID -> R.string.image_invalid
    ChatError.VISION_MODEL_REQUIRED -> R.string.vision_model_required
    ChatError.ATTACHMENT_UNAVAILABLE -> R.string.attachment_unavailable
    ChatError.FAILED -> R.string.generation_failed
    ChatError.RECAP_NOT_ENOUGH_CONTENT -> R.string.recap_not_enough_content
    ChatError.RECAP_FAILED -> R.string.recap_failed
}
