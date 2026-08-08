package com.ap.messages.ui.starred

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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ap.messages.data.model.SmsMessage
import com.ap.messages.viewmodel.StarredMessagesViewModel
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
                navigationIcon = { IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Default.Close, "Close selection") } },
                actions = {
                    val visibleIds = messages.map { it.id }.toSet()
                    val allSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
                    IconButton(onClick = { selectedIds = if (allSelected) selectedIds - visibleIds else selectedIds + visibleIds }) {
                        Icon(Icons.Default.Check, if (allSelected) "Deselect all" else "Select all")
                    }
                    IconButton(onClick = {
                        viewModel.unstar(selectedIds)
                        selectedIds = emptySet()
                    }) { Icon(Icons.Outlined.StarOutline, "Unstar") }
                }
            )
        } else TopAppBar(
            title = { Text("Starred Messages") },
            navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Loading starred messages...")
                }
            }
            messages.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No starred messages", style = MaterialTheme.typography.titleMedium)
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
                    if (message.type == Telephony.Sms.MESSAGE_TYPE_INBOX) "Incoming" else "Outgoing",
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
