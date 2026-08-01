package com.atul.messageapp.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.navigation.Routes
import com.atul.messageapp.ui.components.ConversationCard
import com.atul.messageapp.ui.components.SearchBar
import com.atul.messageapp.ui.components.TopBar
import com.atul.messageapp.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue

@Composable
fun HomeScreen(
    isActive: Boolean,
    onNewMessageClick: () -> Unit,
    onConversationClick: (
        Long,
        String,
        String
    ) -> Unit,
    onDrawerNavigate: (String) -> Unit
) {
    val homeViewModel: HomeViewModel =
        viewModel()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val coroutineScope =
        rememberCoroutineScope()

    val conversations by
    homeViewModel.conversations.collectAsState()

    val isLoading by
    homeViewModel.isLoading.collectAsState()

    val contactNames by
    homeViewModel.contactNames.collectAsState()

    val deletingConversationIds by
    homeViewModel.deletingConversationIds.collectAsState()

    var searchText by remember {
        mutableStateOf("")
    }

    var showExitDialog by remember {
        mutableStateOf(false)
    }

    var conversationToDelete by remember {
        mutableStateOf<SmsConversation?>(null)
    }
    var conversationToArchive by remember {
        mutableStateOf<SmsConversation?>(null)
    }

    val filteredConversations =
        remember(
            conversations,
            searchText,
            contactNames
        ) {
            if (searchText.isBlank()) {
                conversations
            } else {
                conversations.filter { conversation ->

                    val contactName =
                        contactNames[
                            HomeViewModel.normalizeAddress(
                                conversation.address
                            )
                        ].orEmpty()

                    contactName.contains(
                        searchText,
                        ignoreCase = true
                    ) ||
                            conversation.address.contains(
                                searchText,
                                ignoreCase = true
                            ) ||
                            conversation.body.contains(
                                searchText,
                                ignoreCase = true
                            )
                }
            }
        }

    DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            LifecycleEventObserver { _, event ->

                if (
                    event == Lifecycle.Event.ON_RESUME
                ) {
                    homeViewModel.loadConversations()
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

    BackHandler(enabled = isActive) {
        showExitDialog = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Text(
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    ),
                    text = "Message App"
                )

                HorizontalDivider()

                NavigationDrawerItem(
                    label = {
                        Text("Home")
                    },
                    selected = true,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Archive Chats")
                    },
                    selected = false,
                    icon = {
                        Text("📦")
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onDrawerNavigate(
                                Routes.ArchiveChats.route
                            )
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Theme")
                    },
                    selected = false,
                    icon = {
                        Text("🎨")
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()

                            onDrawerNavigate(
                                Routes.Theme.route
                            )
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Scheduled SMS")
                    },
                    selected = false,
                    icon = {
                        Text("📅")
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onDrawerNavigate(
                                Routes.ScheduledSms.route
                            )
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Block Numbers")
                    },
                    selected = false,
                    icon = {
                        Text("🔒")
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onDrawerNavigate(
                                Routes.BlockNumbers.route
                            )
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Starred Messages")
                    },
                    selected = false,
                    icon = {
                        Text("⭐")
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onDrawerNavigate(
                                Routes.StarredMessages.route
                            )
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Recycle Bin")
                    },
                    selected = false,
                    icon = {
                        Text("🗑️")
                    },
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onDrawerNavigate(
                                Routes.RecycleBin.route
                            )
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = "Messages",
                    onNavigationClick = {
                        coroutineScope.launch {
                            drawerState.open()
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNewMessageClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Message"
                    )
                }
            }
        ) { paddingValues ->

            if (
                isLoading &&
                conversations.isEmpty()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text("Syncing messages...")
                    }
                }

            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    item {
                        SearchBar(
                            searchText = searchText,
                            onValueChange = { newText ->
                                searchText = newText
                            }
                        )
                    }

                    items(
                        items = filteredConversations,
                        key = { conversation ->
                            conversation.threadId
                        }
                    ) { conversation ->

                        ConversationCard(
                            conversation = conversation,
                            onClick = {
                                val contactName =
                                    contactNames[
                                        HomeViewModel.normalizeAddress(
                                            conversation.address
                                        )
                                    ] ?: conversation.address

                                onConversationClick(
                                    conversation.threadId,
                                    contactName,
                                    conversation.address
                                )
                            },
                            onLongClick = {
                                conversationToArchive =
                                    conversation
                            }
                        )
                    }

                    if (
                        filteredConversations.isEmpty() &&
                        !isLoading
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize(),
                                contentAlignment =
                                    Alignment.Center
                            ) {
                                Text(
                                    text =
                                        if (
                                            searchText.isBlank()
                                        ) {
                                            "No messages found"
                                        } else {
                                            "No matching messages"
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    conversationToArchive?.let { conversation ->

        AlertDialog(
            onDismissRequest = {
                conversationToArchive = null
            },
            title = {
                Text("Conversation")
            },
            text = {
                Text(
                    "Choose an action for this conversation."
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        homeViewModel.archiveConversation(
                            conversation
                        )

                        conversationToArchive = null
                    }
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {

                        conversationToDelete =
                            conversation

                        conversationToArchive = null
                    }
                ) {
                    Text("Delete")
                }
            }
        )
    }
    conversationToDelete?.let { conversation ->

        AlertDialog(
            onDismissRequest = {
                conversationToDelete = null
            },
            title = {
                Text(
                    text = "Delete Conversation"
                )
            },
            text = {
                Text(
                    text =
                        "Delete messages from ${conversation.address}?"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = conversation.threadId !in
                            deletingConversationIds,
                    onClick = {
                        homeViewModel.deleteConversation(
                            conversation
                        )

                        conversationToDelete = null
                    }
                ) {
                    Text(
                        text = "Delete"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        conversationToDelete = null
                    }
                ) {
                    Text(
                        text = "Cancel"
                    )
                }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
            },
            title = {
                Text("Are you sure you want to exit?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                    }
                ) {
                    Text("No")
                }
            }
        )
    }
}
