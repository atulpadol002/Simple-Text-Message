package com.ap.simpletextmessage.ui.recyclebin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ap.simpletextmessage.data.model.DeletedConversation
import com.ap.simpletextmessage.viewmodel.RecycleBinViewModel
import com.ap.simpletextmessage.utils.AvatarColorResolver
import java.text.DateFormat
import java.util.Date
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.AdRuntime
import com.ap.simpletextmessage.ads.RewardedAdManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBackClick: () -> Unit
) {
    val recycleBinViewModel: RecycleBinViewModel =
        viewModel()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adsReady by AdRuntime.mobileAdsReady.collectAsState()

    val deletedConversations by
    recycleBinViewModel.deletedConversations.collectAsState()

    val isLoading by
    recycleBinViewModel.isLoading.collectAsState()

    val restoringConversationIds by
    recycleBinViewModel.restoringConversationIds.collectAsState()

    val processingConversationIds by
    recycleBinViewModel.processingConversationIds.collectAsState()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val selectionMode = selectedIds.isNotEmpty()
    androidx.compose.runtime.LaunchedEffect(adConfig, adsReady) {
        if (activity != null && adsReady) {
            RewardedAdManager.preload(activity, AdPlacement.REWARDED_RESTORE)
            RewardedAdManager.preload(activity, AdPlacement.REWARDED_DELETE)
        }
    }

    fun performWithReward(placement: AdPlacement, action: () -> Unit) {
        val host = activity
        if (host == null) {
            RewardedAdManager.logActivityUnavailable(placement)
            return
        }
        RewardedAdManager.showOrFallback(
            activity = host,
            placement = placement,
            onReward = action,
            onBypass = action,
            onUnavailable = {}
        )
    }
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) pluralStringResource(R.plurals.selected_count, selectedIds.size, selectedIds.size) else stringResource(R.string.recycle_bin)) },
                navigationIcon = {
                        IconButton(onClick = if (selectionMode) ({ selectedIds = emptySet() }) else onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = { if (selectionMode) {
                    val all = deletedConversations.isNotEmpty() && deletedConversations.all { it.recycleBinId in selectedIds }
                    IconButton(onClick = { val ids = deletedConversations.map { it.recycleBinId }.toSet(); selectedIds = if (all) selectedIds - ids else selectedIds + ids }) { Icon(Icons.Default.Check, stringResource(if (all) R.string.deselect_all else R.string.select_all)) }
                    IconButton(onClick = { dialog = "restore" }) { Icon(Icons.Default.RestoreFromTrash, stringResource(R.string.restore)) }
                    IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.DeleteForever, stringResource(R.string.delete_forever), tint = MaterialTheme.colorScheme.error) }
                } }
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            deletedConversations.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.recycle_bin_empty))
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = deletedConversations,
                        key = { conversation ->
                            conversation.recycleBinId
                        }
                    ) { conversation ->
                        DeletedConversationCard(
                            conversation = conversation,
                            isRestoring = conversation.recycleBinId in
                                    restoringConversationIds,
                            isProcessing = conversation.recycleBinId in
                                    processingConversationIds,
                            onRestoreClick = {
                                performWithReward(AdPlacement.REWARDED_RESTORE) {
                                    recycleBinViewModel.restoreConversation(conversation.recycleBinId)
                                }
                            },
                            onDeleteForeverClick = {
                                performWithReward(AdPlacement.REWARDED_DELETE) {
                                    recycleBinViewModel.deleteConversationPermanently(
                                        conversation.recycleBinId
                                    )
                                }
                            },
                            selected = conversation.recycleBinId in selectedIds,
                            onClick = { if (selectionMode) selectedIds = if (conversation.recycleBinId in selectedIds) selectedIds - conversation.recycleBinId else selectedIds + conversation.recycleBinId },
                            onLongClick = { selectedIds = selectedIds + conversation.recycleBinId }
                        )
                    }
                }
            }
        }
    }
    dialog?.let { action -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(if (action == "restore") R.string.restore_conversations_question else R.string.delete_forever_question)) }, text = { Text(stringResource(if (action == "restore") R.string.restore_selected_conversations else R.string.permanent_delete_conversations_warning)) }, confirmButton = { Button(onClick = { val ids = selectedIds; dialog = null; selectedIds = emptySet(); if (action == "restore") performWithReward(AdPlacement.REWARDED_RESTORE) { recycleBinViewModel.restoreSelected(ids) } else performWithReward(AdPlacement.REWARDED_DELETE) { recycleBinViewModel.deleteSelected(ids) } }) { Text(stringResource(if (action == "restore") R.string.restore else R.string.delete_forever)) } }, dismissButton = { Button(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
private fun DeletedConversationCard(
    conversation: DeletedConversation,
    isRestoring: Boolean,
    isProcessing: Boolean,
    onRestoreClick: () -> Unit,
    onDeleteForeverClick: () -> Unit
    , selected: Boolean = false, onClick: () -> Unit = {}, onLongClick: () -> Unit = {}
) {
    var showDeleteConfirmation by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 4.dp
            )
        , colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            val seed = conversation.cachedDisplayName ?: conversation.address
            val avatarColor = AvatarColorResolver.background(seed, MaterialTheme.colorScheme)
            Box(Modifier.size(42.dp).clip(CircleShape).background(avatarColor), contentAlignment = Alignment.Center) {
                Text(seed.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?", color = AvatarColorResolver.foreground(avatarColor, MaterialTheme.colorScheme), fontWeight = FontWeight.SemiBold)
            }
            Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            conversation.cachedDisplayName
                ?.takeIf { displayName ->
                    displayName.isNotBlank()
                }
                ?.let { displayName ->
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

            Text(
                text = conversation.address,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = stringResource(R.string.deleted_at, formatDeletedAt(conversation.deletedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestoreClick,
                    enabled = !isProcessing
                ) {
                    Text(
                        if (isRestoring) {
                            stringResource(R.string.restoring)
                        } else {
                            stringResource(R.string.restore)
                        }
                    )
                }

                Button(
                    onClick = {
                        showDeleteConfirmation = true
                    },
                    enabled = !isProcessing
                ) {
                    Text(stringResource(R.string.delete_forever))
                }
            }
        }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
            },
            title = {
                Text(stringResource(R.string.delete_forever_question))
            },
            text = {
                Text(
                    stringResource(R.string.permanent_delete_conversation_warning)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteForeverClick()
                    }
                ) {
                    Text(stringResource(R.string.delete_forever))
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun formatDeletedAt(deletedAt: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(deletedAt))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
