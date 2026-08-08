package com.ap.messages.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ap.messages.navigation.Routes
import com.ap.messages.R
import com.ap.messages.ui.components.ConversationCard
import com.ap.messages.ui.components.SearchBar
import com.ap.messages.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isActive: Boolean,
    navigationInProgress: Boolean,
    onHomeResumed: () -> Unit,
    onNewMessageClick: () -> Unit,
    onConversationClick: (Long, String, String) -> Unit,
    onDrawerNavigate: (String) -> Unit
) {
    val homeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp.dp * 0.6f).coerceIn(240.dp, 360.dp)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var drawerJob by remember { mutableStateOf<Job?>(null) }
    var lifecycleState by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState)
    }
    val listState = rememberLazyListState()
    val conversations by homeViewModel.conversations.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val contactNames by homeViewModel.contactNames.collectAsState()
    val contactPresentations by homeViewModel.contactPresentations.collectAsState()
    val selectedIds by homeViewModel.selectedThreadIds.collectAsState()
    val pinnedIds by homeViewModel.pinnedThreadIds.collectAsState()
    val deleting by homeViewModel.isDeletingSelection.collectAsState()
    val scrollToTopRequestId by homeViewModel.scrollToTopRequestId.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    val allSelectedPinned = selectionMode && selectedIds.all { it in pinnedIds }
    val canInteract = isActive &&
            lifecycleState == Lifecycle.State.RESUMED &&
            !navigationInProgress
    val newMessageFabContainerColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val newMessageFabContentColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }


    val displayNames = remember(conversations, contactNames) {
        conversations.associate { conversation ->
            conversation.threadId to (contactNames[conversation.threadId] ?: conversation.address)
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
    var isScrollingToTop by remember { mutableStateOf(false) }
    val showScrollToTop by remember(listState, selectionMode, isLoading, filteredConversations.isNotEmpty()) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 4 && !selectionMode && !isLoading && filteredConversations.isNotEmpty()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleState = lifecycleOwner.lifecycle.currentState
            if (event == Lifecycle.Event.ON_RESUME) {
                onHomeResumed()
                homeViewModel.loadConversations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            drawerJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            homeViewModel.clearSelection()
        }
    }
    LaunchedEffect(isActive) { if (!isActive) homeViewModel.clearSelection() }

    LaunchedEffect(scrollToTopRequestId, canInteract, conversations.isNotEmpty()) {
        val requestId = scrollToTopRequestId ?: return@LaunchedEffect
        if (!canInteract || conversations.isEmpty()) return@LaunchedEffect

        homeViewModel.consumeScrollToTopRequest(requestId)
        if (listState.isScrollInProgress || listState.firstVisibleItemIndex == 0) {
            return@LaunchedEffect
        }
        isScrollingToTop = true
        try {
            listState.animateScrollToItem(0)
        } finally {
            isScrollingToTop = false
        }
    }

    fun closeDrawer(afterClose: (() -> Unit)? = null) {
        drawerJob?.cancel()
        drawerJob = scope.launch {
            drawerState.close()
            afterClose?.invoke()
        }
    }

    fun requestDrawerOpen() {
        if (!canInteract || drawerState.isOpen) return
        focusManager.clearFocus()
        keyboardController?.hide()
        drawerJob?.cancel()
        drawerJob = scope.launch { drawerState.open() }
    }

    BackHandler(enabled = canInteract) {
        when {
            showDeleteDialog -> showDeleteDialog = false
            selectionMode -> homeViewModel.clearSelection()
            else -> showExitDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = canInteract && !selectionMode,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerHeader()
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                DrawerItem("Messages", Icons.Default.Home, true) { closeDrawer() }
                DrawerItem("Archive", Icons.Default.Archive) { closeDrawer { onDrawerNavigate(Routes.ArchiveChats.route) } }
                DrawerItem("Theme", Icons.Default.Palette) { closeDrawer { onDrawerNavigate(Routes.Theme.route) } }
                DrawerItem("Scheduled SMS", Icons.Default.Schedule) { closeDrawer { onDrawerNavigate(Routes.ScheduledSms.route) } }
                DrawerItem("Block Numbers", Icons.Default.Block) { closeDrawer { onDrawerNavigate(Routes.BlockNumbers.route) } }
                DrawerItem("Starred Messages", Icons.Default.Star) { closeDrawer { onDrawerNavigate(Routes.StarredMessages.route) } }
                DrawerItem("Recycle Bin", Icons.Default.RestoreFromTrash) { closeDrawer { onDrawerNavigate(Routes.RecycleBin.route) } }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text(selectedIds.size.toString()) },
                        navigationIcon = {
                            IconButton(onClick = homeViewModel::clearSelection) {
                                Icon(Icons.Default.Close, "Cancel selection")
                            }
                        },
                        actions = {
                            val visibleIds = filteredConversations.map { it.threadId }.toSet()
                            val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
                            IconButton(onClick = { homeViewModel.setVisibleSelection(visibleIds, !allVisibleSelected) }) {
                                Icon(Icons.Default.Check, if (allVisibleSelected) "Deselect all" else "Select all")
                            }
                            IconButton(onClick = homeViewModel::togglePinnedSelection) {
                                Icon(Icons.Default.PushPin, if (allSelectedPinned) "Unpin" else "Pin")
                            }
                            IconButton(onClick = homeViewModel::archiveSelected) {
                                Icon(Icons.Default.Archive, "Archive selected conversations")
                            }
                            IconButton(onClick = homeViewModel::blockSelected) {
                                Icon(Icons.Default.Block, "Block selected conversations")
                            }
                            IconButton(enabled = !deleting, onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, "Delete selected conversations")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = {
                                requestDrawerOpen()
                            }) {
                                Icon(Icons.Default.Menu, "Open navigation menu")
                            }
                        },
                        title = { SearchBar(searchText, onValueChange = { searchText = it }) }
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode) FloatingActionButton(
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                    onClick = onNewMessageClick,
                    containerColor = newMessageFabContainerColor,
                    contentColor = newMessageFabContentColor
                ) {
                    Icon(Icons.Default.Add, "New message")
                }
            }
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading && conversations.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Syncing messages...")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(filteredConversations, key = { it.threadId }) { conversation ->
                            val selected = conversation.threadId in selectedIds
                            val displayName = displayNames[conversation.threadId] ?: conversation.address
                            ConversationCard(
                                conversation = conversation,
                                displayName = displayName,
                                selected = selected,
                                isPinned = conversation.threadId in pinnedIds,
                                contactPhoto = contactPhotos[conversation.threadId],
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

                if (showScrollToTop) {
                    SmallFloatingActionButton(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                        onClick = {
                            if (!isScrollingToTop) scope.launch {
                                isScrollingToTop = true
                                try { listState.animateScrollToItem(0) } finally { isScrollingToTop = false }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.Default.ArrowUpward, "Scroll to top")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { if (!deleting) showDeleteDialog = false },
        title = { Text("Delete conversation?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                if (selectedIds.size == 1) "This conversation will be moved to Recycle Bin."
                else "These conversations will be moved to Recycle Bin.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
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
        title = { Text("Exit Message App?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("Are you sure you want to exit?", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = { showExitDialog = false; (context as? Activity)?.finish() }) { Text("Exit") } },
        dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun DrawerHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.message_logo_symbol),
                contentDescription = "MESSAGE logo",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
        }
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
