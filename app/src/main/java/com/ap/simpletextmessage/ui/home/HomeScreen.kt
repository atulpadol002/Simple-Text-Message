package com.ap.simpletextmessage.ui.home

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ap.simpletextmessage.navigation.Routes
import com.ap.simpletextmessage.R
import com.ap.simpletextmessage.MainActivity
import com.ap.simpletextmessage.ui.components.ConversationCard
import com.ap.simpletextmessage.ui.components.SearchBar
import com.ap.simpletextmessage.viewmodel.HomeViewModel
import com.ap.simpletextmessage.ads.AdConsentManager
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.BannerAd
import com.ap.simpletextmessage.ads.NativeAdCard
import com.ap.simpletextmessage.ads.AdDebug
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.AdRuntime
import com.ap.simpletextmessage.ads.AutoInterstitialManager
import com.ap.simpletextmessage.ads.FullScreenAdCoordinator
import com.ap.simpletextmessage.ads.FullScreenAdType
import com.ap.simpletextmessage.premium.LegalLinks
import com.ap.simpletextmessage.premium.PremiumBillingManager
import com.ap.simpletextmessage.premium.PremiumEntitlementStatus
import com.ap.simpletextmessage.premium.PremiumPopupSession
import com.ap.simpletextmessage.ui.premium.PremiumPaywallPopup
import com.ap.simpletextmessage.utils.MessageCategory
import com.ap.simpletextmessage.utils.classifyMessage
import com.google.android.ump.ConsentInformation
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isActive: Boolean,
    navigationInProgress: Boolean,
    contactsPermissionGranted: Boolean,
    onHomeResumed: () -> Unit,
    onNewMessageClick: () -> Unit,
    onConversationClick: (Long, String, String) -> Unit,
    onPremiumClick: () -> Unit,
    onDrawerNavigate: (String) -> Unit
) {
    val homeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    val paywallEnabled by AdRemoteConfigManager.paywallEnabled.collectAsState()
    val privacyOptionsRequirementStatus by
        AdConsentManager.privacyOptionsRequirementStatus.collectAsState()
    val revokeConsentVisible =
        privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    val premiumState by PremiumBillingManager.state.collectAsState()
    val activeFullScreen by FullScreenAdCoordinator.activeTypeFlow.collectAsState()
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
    var previousContactsPermission by remember {
        mutableStateOf(contactsPermissionGranted)
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
    var selectedCategoryName by rememberSaveable { mutableStateOf(MessageCategory.ALL.name) }
    val selectedCategory = MessageCategory.valueOf(selectedCategoryName)
    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRateUsDialog by remember { mutableStateOf(false) }
    var showPremiumPopup by remember { mutableStateOf(false) }
    var selectedRating by remember { mutableStateOf(0) }
    val selectionMode = selectedIds.isNotEmpty()
    val appSessionNumber = remember { AutoInterstitialManager.currentSessionNumber() }
    LaunchedEffect(adConfig.masterEnabled, adConfig.homeBanner.enabled, selectionMode) {
        AdDebug.log {
            "HomeScreen effective config: master=${adConfig.masterEnabled} " +
                "homeBanner.enabled=${adConfig.homeBanner.enabled} selectionMode=$selectionMode"
        }
    }
    LaunchedEffect(privacyOptionsRequirementStatus) {
        AdDebug.log { "PrivacyDrawer requirementStatus=$privacyOptionsRequirementStatus" }
        AdDebug.log { "PrivacyDrawer revokeVisible=$revokeConsentVisible" }
    }
    val allSelectedPinned = selectionMode && selectedIds.all { it in pinnedIds }
    val canInteract = isActive &&
            lifecycleState == Lifecycle.State.RESUMED &&
            !navigationInProgress

    fun dismissPremiumPopup() {
        showPremiumPopup = false
        FullScreenAdCoordinator.release(FullScreenAdType.PAYWALL_POPUP)
    }

    DisposableEffect(Unit) {
        onDispose {
            FullScreenAdCoordinator.release(FullScreenAdType.PAYWALL_POPUP)
        }
    }

    LaunchedEffect(
        canInteract,
        selectionMode,
        drawerState.currentValue,
        showDeleteDialog,
        showExitDialog,
        showRateUsDialog,
        showPremiumPopup,
        paywallEnabled,
        premiumState.entitlementStatus,
        activeFullScreen
    ) {
        if ((!paywallEnabled || premiumState.isPremium) && showPremiumPopup) {
            dismissPremiumPopup()
            return@LaunchedEffect
        }
        val activity = context as? MainActivity ?: return@LaunchedEffect
        val safeToOffer = canInteract && !selectionMode && drawerState.isClosed &&
            !showDeleteDialog && !showExitDialog && !showRateUsDialog && !showPremiumPopup &&
            premiumState.entitlementStatus != PremiumEntitlementStatus.CHECKING &&
            !premiumState.isPremium && activeFullScreen == null &&
            activity.isAdPresentationSafe() &&
            paywallEnabled && PremiumPopupSession.shouldShow(context, appSessionNumber)
        if (safeToOffer && FullScreenAdCoordinator.tryAcquire(FullScreenAdType.PAYWALL_POPUP)) {
            PremiumPopupSession.markShown(context, appSessionNumber)
            showPremiumPopup = true
        }
    }
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
    val filteredConversations by remember(
        conversations,
        searchText,
        displayNames,
        selectedCategory
    ) {
        derivedStateOf {
            conversations.filter { conversation ->
                val inCategory = selectedCategory == MessageCategory.ALL ||
                    classifyMessage(conversation.address, conversation.body) == selectedCategory
                val name = displayNames[conversation.threadId].orEmpty()
                val matchesSearch = searchText.isBlank() ||
                    name.contains(searchText, true) ||
                    conversation.address.contains(searchText, true) ||
                    conversation.body.contains(searchText, true)
                inCategory && matchesSearch
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
    LaunchedEffect(contactsPermissionGranted) {
        if (contactsPermissionGranted && !previousContactsPermission) {
            homeViewModel.loadConversations()
        }
        previousContactsPermission = contactsPermissionGranted
    }

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

    BackHandler(enabled = canInteract && !showPremiumPopup) {
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    item {
                        DrawerHeader()
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(10.dp))
                    }
                    item {
                        DrawerItem(stringResource(R.string.messages), Icons.Default.Home, true) { closeDrawer() }
                    }
                    if (paywallEnabled || premiumState.isPremium) item {
                        PremiumDrawerItem(
                            label = stringResource(
                                if (premiumState.isPremium) R.string.manage_subscription else R.string.go_premium
                            )
                        ) {
                            closeDrawer {
                                if (premiumState.isPremium) {
                                    AdRuntime.suppressNextAppOpen()
                                    LegalLinks.openSubscriptionManagement(context)
                                } else {
                                    onPremiumClick()
                                }
                            }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.archive), Icons.Default.Archive) {
                            closeDrawer { onDrawerNavigate(Routes.ArchiveChats.route) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.theme), Icons.Default.Palette) {
                            closeDrawer { onDrawerNavigate(Routes.Theme.route) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.languages), Icons.Default.Language) {
                            closeDrawer { onDrawerNavigate(Routes.Language.create("drawer")) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.scheduled_sms), Icons.Default.Schedule) {
                            closeDrawer { onDrawerNavigate(Routes.ScheduledSms.route) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.block_numbers), Icons.Default.Block) {
                            closeDrawer { onDrawerNavigate(Routes.BlockNumbers.route) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.starred_messages), Icons.Default.Star) {
                            closeDrawer { onDrawerNavigate(Routes.StarredMessages.route) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.recycle_bin), Icons.Default.RestoreFromTrash) {
                            closeDrawer { onDrawerNavigate(Routes.RecycleBin.route) }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.about), Icons.Default.Info) {
                            closeDrawer { onDrawerNavigate(Routes.About.route) }
                        }
                    }
                    if (revokeConsentVisible) {
                        item {
                            DrawerItem(
                                label = stringResource(R.string.revoke_consent),
                                icon = Icons.Default.PrivacyTip,
                                contentDescription = stringResource(R.string.manage_privacy_consent)
                            ) {
                                AdDebug.log { "PrivacyDrawer revokeTapped=true" }
                                closeDrawer {
                                    (context as? Activity)
                                        ?.let(AdConsentManager::showPrivacyOptions)
                                }
                            }
                        }
                    }
                    item {
                        DrawerItem(stringResource(R.string.rate_us), Icons.Default.RateReview) {
                            closeDrawer {
                                if (!RateUsSession.wasDialogShown) {
                                    RateUsSession.markDialogShown()
                                    selectedRating = 0
                                    showRateUsDialog = true
                                }
                            }
                        }
                    }
                }
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
                                if (allSelectedPinned) {
                                    Image(
                                        painter = painterResource(R.drawable.unpin_icon),
                                        contentDescription = "Unpin chat",
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.PushPin, "Pin chat")
                                }
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
                        title = { SearchBar(searchText, onValueChange = { searchText = it }) },
                        actions = {
                            if (paywallEnabled) {
                            IconButton(onClick = onPremiumClick) {
                                Image(
                                    painter = painterResource(R.drawable.paywall_icon),
                                    contentDescription = "Open Premium",
                                    modifier = Modifier.size(width = 40.dp, height = 30.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            }
                        }
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
            },
            bottomBar = {
                when (adTypeConfig[AdTypePlacement.HOME]) {
                    AdType.BANNER -> BannerAd(
                        placement = AdPlacement.HOME_BANNER,
                        enabled = adConfig.homeBanner.enabled,
                        visible = !selectionMode
                    )
                    AdType.NATIVE -> if (!selectionMode) {
                        NativeAdCard(
                            placement = AdPlacement.HOME_SURFACE_NATIVE,
                            enabled = adConfig.homeBanner.enabled,
                            maxPerSession = adConfig.sessionMaxAds
                        )
                    }
                    else -> Unit
                }
            }
        ) { paddingValues ->
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                MessageCategoryRow(
                    selectedCategory = selectedCategory,
                    onSelected = { selectedCategoryName = it.name }
                )
                Box(Modifier.fillMaxSize()) {
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
                        filteredConversations.forEachIndexed { index, conversation ->
                            item(key = "conversation_${conversation.threadId}") {
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
                            val nativeConfig = adConfig.homeInlineNative
                            val adOrdinal = (index + 1) / nativeConfig.everyItems
                            if (
                                !selectionMode && nativeConfig.enabled &&
                                adTypeConfig[AdTypePlacement.HOME_INLINE] == AdType.NATIVE &&
                                (index + 1) % nativeConfig.everyItems == 0 &&
                                adOrdinal <= nativeConfig.maxPerSession
                            ) {
                                item(key = "home_native_$adOrdinal") {
                                    NativeAdCard(
                                        placement = AdPlacement.HOME_NATIVE,
                                        enabled = true,
                                        maxPerSession = nativeConfig.maxPerSession,
                                        compact = true,
                                        cacheKey = "home_native_$adOrdinal"
                                    )
                                }
                            }
                        }
                        if (filteredConversations.isEmpty() && !isLoading) item {
                            Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                                Text(
                                    if (searchText.isNotBlank()) "No matching messages"
                                    else if (selectedCategory == MessageCategory.ALL) "No messages found"
                                    else stringResource(
                                        R.string.no_category_messages,
                                        stringResource(selectedCategory.labelResource())
                                    )
                                )
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

    if (showRateUsDialog) RateUsDialog(
        selectedRating = selectedRating,
        onRatingSelected = { selectedRating = it },
        onConfirm = {
            showRateUsDialog = false
            launchPlayStoreReview(context)
        },
        onDismiss = { showRateUsDialog = false }
    )

    if (showExitDialog) AlertDialog(
        onDismissRequest = { showExitDialog = false },
        title = { Text("Exit Simple Text Message?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("Are you sure you want to exit?", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = { showExitDialog = false; (context as? Activity)?.finish() }) { Text("Exit") } },
        dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Cancel") } }
    )

    if (showPremiumPopup && paywallEnabled) {
        PremiumPaywallPopup(
            onGoPremium = {
                dismissPremiumPopup()
                onPremiumClick()
            },
            onNotNow = ::dismissPremiumPopup
        )
    }
}

@Composable
private fun MessageCategoryRow(
    selectedCategory: MessageCategory,
    onSelected: (MessageCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MessageCategory.entries.forEach { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onSelected(category) },
                label = { Text(stringResource(category.labelResource())) }
            )
        }
    }
}

private fun MessageCategory.labelResource(): Int = when (this) {
    MessageCategory.ALL -> R.string.category_all
    MessageCategory.PERSONAL -> R.string.category_personal
    MessageCategory.OTP -> R.string.category_otp
    MessageCategory.TRANSACTIONS -> R.string.category_transactions
    MessageCategory.OFFERS -> R.string.category_offers
}

@Composable
private fun DrawerHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.simple_text_message_app_icon),
            contentDescription = "Simple Text Message logo",
            modifier = Modifier.size(52.dp),
            contentScale = ContentScale.Fit
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text("Simple Text Message", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("SMS conversations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription ?: label,
                modifier = Modifier.size(24.dp)
            )
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun PremiumDrawerItem(label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        icon = {
            Image(
                painter = painterResource(R.drawable.paywall_icon),
                contentDescription = "Premium",
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}
