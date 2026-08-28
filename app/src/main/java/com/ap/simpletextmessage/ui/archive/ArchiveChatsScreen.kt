package com.ap.simpletextmessage.ui.archive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ap.simpletextmessage.ui.components.ConversationCard
import com.ap.simpletextmessage.viewmodel.ArchiveViewModel
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.NativeAdCard
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.BannerAd
import com.ap.simpletextmessage.ads.AdDebug
import com.ap.simpletextmessage.ads.AdPosition
import com.ap.simpletextmessage.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveChatsScreen(
    onBackClick: () -> Unit,
    onConversationClick: (Long, String, String) -> Unit
) {
    val viewModel: ArchiveViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val conversations by viewModel.conversations.collectAsState()
    val selectedIds by viewModel.selectedThreadIds.collectAsState()
    val contactNames by viewModel.contactNames.collectAsState()
    val pinnedIds by viewModel.pinnedThreadIds.collectAsState()
    val contactPresentations by viewModel.contactPresentations.collectAsState()
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()

    LaunchedEffect(adConfig.archiveNative.position) {
        AdDebug.log {
            "archiveNative position=${adConfig.archiveNative.position.remoteValue}"
        }
    }

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
                title = {
                    Text(
                        if (selectionMode) pluralStringResource(
                            R.plurals.selected_count,
                            selectedIds.size,
                            selectedIds.size
                        ) else stringResource(R.string.archive_chats)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (selectionMode) viewModel::clearSelection else onBackClick) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(
                                if (selectionMode) R.string.cancel_selection else R.string.back
                            )
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        val visibleIds = conversations.map { it.threadId }.toSet()
                        val allSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
                        IconButton(onClick = { viewModel.setVisibleSelection(visibleIds, !allSelected) }) {
                            Icon(Icons.Default.Check, stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
                        }
                        IconButton(onClick = viewModel::unarchiveSelected) {
                            Icon(Icons.Default.Unarchive, stringResource(R.string.unarchive_selected_conversations))
                        }
                    }
                }
            )
        },
        bottomBar = {
            when (adTypeConfig[AdTypePlacement.ARCHIVE]) {
                AdType.BANNER -> BannerAd(
                    placement = AdPlacement.ARCHIVE_BANNER,
                    enabled = adConfig.archiveNative.enabled &&
                        adConfig.archiveNative.maxPerSession > 0,
                    visible = !selectionMode
                )
                AdType.NATIVE -> if (
                    !selectionMode && adConfig.archiveNative.enabled &&
                    adConfig.archiveNative.position == AdPosition.BOTTOM
                ) {
                    NativeAdCard(
                        placement = AdPlacement.ARCHIVE_NATIVE,
                        enabled = true,
                        maxPerSession = adConfig.archiveNative.maxPerSession,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
                else -> Unit
            }
        }
    ) { paddingValues ->
        when {
            !hasLoaded && conversations.isEmpty() -> Box(Modifier.fillMaxSize().padding(paddingValues))
            conversations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(paddingValues), Alignment.Center
            ) { Text(stringResource(R.string.no_archived_conversations)) }
            else -> LazyColumn(Modifier.fillMaxSize().padding(paddingValues)) {
                if (
                    !selectionMode && adConfig.archiveNative.enabled &&
                    adTypeConfig[AdTypePlacement.ARCHIVE] == AdType.NATIVE &&
                    adConfig.archiveNative.position == AdPosition.TOP
                ) {
                    item(key = "archive_native") {
                        NativeAdCard(
                            placement = AdPlacement.ARCHIVE_NATIVE,
                            enabled = true,
                            maxPerSession = adConfig.archiveNative.maxPerSession
                        )
                    }
                }
                items(conversations, key = { it.threadId }) { conversation ->
                    val selected = conversation.threadId in selectedIds
                    ConversationCard(
                        conversation = conversation,
                        displayName = contactNames[conversation.threadId] ?: conversation.address,
                        selected = selected,
                        isPinned = conversation.threadId in pinnedIds,
                        contactPhoto = contactPresentations[conversation.threadId]?.photo?.asImageBitmap(),
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
