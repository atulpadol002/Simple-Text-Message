package com.ap.messages.ui.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ap.messages.viewmodel.BlockedNumbersViewModel
import com.ap.messages.viewmodel.ContactViewModel
import com.ap.messages.viewmodel.ContactUiState
import com.ap.messages.ui.components.ContactCard
import com.ap.messages.ads.AdPlacement
import com.ap.messages.ads.AdRemoteConfigManager
import com.ap.messages.ads.BannerAd
import com.ap.messages.ads.AdType
import com.ap.messages.ads.AdTypePlacement
import com.ap.messages.ads.NativeAdCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBackClick: () -> Unit,
    blockedNumbersViewModel: BlockedNumbersViewModel = viewModel()
) {
    val blockedNumbers by blockedNumbersViewModel.blockedNumbers.collectAsState()
    val contactNames by blockedNumbersViewModel.contactNames.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var unblockTarget by remember { mutableStateOf<String?>(null) }
    var showChoice by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<com.ap.messages.data.model.Contact?>(null) }
    val selected by blockedNumbersViewModel.selectedNumbers.collectAsState()
    val selectionMode = selected.isNotEmpty()
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    BackHandler(enabled = selectionMode) { blockedNumbersViewModel.clearSelection() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (selectionMode) "${selected.size} selected" else "Blocked Numbers") },
            navigationIcon = {
                    IconButton(onClick = if (selectionMode) blockedNumbersViewModel::clearSelection else onBackClick) {
                    Icon(if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, if (selectionMode) "Close" else "Back")
                }
            },
            actions = {
                if (selectionMode) {
                    val all = blockedNumbers.isNotEmpty() && blockedNumbers.all { it in selected }
                    IconButton(onClick = { blockedNumbersViewModel.setVisibleSelection(blockedNumbers.toSet(), !all) }) { Icon(Icons.Default.Check, if (all) "Deselect all" else "Select all") }
                    TextButton(onClick = { unblockTarget = "__batch__" }) { Text("UNBLOCK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                } else IconButton(onClick = { showChoice = true }) { Icon(Icons.Default.PersonAdd, "Add blocked number") }
            }
        )
    }, bottomBar = {
        when (adTypeConfig[AdTypePlacement.BLOCKED]) {
            AdType.BANNER -> BannerAd(
                placement = AdPlacement.BLOCKED_BANNER,
                enabled = adConfig.blockedBanner.enabled,
                visible = !selectionMode
            )
            AdType.NATIVE -> if (!selectionMode) {
                NativeAdCard(
                    placement = AdPlacement.BLOCKED_NATIVE,
                    enabled = adConfig.blockedBanner.enabled,
                    maxPerSession = adConfig.sessionMaxAds
                )
            }
            else -> Unit
        }
    }) { padding ->
        if (blockedNumbers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PersonAdd, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("No blocked numbers", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Numbers you block will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(blockedNumbers, key = { it }) { number ->
                    BlockedNumberItem(contactNames[number] ?: number, number, selected = number in selected,
                        onClick = { if (selectionMode) blockedNumbersViewModel.toggleSelection(number) else unblockTarget = number },
                        onLongClick = { blockedNumbersViewModel.toggleSelection(number) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBlockedNumberDialog(
            isBlocked = blockedNumbersViewModel::isNumberBlocked,
            onDismiss = { showAddDialog = false },
            onBlock = {
                if (blockedNumbersViewModel.blockNumber(it)) {
                    showAddDialog = false
                    true
                } else false
            }
        )
    }
    if (showChoice) AlertDialog(
        onDismissRequest = { showChoice = false },
        title = { Text("Block contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showChoice = false; showPicker = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Choose from contacts",
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                }
                OutlinedButton(
                    onClick = { showChoice = false; showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Dialpad, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Enter number manually",
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showChoice = false }) { Text("Cancel") } }
    )
    if (showPicker) ContactPickerDialog(onDismiss = { showPicker = false }, onContact = { pickerTarget = it })
    pickerTarget?.let { contact -> AlertDialog(onDismissRequest = { pickerTarget = null }, title = { Text("Block contact?") }, text = { Text("Are you sure you want to block this contact?") },
        confirmButton = { TextButton(onClick = { pickerTarget = null; showPicker = false; blockedNumbersViewModel.blockNumberAsync(contact.phoneNumber) }) { Text("Block") } }, dismissButton = { TextButton(onClick = { pickerTarget = null }) { Text("Cancel") } }) }
    unblockTarget?.let { number ->
        val batch = number == "__batch__"
        AlertDialog(onDismissRequest = { unblockTarget = null }, title = { Text(if (batch) "Unblock contacts?" else "Unblock contact?") },
            text = { Text(if (batch) "Are you sure you want to unblock the selected contacts?" else "Are you sure you want to unblock this contact?") },
            confirmButton = { TextButton(onClick = { unblockTarget = null; if (batch) blockedNumbersViewModel.unblockSelected() else blockedNumbersViewModel.unblockNumber(number) }) { Text("Unblock") } },
            dismissButton = { TextButton(onClick = { unblockTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun AddBlockedNumberDialog(
    isBlocked: (String) -> Boolean,
    onDismiss: () -> Unit,
    onBlock: (String) -> Boolean
) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block contact") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                singleLine = true,
                label = { Text("Phone number or sender ID") },
                placeholder = { Text("e.g. +91… or BANK") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cleaned = value.trim()
                error = when {
                    cleaned.isEmpty() -> "Enter a phone number or sender ID"
                    isBlocked(cleaned) -> "This number or sender is already blocked"
                    !onBlock(cleaned) -> "Unable to block number or sender"
                    else -> null
                }
            }) { Text("Block") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BlockedNumberItem(contactName: String, phoneNumber: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val savedContact = contactName.isNotBlank() && contactName != phoneNumber
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (if (savedContact) contactName else phoneNumber)
                        .firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(if (savedContact) contactName else phoneNumber, fontWeight = FontWeight.SemiBold)
                if (savedContact) Text(phoneNumber, style = MaterialTheme.typography.bodySmall)
                Text("Blocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            else Text("UNBLOCK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ContactPickerDialog(onDismiss: () -> Unit, onContact: (com.ap.messages.data.model.Contact) -> Unit) {
    val vm: ContactViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    val contacts = (state as? ContactUiState.Content)?.contacts.orEmpty().filter { it.name.contains(query, true) || it.phoneNumber.contains(query, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose contact") }, text = {
        Column(Modifier.heightIn(max = 420.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search contacts") })
            Spacer(Modifier.height(8.dp))
            when (state) {
                ContactUiState.InitialLoading -> CircularProgressIndicator()
                ContactUiState.Empty -> Text("No contacts available")
                ContactUiState.Error -> Text("Unable to load contacts")
                is ContactUiState.Content -> LazyColumn { items(contacts, key = { it.phoneNumber }) { contact -> ContactCard(contact) { onContact(contact) } } }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
