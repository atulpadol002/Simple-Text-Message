package com.ap.simpletextmessage.ui.starred

import android.provider.Telephony
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.StarOutline
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ap.simpletextmessage.data.model.SmsMessage
import com.ap.simpletextmessage.viewmodel.StarredMessagesViewModel
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.BannerAd
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.NativeAdCard
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StarredMessagesScreen(
    onBackClick: () -> Unit,
    onMessageClick: (Long, String, String) -> Unit,
    viewModel: StarredMessagesViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val names by viewModel.contactNames.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.load() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = {
        if (selectionMode) {
            TopAppBar(
                title = { Text(selectedIds.size.toString()) },
                navigationIcon = { IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, stringResource(R.string.close_selection)) } },
                actions = {
                    val visibleIds = messages.map { it.id }.toSet()
                    val allSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
                    IconButton(onClick = { selectedIds = if (allSelected) selectedIds - visibleIds else selectedIds + visibleIds }) {
                        Icon(Icons.Default.Check, stringResource(if (allSelected) R.string.deselect_all else R.string.select_all))
                    }
                    IconButton(onClick = {
                        viewModel.unstar(selectedIds)
                        selectedIds = emptySet()
                    }) { Icon(Icons.Outlined.StarOutline, stringResource(R.string.unstar)) }
                }
            )
        } else TopAppBar(
            title = { Text(stringResource(R.string.starred_messages)) },
            navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }, bottomBar = {
        when (adTypeConfig[AdTypePlacement.STARRED]) {
            AdType.BANNER -> BannerAd(
                placement = AdPlacement.STARRED_BANNER,
                enabled = adConfig.starredBanner.enabled,
                visible = !selectionMode
            )
            AdType.NATIVE -> if (!selectionMode) {
                NativeAdCard(
                    placement = AdPlacement.STARRED_NATIVE,
                    enabled = adConfig.starredBanner.enabled,
                    maxPerSession = adConfig.sessionMaxAds
                )
            }
            else -> Unit
        }
    }) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.loading_starred_messages))
                }
            }
            messages.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_starred_messages), style = MaterialTheme.typography.titleMedium)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(messages, key = SmsMessage::id) { message ->
                    val name = names[message.address].orEmpty().takeIf { it.isNotBlank() } ?: message.address
                    StarredRow(
                        message = message,
                        displayName = name,
                        selected = message.id in selectedIds,
                        onClick = {
                            if (selectionMode) selectedIds = selectedIds.toggle(message.id)
                            else onMessageClick(message.threadId, name, message.address)
                        },
                        onLongClick = { selectedIds = selectedIds.toggle(message.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StarredRow(message: SmsMessage, displayName: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.date)), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(5.dp))
                Text(message.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (message.type == Telephony.Sms.MESSAGE_TYPE_INBOX) R.string.incoming else R.string.outgoing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = toMutableSet().apply {
    if (!add(id)) remove(id)
}
