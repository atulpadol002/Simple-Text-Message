package com.atul.messageapp.ui.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.ui.components.ConversationCard
import com.atul.messageapp.viewmodel.ArchiveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveChatsScreen(
    onBackClick: () -> Unit,
    onConversationClick: (Long, String, String) -> Unit
) {
    val viewModel: ArchiveViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val conversations by viewModel.conversations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedIds by viewModel.selectedThreadIds.collectAsState()
    val contactNames by viewModel.contactNames.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadArchivedConversations()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearSelection()
        }
    }

    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedIds.size} selected" else "Archive Chats") },
                navigationIcon = {
                    IconButton(onClick = if (selectionMode) viewModel::clearSelection else onBackClick) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            if (selectionMode) "Cancel selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = viewModel::unarchiveSelected) {
                            Icon(Icons.Default.Unarchive, "Unarchive selected conversations")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            isLoading && conversations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(paddingValues), Alignment.Center
            ) { CircularProgressIndicator() }
            conversations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(paddingValues), Alignment.Center
            ) { Text("No archived conversations") }
            else -> LazyColumn(Modifier.fillMaxSize().padding(paddingValues)) {
                items(conversations, key = { it.threadId }) { conversation ->
                    val selected = conversation.threadId in selectedIds
                    ConversationCard(
                        conversation = conversation,
                        displayName = contactNames[conversation.threadId] ?: conversation.address,
                        selected = selected,
                        onClick = {
                            if (selectionMode) viewModel.toggleSelection(conversation.threadId)
                            else onConversationClick(
                                conversation.threadId,
                                contactNames[conversation.threadId] ?: conversation.address,
                                conversation.address
                            )
                        },
                        onLongClick = { viewModel.toggleSelection(conversation.threadId) }
                    )
                }
            }
        }
    }
}
