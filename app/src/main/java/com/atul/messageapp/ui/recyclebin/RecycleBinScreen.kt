package com.atul.messageapp.ui.recyclebin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Recycle Bin")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
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
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedConversationCard(
    conversation: DeletedConversation,
    isRestoring: Boolean,
    isProcessing: Boolean,
    onRestoreClick: () -> Unit,
    onDeleteForeverClick: () -> Unit
) {
    var showDeleteConfirmation by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 4.dp
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
