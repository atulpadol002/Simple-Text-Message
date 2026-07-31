package com.atul.messageapp.ui.archive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.ui.components.ConversationCard
import com.atul.messageapp.utils.getContactName
import com.atul.messageapp.viewmodel.ArchiveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveChatsScreen(
    onBackClick: () -> Unit,
    onConversationClick: (
        Long,
        String,
        String
    ) -> Unit
) {
    val archiveViewModel: ArchiveViewModel =
        viewModel()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val conversations by
    archiveViewModel.conversations.collectAsState()

    val isLoading by
    archiveViewModel.isLoading.collectAsState()

    var conversationToUnarchive by remember {
        mutableStateOf<SmsConversation?>(null)
    }

    DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            LifecycleEventObserver { _, event ->

                if (
                    event == Lifecycle.Event.ON_RESUME
                ) {
                    archiveViewModel
                        .loadArchivedConversations()
                }
            }

        lifecycleOwner.lifecycle.addObserver(
            observer
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(
                observer
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Archive Chats")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled
                                    .ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        when {
            isLoading &&
                    conversations.isEmpty() -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            conversations.isEmpty() -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        text =
                            "No archived conversations"
                    )
                }
            }

            else -> {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(
                        items = conversations,
                        key = { conversation ->
                            conversation.threadId
                        }
                    ) { conversation ->

                        ConversationCard(
                            conversation = conversation,
                            onClick = {

                                val contactName =
                                    getContactName(
                                        context = context,
                                        phoneNumber =
                                            conversation.address
                                    )

                                onConversationClick(
                                    conversation.threadId,
                                    contactName,
                                    conversation.address
                                )
                            },
                            onLongClick = {
                                conversationToUnarchive =
                                    conversation
                            }
                        )
                    }
                }
            }
        }
    }

    conversationToUnarchive?.let { conversation ->

        AlertDialog(
            onDismissRequest = {
                conversationToUnarchive = null
            },
            title = {
                Text("Unarchive Conversation")
            },
            text = {
                Text(
                    text =
                        "Move this conversation back to Home?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {

                        archiveViewModel
                            .unarchiveConversation(
                                conversation
                            )

                        conversationToUnarchive =
                            null
                    }
                ) {
                    Text("Unarchive")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        conversationToUnarchive =
                            null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}