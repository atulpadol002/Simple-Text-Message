package com.atul.messageapp.ui.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.viewmodel.BlockedNumbersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBackClick: () -> Unit,
    blockedNumbersViewModel: BlockedNumbersViewModel = viewModel()
) {
    val blockedNumbers by blockedNumbersViewModel.blockedNumbers.collectAsState()
    val contactNames by blockedNumbersViewModel.contactNames.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Blocked Numbers") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.PersonAdd, "Add blocked number")
                }
            }
        )
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
                    BlockedNumberItem(contactNames[number] ?: number, number) {
                        blockedNumbersViewModel.unblockNumber(number)
                    }
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
        title = { Text("Block number or sender") },
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
private fun BlockedNumberItem(contactName: String, phoneNumber: String, onUnblockClick: () -> Unit) {
    val savedContact = contactName.isNotBlank() && contactName != phoneNumber
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
            IconButton(onClick = onUnblockClick) { Icon(Icons.Default.Delete, "Unblock number") }
        }
    }
}
