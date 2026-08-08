package com.atul.messageapp.ui.recyclebin

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
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Close
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.data.model.DeletedConversation
import com.atul.messageapp.viewmodel.RecycleBinViewModel
import com.atul.messageapp.utils.AvatarColorResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBackClick: () -> Unit
) {
    val recycleBinViewModel: RecycleBinViewModel =
        viewModel()

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
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedIds.size} selected" else "Recycle Bin") },
                navigationIcon = {
                        IconButton(onClick = if (selectionMode) ({ selectedIds = emptySet() }) else onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = { if (selectionMode) {
                    val all = deletedConversations.isNotEmpty() && deletedConversations.all { it.recycleBinId in selectedIds }
                    IconButton(onClick = { val ids = deletedConversations.map { it.recycleBinId }.toSet(); selectedIds = if (all) selectedIds - ids else selectedIds + ids }) { Icon(Icons.Default.Check, if (all) "Deselect all" else "Select all") }
                    IconButton(onClick = { dialog = "restore" }) { Icon(Icons.Default.RestoreFromTrash, "Restore") }
                    IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Default.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
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
                    Text("Recycle Bin is empty")
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
                                recycleBinViewModel.restoreConversation(
                                    conversation.recycleBinId
                                )
                            },
                            onDeleteForeverClick = {
                                recycleBinViewModel
                                    .deleteConversationPermanently(
                                        conversation.recycleBinId
                                    )
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
    dialog?.let { action -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(if (action == "restore") "Restore conversations?" else "Delete forever?") }, text = { Text(if (action == "restore") "Restore the selected conversations?" else "These conversations cannot be restored after permanent deletion.") }, confirmButton = { Button(onClick = { val ids = selectedIds; dialog = null; selectedIds = emptySet(); if (action == "restore") recycleBinViewModel.restoreSelected(ids) else recycleBinViewModel.deleteSelected(ids) }) { Text(if (action == "restore") "Restore" else "Delete Forever") } }, dismissButton = { Button(onClick = { dialog = null }) { Text("Cancel") } }) }
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
                text = "Deleted ${formatDeletedAt(conversation.deletedAt)}",
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
                            "Restoring..."
                        } else {
                            "Restore"
                        }
                    )
                }

                Button(
                    onClick = {
                        showDeleteConfirmation = true
                    },
                    enabled = !isProcessing
                ) {
                    Text("Delete Forever")
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
                Text("Delete forever?")
            },
            text = {
                Text(
                    "This conversation cannot be restored after permanent deletion."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteForeverClick()
                    }
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatDeletedAt(
    deletedAt: Long
): String = Instant.ofEpochMilli(deletedAt)
    .atZone(ZoneId.systemDefault())
    .format(DELETED_AT_FORMATTER)

private val DELETED_AT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
