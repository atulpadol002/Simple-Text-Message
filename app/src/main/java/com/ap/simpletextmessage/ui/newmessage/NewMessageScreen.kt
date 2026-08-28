package com.ap.simpletextmessage.ui.newmessage


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
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ap.simpletextmessage.ui.components.ContactCard
import com.ap.simpletextmessage.viewmodel.ContactViewModel
import com.ap.simpletextmessage.viewmodel.ContactUiState
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.NativeAdCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onBackClick: () -> Unit,
    onContactClick: (Long, String, String) -> Unit
) {
    val viewModel: ContactViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    val contacts = (uiState as? ContactUiState.Content)?.contacts.orEmpty()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var resolvingPhone by remember { mutableStateOf<String?>(null) }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(if (searching) R.string.close_search else R.string.back))
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
                        decorationBox = { inner -> if (query.isEmpty()) Text(stringResource(R.string.search_contacts), color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                    )
                } else Text(stringResource(R.string.new_message))
            },
            actions = {
                if (searching && query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) }
                } else if (!searching) {
                    IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, stringResource(R.string.search_contacts)) }
                }
            }
        )
    }) { padding ->
        when (uiState) {
        ContactUiState.InitialLoading -> {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.loading_contacts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ContactUiState.Empty -> {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { Text(stringResource(R.string.no_contacts_available), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        ContactUiState.Error -> {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.unable_load_contacts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::loadContacts) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        is ContactUiState.Content -> {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (adTypeConfig[AdTypePlacement.NEW_MESSAGE] == AdType.NATIVE &&
                    adConfig.newMessageNative.enabled
                ) {
                    item(key = "new_message_native") {
                        NativeAdCard(
                            placement = AdPlacement.NEW_MESSAGE_NATIVE,
                            enabled = true,
                            maxPerSession = adConfig.newMessageNative.maxPerSession,
                            compact = false,
                            cacheKey = "new_message_header"
                        )
                    }
                }
                items(filtered, key = { it.phoneNumber }) { contact ->
                    ContactCard(contact) {
                        if (resolvingPhone == null) {
                            resolvingPhone = contact.phoneNumber
                            viewModel.resolveConversationThread(contact.phoneNumber) { threadId ->
                                resolvingPhone = null
                                onContactClick(threadId, contact.name, contact.phoneNumber)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
