@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.atul.messageapp.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.data.model.Message
import com.atul.messageapp.data.model.ScheduledSms
import com.atul.messageapp.receiver.SmsEventBus
import com.atul.messageapp.ui.components.MessageBubble
import com.atul.messageapp.ui.components.ScheduledMessageBubble
import com.atul.messageapp.ui.components.ScheduledMessageEditorDialog
import com.atul.messageapp.ui.components.ScheduledMessageOptionsDialog
import com.atul.messageapp.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextFieldDefaults
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences
import com.atul.messageapp.data.preferences.RecentEmojiPreferences
import com.atul.messageapp.ui.components.EmojiPicker
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Job

@Composable
fun ChatScreen(
    contactName: String,
    phoneNumber: String,
    conversationId: Long,
    onBackClick: () -> Unit,
    onConversationDeleted: () -> Unit
) {

    val chatViewModel: ChatViewModel =
        viewModel()

    val context =
        LocalContext.current

    val blockedNumbersPreferences =
        remember(context) {
            BlockedNumbersPreferences(
                context
            )
        }

    val showMoreMenu =
        remember {
            mutableStateOf(false)
        }

    val showDeleteConversationDialog =
        remember {
            mutableStateOf(false)
        }

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val coroutineScope =
        rememberCoroutineScope()

    val keyboardController =
        LocalSoftwareKeyboardController.current

    val messageFocusRequester =
        remember {
            FocusRequester()
        }

    val searchFocusRequester =
        remember {
            FocusRequester()
        }

    val messageText = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    var showEmojiPanel by rememberSaveable { mutableStateOf(false) }
    val recentEmojiPreferences = remember(context) { RecentEmojiPreferences(context) }
    var recentEmojis by remember { mutableStateOf(recentEmojiPreferences.getRecentEmojis()) }
    val messageInteractionSource = remember { MutableInteractionSource() }

    val showScheduleDialog =
        remember {
            mutableStateOf(false)
        }

    var selectedMessageIds by remember { mutableStateOf(emptySet<Long>()) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentSearchMatchId by remember { mutableStateOf<Long?>(null) }
    var searchScrollJob by remember { mutableStateOf<Job?>(null) }
    var showDeleteMessagesDialog by remember { mutableStateOf(false) }
    var isDeletingMessages by remember { mutableStateOf(false) }
    val contactAvatar by chatViewModel.contactAvatar.collectAsState()
    val routeContactAvatar = contactAvatar.takeIf {
        it.conversationId == conversationId && it.phoneNumber == phoneNumber
    }
    val isDeletingConversation by chatViewModel.isDeletingConversation.collectAsState()

    val selectedScheduledMessage =
        remember {
            mutableStateOf<ScheduledSms?>(null)
        }

    val editingScheduledMessage =
        remember {
            mutableStateOf<ScheduledSms?>(null)
        }

    val messages by
    chatViewModel.messages
        .collectAsState()

    val starredMessageIds by
    chatViewModel.starredMessageIds
        .collectAsState()

    val scheduledMessages by
    chatViewModel.scheduledMessages
        .collectAsState()

    val listState =
        rememberLazyListState()

    val trimmedSearchQuery by remember {
        derivedStateOf { searchQuery.trim() }
    }
    val matchingMessageIds by remember(messages, trimmedSearchQuery) {
        derivedStateOf {
            if (trimmedSearchQuery.isEmpty()) {
                emptyList()
            } else {
                messages.asSequence()
                    .filter { it.body.contains(trimmedSearchQuery, ignoreCase = true) }
                    .map { it.id }
                    .toList()
            }
        }
    }
    val currentSearchMatchIndex = matchingMessageIds.indexOf(currentSearchMatchId)

    fun exitSearchMode() {
        searchScrollJob?.cancel()
        searchScrollJob = null
        isSearchMode = false
        searchQuery = ""
        currentSearchMatchId = null
    }

    fun selectSearchResult(index: Int) {
        if (matchingMessageIds.isEmpty()) return
        val wrappedIndex = (index + matchingMessageIds.size) % matchingMessageIds.size
        val matchId = matchingMessageIds[wrappedIndex]
        currentSearchMatchId = matchId
        val messageIndex = messages.indexOfFirst { it.id == matchId }
        if (messageIndex >= 0) {
            searchScrollJob?.cancel()
            searchScrollJob = coroutineScope.launch {
                listState.animateScrollToItem(messageIndex)
            }
        }
    }

    val selectedMessages = messages
        .filter { it.id in selectedMessageIds }
        .sortedBy { it.timestamp }
    val allSelectedAreStarred = selectedMessages.isNotEmpty() &&
            selectedMessages.all { it.id in starredMessageIds }

    BackHandler(
        enabled = showEmojiPanel || showDeleteMessagesDialog || selectedMessageIds.isNotEmpty() || isSearchMode
    ) {
        if (showEmojiPanel) {
            showEmojiPanel = false
        } else if (showDeleteMessagesDialog) {
            showDeleteMessagesDialog = false
        } else if (isSearchMode) {
            exitSearchMode()
        } else {
            selectedMessageIds = emptySet()
        }
    }

    LaunchedEffect(messageInteractionSource) {
        messageInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press && showEmojiPanel) {
                showEmojiPanel = false
                messageFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    LaunchedEffect(isSearchMode, trimmedSearchQuery, matchingMessageIds) {
        if (!isSearchMode || matchingMessageIds.isEmpty()) {
            currentSearchMatchId = null
        } else if (currentSearchMatchId !in matchingMessageIds) {
            currentSearchMatchId = matchingMessageIds.first()
            val messageIndex = messages.indexOfFirst { it.id == currentSearchMatchId }
            if (messageIndex >= 0) {
                searchScrollJob?.cancel()
                searchScrollJob = coroutineScope.launch {
                    listState.animateScrollToItem(messageIndex)
                }
            }
        }
    }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            searchScrollJob?.cancel()
        }
    }

    LaunchedEffect(
        conversationId,
        phoneNumber
    ) {

        chatViewModel.loadMessages(
            conversationId = conversationId,
            phoneNumber = phoneNumber,
            initialDisplayName = contactName
        )

        SmsEventBus.events.collectLatest {

            chatViewModel.refreshMessages()
        }
    }

    LaunchedEffect(
        messages.size,
        scheduledMessages.size
    ) {

        val totalItems =
            messages.size +
                    scheduledMessages.size +
                    if (
                        scheduledMessages.isNotEmpty()
                    ) {
                        1
                    } else {
                        0
                    }

        if (totalItems > 0 && !isSearchMode) {

            if (messages.size > 100) {
                listState.scrollToItem(
                    index = totalItems - 1
                )
            } else {
                listState.animateScrollToItem(
                    index = totalItems - 1
                )
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectedMessageIds.isNotEmpty()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedMessageIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection")
                        }
                    },
                    title = { Text(selectedMessageIds.size.toString()) },
                    actions = {
                        IconButton(onClick = {
                            val copiedText = selectedMessages.joinToString("\n") { it.body }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SMS messages", copiedText))
                            selectedMessageIds = emptySet()
                            Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = {
                            chatViewModel.setMessagesStarred(selectedMessages, !allSelectedAreStarred)
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(
                                if (allSelectedAreStarred) Icons.Outlined.StarOutline else Icons.Default.Star,
                                contentDescription = if (allSelectedAreStarred) "Unstar" else "Star"
                            )
                        }
                        IconButton(onClick = { showDeleteMessagesDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else if (isSearchMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = { exitSearchMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp)
                                        .focusRequester(searchFocusRequester),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "Search messages",
                                                    maxLines = 1,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        Text(
                            text = if (matchingMessageIds.isEmpty()) "0 of 0"
                            else "${currentSearchMatchIndex.coerceAtLeast(0) + 1} of ${matchingMessageIds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            modifier = Modifier.size(40.dp),
                            enabled = matchingMessageIds.isNotEmpty(),
                            onClick = { selectSearchResult(currentSearchMatchIndex - 1) }
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous result")
                        }
                        IconButton(
                            modifier = Modifier.size(40.dp),
                            enabled = matchingMessageIds.isNotEmpty(),
                            onClick = { selectSearchResult(currentSearchMatchIndex + 1) }
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next result")
                        }
                        }
                    }
                )
            } else {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                title = {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                end = 4.dp,
                                top = 6.dp,
                                bottom = 6.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            val photo = routeContactAvatar?.photo
                            if (photo != null) {
                                Image(
                                    bitmap = photo.asImageBitmap(),
                                    contentDescription = "Contact photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val avatarText = meaningfulInitial(
                                    routeContactAvatar?.displayName.orEmpty().ifBlank { contactName },
                                    phoneNumber
                                )
                                Text(
                                    text = avatarText,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(
                            modifier =
                                Modifier.width(10.dp)
                        )

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    routeContactAvatar?.displayName.orEmpty().ifBlank { contactName },
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer,
                                fontSize = 16.sp,
                                fontWeight =
                                    FontWeight.SemiBold,
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis
                            )

                            Text(
                                text =
                                    phoneNumber,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer
                                        .copy(
                                            alpha = 0.7f
                                        ),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                showEmojiPanel = false
                                selectedMessageIds = emptySet()
                                showMoreMenu.value = false
                                isSearchMode = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search messages",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Box {

                            IconButton(
                                onClick = {

                                    showMoreMenu.value =
                                        true
                                }
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default.MoreVert,
                                    contentDescription =
                                        "More options",
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .onPrimaryContainer
                                )
                            }

                            DropdownMenu(
                                expanded =
                                    showMoreMenu.value,
                                onDismissRequest = {

                                    showMoreMenu.value =
                                        false
                                }
                            ) {

                                DropdownMenuItem(
                                    text = {

                                        Text(
                                            text =
                                                "View contact"
                                        )
                                    },
                                    leadingIcon = {

                                        Icon(
                                            imageVector =
                                                Icons.Default.Person,
                                            contentDescription =
                                                null
                                        )
                                    },
                                    onClick = {

                                        showMoreMenu.value =
                                            false

                                        openContact(
                                            context =
                                                context,
                                            phoneNumber =
                                                phoneNumber
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = {

                                        Text(
                                            text =
                                                if (
                                                    blockedNumbersPreferences
                                                        .isNumberBlocked(
                                                            phoneNumber
                                                        )
                                                ) {
                                                    "Already blocked"
                                                } else {
                                                    "Add to block list"
                                                }
                                        )
                                    },
                                    leadingIcon = {

                                        Icon(
                                            imageVector =
                                                Icons.Default.Block,
                                            contentDescription =
                                                null
                                        )
                                    },
                                    enabled =
                                        !blockedNumbersPreferences
                                            .isNumberBlocked(
                                                phoneNumber
                                            ),
                                    onClick = {

                                        showMoreMenu.value =
                                            false

                                        val blocked =
                                            blockedNumbersPreferences
                                                .blockNumber(
                                                    phoneNumber
                                                )

                                        Toast.makeText(
                                            context,
                                            if (blocked) {
                                                "Number added to block list"
                                            } else {
                                                "Unable to block number"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        if (blocked) {

                                            onBackClick()
                                        }
                                    }
                                )

                                DropdownMenuItem(
                                    text = {

                                        Text(
                                            text =
                                                "Delete conversation",
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .error
                                        )
                                    },
                                    leadingIcon = {

                                        Icon(
                                            imageVector =
                                                Icons.Default.Delete,
                                            contentDescription =
                                                null,
                                            tint =
                                                MaterialTheme
                                                    .colorScheme
                                                    .error
                                        )
                                    },
                                    onClick = {

                                        showMoreMenu.value =
                                            false

                                        showDeleteConversationDialog
                                            .value =
                                            true
                                    }
                                )
                            }
                        }
                    }
                }
            )
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(
                    if (showEmojiPanel) WindowInsets.navigationBars
                    else WindowInsets.ime.union(WindowInsets.navigationBars)
                )
        ) {

            LazyColumn(
                state =
                    listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                itemsIndexed(
                    items =
                        messages,
                    key = { _, message ->

                        "sms_${message.id}"
                    }
                ) { index, message ->

                    val currentMessageDate =
                        timestampToLocalDate(
                            message.timestamp
                        )

                    val previousMessageDate =
                        if (index > 0) {

                            timestampToLocalDate(
                                messages[index - 1]
                                    .timestamp
                            )

                        } else {

                            null
                        }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        if (
                            previousMessageDate == null ||
                            currentMessageDate !=
                            previousMessageDate
                        ) {

                            DateSeparator(
                                date =
                                    currentMessageDate
                            )
                        }

                        MessageBubble(
                            message =
                                message,
                            isStarred =
                                starredMessageIds
                                    .contains(
                                        message.id
                                    ),
                            isSelected =
                                message.id in selectedMessageIds,
                            isSearchMatch =
                                isSearchMode && message.id in matchingMessageIds,
                            isCurrentSearchMatch =
                                isSearchMode && message.id == currentSearchMatchId,
                            onClick = { tappedMessage ->
                                if (selectedMessageIds.isNotEmpty()) {
                                    selectedMessageIds =
                                        if (tappedMessage.id in selectedMessageIds) {
                                            selectedMessageIds - tappedMessage.id
                                        } else {
                                            selectedMessageIds + tappedMessage.id
                                        }
                                }
                            },
                            onRetryClick = {
                                    failedMessage:
                                    Message ->

                                chatViewModel.retrySend(
                                    failedMessage
                                )
                            },
                            onLongClick = {
                                    longPressedMessage:
                                    Message ->

                                if (isSearchMode) exitSearchMode()
                                selectedMessageIds =
                                    selectedMessageIds + longPressedMessage.id
                            }
                        )
                    }
                }

                if (
                    scheduledMessages.isNotEmpty()
                ) {

                    item(
                        key =
                            "scheduled_header"
                    ) {

                        ScheduledSectionHeader()
                    }
                }

                items(
                    items =
                        scheduledMessages,
                    key = { scheduledSms ->

                        "scheduled_${scheduledSms.id}"
                    }
                ) { scheduledSms ->

                    ScheduledMessageBubble(
                        scheduledSms =
                            scheduledSms,
                        onClick = {

                            selectedScheduledMessage.value =
                                scheduledSms
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(
                            messageFocusRequester
                        ),
                    value =
                        messageText.value,
                    onValueChange = { newText ->

                        messageText.value =
                            newText
                    },
                    placeholder = {

                        Text(
                            text =
                                "Type message"
                        )
                    },
                    singleLine =
                        false,
                    minLines = 1,
                    maxLines = 5,
                    interactionSource = messageInteractionSource,
                    shape =
                        RoundedCornerShape(
                            24.dp
                        ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    leadingIcon = {
                        IconButton(onClick = {
                            if (showEmojiPanel) {
                                showEmojiPanel = false
                                messageFocusRequester.requestFocus()
                                keyboardController?.show()
                            } else {
                                keyboardController?.hide()
                                showEmojiPanel = true
                            }
                        }) {
                            Icon(
                                if (showEmojiPanel) Icons.Default.Keyboard
                                else Icons.Default.EmojiEmotions,
                                if (showEmojiPanel) "Show keyboard" else "Open emoji picker"
                            )
                        }
                    },
                    trailingIcon = {

                        IconButton(
                            onClick = {

                                if (
                                    messageText.value.text
                                        .isBlank()
                                ) {

                                    Toast.makeText(
                                        context,
                                        "Type a message first",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                } else {

                                    keyboardController
                                        ?.hide()

                                    showScheduleDialog.value =
                                        true
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default
                                        .Schedule,
                                contentDescription =
                                    "Schedule message"
                            )
                        }
                    }
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                IconButton(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme
                                .colorScheme
                                .primary
                        ),
                    onClick = {

                        if (
                            messageText.value.text
                                .isNotBlank()
                        ) {

                            val text =
                                messageText.value.text
                                    .trim()

                            messageText.value =
                                TextFieldValue("")

                            chatViewModel.sendMessage(
                                phoneNumber =
                                    phoneNumber,
                                conversationId =
                                    conversationId,
                                message =
                                    text
                            )
                        }
                    }
                ) {

                    Icon(
                        imageVector =
                            Icons.AutoMirrored
                                .Filled
                                .Send,
                        contentDescription =
                            "Send",
                        tint =
                            MaterialTheme
                                .colorScheme
                                .onPrimary
                    )
                }
            }

            AnimatedVisibility(
                visible = showEmojiPanel,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                EmojiPicker(
                    recentEmojis = recentEmojis,
                    onEmojiSelected = { emoji ->
                        val current = messageText.value
                        val start = current.selection.min.coerceIn(0, current.text.length)
                        val end = current.selection.max.coerceIn(start, current.text.length)
                        val updatedText = current.text.replaceRange(start, end, emoji)
                        val cursor = start + emoji.length
                        messageText.value = TextFieldValue(
                            text = updatedText,
                            selection = TextRange(cursor)
                        )
                        recentEmojis = recentEmojiPreferences.addEmoji(emoji)
                    }
                )
            }
        }
    }

    if (
        showScheduleDialog.value
    ) {

        ScheduledMessageEditorDialog(
            title =
                "Schedule message",
            contactName =
                contactName,
            phoneNumber =
                phoneNumber,
            initialMessage =
                messageText.value.text,
            initialScheduledTime =
                System.currentTimeMillis() +
                        60_000L,
            confirmButtonText =
                "Schedule",
            allowMessageEditing = false,
            onDismiss = {

                showScheduleDialog.value =
                    false
            },
            onConfirm = {
                    scheduledMessage: String,
                    scheduledTime: Long ->

                val scheduled =
                    chatViewModel.scheduleMessage(
                        contactName =
                            contactName,
                        phoneNumber =
                            phoneNumber,
                        message =
                            scheduledMessage,
                        scheduledTime =
                            scheduledTime
                    )

                if (scheduled) {

                    messageText.value =
                        TextFieldValue("")

                    showScheduleDialog.value =
                        false

                    Toast.makeText(
                        context,
                        "Message scheduled",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    Toast.makeText(
                        context,
                        "Enter message and select a future time",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    if (showDeleteMessagesDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingMessages) showDeleteMessagesDialog = false
            },
            title = { Text("Delete") },
            text = {
                Text(
                    if (selectedMessageIds.size == 1) {
                        "Are you sure you want to delete this message?"
                    } else {
                        "Are you sure you want to delete these messages?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingMessages,
                    onClick = {
                        if (!isDeletingMessages) {
                            isDeletingMessages = true
                            chatViewModel.deleteMessages(selectedMessages) { success ->
                                isDeletingMessages = false
                                if (success) {
                                    showDeleteMessagesDialog = false
                                    selectedMessageIds = emptySet()
                                } else {
                                    Toast.makeText(context, "Unable to delete message", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingMessages,
                    onClick = { showDeleteMessagesDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    selectedScheduledMessage.value
        ?.let { scheduledSms ->

            ScheduledMessageOptionsDialog(
                scheduledSms =
                    scheduledSms,
                onDismiss = {

                    selectedScheduledMessage.value =
                        null
                },
                onEditClick = {

                    val editingStarted =
                        chatViewModel
                            .beginEditingScheduledMessage(
                                scheduledSms
                            )

                    selectedScheduledMessage.value =
                        null

                    if (editingStarted) {

                        editingScheduledMessage.value =
                            scheduledSms

                    } else {

                        Toast.makeText(
                            context,
                            "Scheduled time has already passed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onSendNowClick = {

                    val started =
                        chatViewModel
                            .sendScheduledMessageNow(
                                scheduledSms
                            )

                    selectedScheduledMessage.value =
                        null

                    Toast.makeText(
                        context,
                        if (started) {
                            "Sending SMS now"
                        } else {
                            "Unable to send SMS"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onCancelScheduleClick = {

                    chatViewModel
                        .cancelScheduledMessage(
                            scheduledSms
                        )

                    selectedScheduledMessage.value =
                        null

                    Toast.makeText(
                        context,
                        "Scheduled message cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

    editingScheduledMessage.value
        ?.let { scheduledSms ->

            ScheduledMessageEditorDialog(
                title =
                    "Edit scheduled message",
                contactName =
                    scheduledSms.contactName
                        .ifBlank {
                            contactAvatar.displayName.ifBlank { contactName } },
                phoneNumber =
                    scheduledSms.phoneNumber,
                initialMessage =
                    scheduledSms.message,
                initialScheduledTime =
                    scheduledSms.scheduledTime,
                confirmButtonText =
                    "Save changes",
                onDismiss = {

                    chatViewModel
                        .cancelEditingScheduledMessage(
                            scheduledSms
                        )

                    editingScheduledMessage.value =
                        null

                    if (
                        scheduledSms.scheduledTime <=
                        System.currentTimeMillis()
                    ) {

                        Toast.makeText(
                            context,
                            "Schedule cancelled because its time passed while editing",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onConfirm = {
                        updatedMessage: String,
                        updatedTime: Long ->

                    val updated =
                        chatViewModel
                            .updateScheduledMessage(
                                oldScheduledSms =
                                    scheduledSms,
                                message =
                                    updatedMessage,
                                scheduledTime =
                                    updatedTime
                            )

                    if (updated) {

                        editingScheduledMessage.value =
                            null

                        Toast.makeText(
                            context,
                            "Scheduled message updated",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        Toast.makeText(
                            context,
                            "Enter message and select a future time",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
            if (
                showDeleteConversationDialog.value
            ) {

                AlertDialog(
                    onDismissRequest = {

                        if (!isDeletingConversation) showDeleteConversationDialog.value = false
                    },
                    title = {

                        Text(
                            text = "Delete conversation?",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {

                        Text(
                            text =
                                "This conversation will be moved to Recycle Bin.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {

                        TextButton(
                            enabled = !isDeletingConversation,
                            onClick = {
                                chatViewModel.deleteConversation(conversationId) { deleted ->
                                    if (deleted) {
                                        showDeleteConversationDialog.value = false
                                        onConversationDeleted()
                                    } else {
                                        Toast.makeText(context, "Unable to delete conversation", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {

                            Text(
                                text = "Delete",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error
                            )
                        }
                    },
                    dismissButton = {

                        TextButton(
                            enabled = !isDeletingConversation,
                            onClick = {

                                showDeleteConversationDialog.value =
                                    false
                            }
                        ) {

                            Text(
                                text = "Cancel"
                            )
                        }
                    }
                )
            }    }
}

private fun meaningfulInitial(displayName: String, phoneNumber: String): String {
    return displayName.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()?.toString()
        ?: phoneNumber.firstOrNull { !it.isWhitespace() }?.toString()
        ?: "?"
}

@Composable
private fun ScheduledSectionHeader() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 12.dp,
                bottom = 4.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        HorizontalDivider(
            modifier =
                Modifier.weight(1f)
        )

        Text(
            modifier =
                Modifier.padding(
                    horizontal = 12.dp
                ),
            text =
                "Scheduled",
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .primary,
            fontWeight =
                FontWeight.SemiBold
        )

        HorizontalDivider(
            modifier =
                Modifier.weight(1f)
        )
    }
}

@Composable
private fun DateSeparator(
    date: LocalDate
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        HorizontalDivider(
            modifier =
                Modifier.weight(1f)
        )

        Text(
            modifier =
                Modifier.padding(
                    horizontal = 12.dp
                ),
            text =
                formatDateLabel(
                    date
                ),
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        HorizontalDivider(
            modifier =
                Modifier.weight(1f)
        )
    }
}

private fun timestampToLocalDate(
    timestamp: Long
): LocalDate {

    return Instant
        .ofEpochMilli(
            timestamp
        )
        .atZone(
            ZoneId.systemDefault()
        )
        .toLocalDate()
}

private fun formatDateLabel(
    date: LocalDate
): String {

    val today =
        LocalDate.now()

    val yesterday =
        today.minusDays(1)

    return when (date) {

        today ->
            "Today"

        yesterday ->
            "Yesterday"

        else ->
            date.format(
                DateTimeFormatter
                    .ofPattern(
                        "dd MMM yyyy"
                    )
            )
    }
}
private fun openContact(
    context: Context,
    phoneNumber: String
) {

    try {

        val lookupUri =
            Uri.withAppendedPath(
                ContactsContract
                    .PhoneLookup
                    .CONTENT_FILTER_URI,
                Uri.encode(
                    phoneNumber
                )
            )

        val cursor =
            context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract
                        .PhoneLookup
                        .CONTACT_ID,
                    ContactsContract
                        .PhoneLookup
                        .LOOKUP_KEY
                ),
                null,
                null,
                null
            )

        cursor?.use {

            if (it.moveToFirst()) {

                val contactId =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            ContactsContract
                                .PhoneLookup
                                .CONTACT_ID
                        )
                    )

                val lookupKey =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            ContactsContract
                                .PhoneLookup
                                .LOOKUP_KEY
                        )
                    )

                val contactUri =
                    ContactsContract
                        .Contacts
                        .getLookupUri(
                            contactId,
                            lookupKey
                        )

                val viewContactIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        contactUri
                    )

                context.startActivity(
                    viewContactIntent
                )

                return
            }
        }

        val addContactIntent =
            Intent(
                ContactsContract
                    .Intents
                    .Insert
                    .ACTION
            ).apply {

                type =
                    ContactsContract
                        .RawContacts
                        .CONTENT_TYPE

                putExtra(
                    ContactsContract
                        .Intents
                        .Insert
                        .PHONE,
                    phoneNumber
                )
            }

        context.startActivity(
            addContactIntent
        )

    } catch (
        exception: Exception
    ) {

        exception.printStackTrace()

        Toast.makeText(
            context,
            "Unable to open contact",
            Toast.LENGTH_SHORT
        ).show()
    }
}
