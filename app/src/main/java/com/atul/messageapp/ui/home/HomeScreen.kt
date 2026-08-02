package com.atul.messageapp.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.navigation.Routes
import com.atul.messageapp.ui.components.ConversationCard
import com.atul.messageapp.ui.components.SearchBar
import com.atul.messageapp.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isActive: Boolean,
    onNewMessageClick: () -> Unit,
    onConversationClick: (Long, String, String) -> Unit,
    onDrawerNavigate: (String) -> Unit
) {
    val homeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val conversations by homeViewModel.conversations.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val contactNames by homeViewModel.contactNames.collectAsState()
    val contactPresentations by homeViewModel.contactPresentations.collectAsState()
    val selectedIds by homeViewModel.selectedThreadIds.collectAsState()
    val pinnedIds by homeViewModel.pinnedThreadIds.collectAsState()
    val deleting by homeViewModel.isDeletingSelection.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    val allSelectedPinned = selectionMode && selectedIds.all { it in pinnedIds }

    val normalizedAddresses = remember(conversations) {
        conversations.associate { it.threadId to HomeViewModel.normalizeAddress(it.address) }
    }
    val displayNames = remember(conversations, contactNames, normalizedAddresses) {
        conversations.associate { conversation ->
            conversation.threadId to (contactNames[normalizedAddresses[conversation.threadId]] ?: conversation.address)
        }
    }
    val contactPhotos = remember(contactPresentations) {
        contactPresentations.mapValues { it.value.photo?.asImageBitmap() }
    }
    val filteredConversations by remember(conversations, searchText, displayNames) {
        derivedStateOf {
            if (searchText.isBlank()) conversations else conversations.filter { conversation ->
                val name = displayNames[conversation.threadId].orEmpty()
                name.contains(searchText, true) || conversation.address.contains(searchText, true) ||
                    conversation.body.contains(searchText, true)
            }
        }
    }
    val visibleIds = remember(filteredConversations) { filteredConversations.mapTo(linkedSetOf()) { it.threadId } }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    var isScrollingToTop by remember { mutableStateOf(false) }
    val showScrollToTop by remember(listState, selectionMode, isLoading, filteredConversations.isNotEmpty()) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 4 && !selectionMode && !isLoading && filteredConversations.isNotEmpty()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeViewModel.loadConversations()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            homeViewModel.clearSelection()
        }
    }
    LaunchedEffect(isActive) { if (!isActive) homeViewModel.clearSelection() }

    BackHandler(enabled = isActive) {
        when {
            showDeleteDialog -> showDeleteDialog = false
            selectionMode -> homeViewModel.clearSelection()
            else -> showExitDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !selectionMode,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                DrawerItem("Messages", Icons.Default.Home, true) { scope.launch { drawerState.close() } }
                DrawerItem("Archive", Icons.Default.Archive) { navigateFromDrawer(scope, drawerState, onDrawerNavigate, Routes.ArchiveChats.route) }
                DrawerItem("Theme", Icons.Default.Palette) { navigateFromDrawer(scope, drawerState, onDrawerNavigate, Routes.Theme.route) }
                DrawerItem("Scheduled SMS", Icons.Default.Schedule) { navigateFromDrawer(scope, drawerState, onDrawerNavigate, Routes.ScheduledSms.route) }
                DrawerItem("Block Numbers", Icons.Default.Block) { navigateFromDrawer(scope, drawerState, onDrawerNavigate, Routes.BlockNumbers.route) }
                DrawerItem("Starred Messages", Icons.Default.Star) { navigateFromDrawer(scope, drawerState, onDrawerNavigate, Routes.StarredMessages.route) }
                DrawerItem("Recycle Bin", Icons.Default.RestoreFromTrash) { navigateFromDrawer(scope, drawerState, onDrawerNavigate, Routes.RecycleBin.route) }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = homeViewModel::clearSelection) {
                                Icon(Icons.Default.Close, "Cancel selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                homeViewModel.setVisibleSelection(visibleIds, !allVisibleSelected)
                            }) {
                                Icon(Icons.Default.SelectAll, if (allVisibleSelected) "Deselect all" else "Select all")
                            }
                            IconButton(onClick = homeViewModel::togglePinnedSelection) {
                                Icon(Icons.Default.PushPin, if (allSelectedPinned) "Unpin" else "Pin")
                            }
                            IconButton(onClick = homeViewModel::archiveSelected) {
                                Icon(Icons.Default.Archive, "Archive selected conversations")
                            }
                            IconButton(enabled = !deleting, onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, "Delete selected conversations")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Open navigation menu")
                            }
                        },
                        title = { SearchBar(searchText, onValueChange = { searchText = it }) }
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode) Column(
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (showScrollToTop) {
                        SmallFloatingActionButton(
                            onClick = {
                                if (!isScrollingToTop) scope.launch {
                                    isScrollingToTop = true
                                    try { listState.animateScrollToItem(0) } finally { isScrollingToTop = false }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) { Icon(Icons.Default.ArrowUpward, "Scroll to top") }
                        Spacer(Modifier.height(12.dp))
                    }
                    FloatingActionButton(
                        onClick = onNewMessageClick,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) { Icon(Icons.Default.Add, "New message") }
                }
            }
        ) { paddingValues ->
            if (isLoading && conversations.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Syncing messages...")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    state = listState
                ) {
                    items(filteredConversations, key = { it.threadId }) { conversation ->
                        val selected = conversation.threadId in selectedIds
                        val displayName = displayNames[conversation.threadId] ?: conversation.address
                        ConversationCard(
                            conversation = conversation,
                            displayName = displayName,
                            selected = selected,
                            isPinned = conversation.threadId in pinnedIds,
                            contactPhoto = contactPhotos[normalizedAddresses[conversation.threadId]],
                            onClick = {
                                if (selectionMode) homeViewModel.toggleSelection(conversation.threadId)
                                else onConversationClick(conversation.threadId, displayName, conversation.address)
                            },
                            onLongClick = { homeViewModel.toggleSelection(conversation.threadId) }
                        )
                    }
                    if (filteredConversations.isEmpty() && !isLoading) item {
                        Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                            Text(if (searchText.isBlank()) "No messages found" else "No matching messages")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { if (!deleting) showDeleteDialog = false },
        title = { Text("Delete") },
        text = { Text(if (selectedIds.size == 1) "Are you sure you want to delete this conversation?" else "Are you sure you want to delete these conversations?") },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = {
                showDeleteDialog = false
                homeViewModel.deleteSelected()
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) { Text("Cancel") } }
    )

    if (showExitDialog) AlertDialog(
        onDismissRequest = { showExitDialog = false },
        title = { Text("Are you sure you want to exit?") },
        confirmButton = { TextButton(onClick = { showExitDialog = false; (context as? Activity)?.finish() }) { Text("Yes") } },
        dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("No") } }
    )
}

@Composable
private fun DrawerHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Column(Modifier.padding(start = 16.dp)) {
            Text("Messages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("SMS conversations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        icon = { Icon(icon, null) },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

private fun navigateFromDrawer(
    scope: kotlinx.coroutines.CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
    navigate: (String) -> Unit,
    route: String
) {
    scope.launch { drawerState.close(); navigate(route) }
}
