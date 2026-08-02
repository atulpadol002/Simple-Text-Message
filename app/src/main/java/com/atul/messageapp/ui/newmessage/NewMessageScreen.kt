package com.atul.messageapp.ui.newmessage


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.ui.components.ContactCard
import com.atul.messageapp.viewmodel.ContactViewModel
import com.atul.messageapp.viewmodel.ContactUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onBackClick: () -> Unit,
    onContactClick: (String, String) -> Unit
) {
    val viewModel: ContactViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val contacts = (uiState as? ContactUiState.Content)?.contacts.orEmpty()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = remember(query, contacts) {
        if (query.isBlank()) contacts else contacts.filter {
            it.name.contains(query, true) || it.phoneNumber.contains(query, true)
        }
    }
    fun exitSearch() { searching = false; query = "" }
    fun handleBack() {
        if (searching) exitSearch() else onBackClick()
    }
    BackHandler { handleBack() }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { handleBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, if (searching) "Close search" else "Back")
                }
            },
            title = {
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner -> if (query.isEmpty()) Text("Search contacts", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                    )
                } else Text("New Message")
            },
            actions = {
                if (searching && query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }
                } else if (!searching) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search contacts") }
                }
            }
        )
    }) { padding ->
        when (uiState) {
        ContactUiState.InitialLoading -> {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { CircularProgressIndicator() }
        }
        ContactUiState.Empty -> {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { Text("No contacts available", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        ContactUiState.Error -> {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Unable to load contacts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::loadContacts) { Text("Retry") }
                }
            }
        }
        is ContactUiState.Content -> {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(filtered, key = { it.phoneNumber }) { contact ->
                    ContactCard(contact) { onContactClick(contact.name, contact.phoneNumber) }
                }
            }
        }
        }
    }
}
