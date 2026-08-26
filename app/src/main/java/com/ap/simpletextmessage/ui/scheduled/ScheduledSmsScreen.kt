package com.ap.simpletextmessage.ui.scheduled

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.ap.simpletextmessage.data.model.Contact
import com.ap.simpletextmessage.data.model.ScheduledSms
import com.ap.simpletextmessage.data.model.ScheduledSmsStatus
import com.ap.simpletextmessage.BuildConfig
import com.ap.simpletextmessage.viewmodel.ContactViewModel
import com.ap.simpletextmessage.viewmodel.ScheduledSmsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.ap.simpletextmessage.ui.components.ScheduledMessageOptionsDialog
import com.ap.simpletextmessage.ads.AdDebug
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.AdRuntime
import com.ap.simpletextmessage.ads.BannerAd
import androidx.compose.runtime.LaunchedEffect
import com.ap.simpletextmessage.sms.ScheduledSmsScheduler
import com.ap.simpletextmessage.ui.components.ExactAlarmPermissionDialog
import com.ap.simpletextmessage.ui.components.exactAlarmSettingsIntent

private const val SCHEDULED_SMS_PERMISSION_TAG = "ScheduledSms"

private fun scheduledSmsPermissionLog(message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(SCHEDULED_SMS_PERMISSION_TAG, message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledSmsScreen(
    onBackClick: () -> Unit
) {

    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val scheduledSmsViewModel:
            ScheduledSmsViewModel =
        viewModel()

    val contactViewModel:
            ContactViewModel =
        viewModel()

    val scheduledMessages by
    scheduledSmsViewModel
        .scheduledMessages
        .collectAsState()

    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    val scheduledAdType = adTypeConfig[AdTypePlacement.SCHEDULED]

    LaunchedEffect(adConfig.scheduleBanner.enabled, scheduledAdType) {
        AdDebug.log {
            "SCHEDULED_BANNER enabled=${adConfig.scheduleBanner.enabled} " +
                "adType=${scheduledAdType.remoteValue}"
        }
    }

    val contacts by
    contactViewModel
        .contacts
        .collectAsState()

    var showContactPicker by remember {
        mutableStateOf(false)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    BackHandler(enabled = showContactPicker) {
        showContactPicker = false
        searchText = ""
    }

    var selectedContact by remember {
        mutableStateOf<Contact?>(null)
    }

    var pendingScheduleContact by remember {
        mutableStateOf<Contact?>(null)
    }

    var showExactAlarmExplanation by remember {
        mutableStateOf(false)
    }

    var pendingSchedulePermissionRequest by remember {
        mutableStateOf(false)
    }

    val scheduledSmsScheduler = remember(context) {
        ScheduledSmsScheduler(context)
    }

    fun continuePendingSchedule() {
        val contact = pendingScheduleContact ?: return
        pendingScheduleContact = null
        selectedContact = contact
        scheduledSmsPermissionLog("ScheduledSms schedule continued=true")
    }

    fun finishPermissionRequest() {
        val granted = scheduledSmsScheduler.canScheduleExactAlarms()
        scheduledSmsPermissionLog("ScheduledSms permission granted=$granted")
        if (granted) {
            continuePendingSchedule()
        } else {
            pendingScheduleContact = null
        }
    }

    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingSchedulePermissionRequest) {
            pendingSchedulePermissionRequest = false
            finishPermissionRequest()
        }
    }

    var selectedScheduledSms by remember {
        mutableStateOf<ScheduledSms?>(null)
    }

    var editingScheduledSms by remember {
        mutableStateOf<ScheduledSms?>(null)
    }

    val filteredContacts =
        remember(
            searchText,
            contacts
        ) {

            if (searchText.isBlank()) {

                contacts

            } else {

                contacts.filter { contact ->

                    contact.name.contains(
                        searchText,
                        ignoreCase = true
                    ) ||
                            contact.phoneNumber.contains(
                                searchText
                            )
                }
            }
        }

    DisposableEffect(
        lifecycleOwner
    ) {

        val observer =
            LifecycleEventObserver {
                    _,
                    event ->

                if (event == Lifecycle.Event.ON_RESUME) {

                    scheduledSmsViewModel
                        .loadScheduledMessages()

                    contactViewModel
                        .loadContacts()

                    if (pendingSchedulePermissionRequest) {
                        pendingSchedulePermissionRequest = false
                        finishPermissionRequest()
                    }
                }
            }

        lifecycleOwner.lifecycle
            .addObserver(observer)

        onDispose {

            lifecycleOwner.lifecycle
                .removeObserver(observer)
        }
    }

    if (showContactPicker) {

        ContactPickerContent(
            contacts =
                filteredContacts,
            searchText =
                searchText,
            onSearchChange = {
                searchText = it
            },
            onBackClick = {

                showContactPicker = false
                searchText = ""
            },
            onContactClick = { contact ->
                showContactPicker =
                    false

                searchText = ""

                pendingScheduleContact = contact
                val permissionRequired =
                    !scheduledSmsScheduler.canScheduleExactAlarms()
                scheduledSmsPermissionLog(
                    "ScheduledSms permission required=$permissionRequired"
                )
                if (permissionRequired) {
                    showExactAlarmExplanation = true
                } else {
                    scheduledSmsPermissionLog(
                        "ScheduledSms permission granted=true"
                    )
                    continuePendingSchedule()
                }
            }
        )

    } else {

        Scaffold(
            topBar = {

                TopAppBar(
                    title = {
                        Text(
                            text = "Scheduled SMS"
                        )
                    },
                    navigationIcon = {

                        IconButton(
                            onClick =
                                onBackClick
                        ) {

                            Icon(
                                imageVector =
                                    Icons.AutoMirrored
                                        .Filled
                                        .ArrowBack,
                                contentDescription =
                                    "Back"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {

                FloatingActionButton(
                    onClick = {

                        showContactPicker =
                            true
                    }
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Add,
                        contentDescription =
                            "Add scheduled SMS"
                    )
                }
            },
            bottomBar = {
                BannerAd(
                    placement = AdPlacement.SCHEDULED_BANNER,
                    enabled = adConfig.scheduleBanner.enabled &&
                        scheduledAdType == AdType.BANNER
                )
            }
        ) { paddingValues ->

            if (
                scheduledMessages
                    .isEmpty()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            paddingValues
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Icon(
                            modifier =
                                Modifier.size(
                                    52.dp
                                ),
                            imageVector =
                                Icons.Default.Schedule,
                            contentDescription =
                                null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp
                                )
                        )

                        Text(
                            text =
                                "No scheduled messages",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        Text(
                            text =
                                "Tap + to schedule a message",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }

            } else {

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            paddingValues
                        )
                        .padding(
                            horizontal = 16.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {

                    item {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    4.dp
                                )
                        )
                    }

                    items(
                        items =
                            scheduledMessages,
                        key = {
                            it.id
                        }
                    ) { scheduledSms ->

                        ScheduledSmsCard(
                            scheduledSms =
                                scheduledSms,
                            onClick = {

                                selectedScheduledSms =
                                    scheduledSms
                            }
                        )
                    }

                    item {

                        Spacer(
                            modifier =
                                Modifier.height(
                                    90.dp
                                )
                        )
                    }
                }
            }
        }
    }

    if (showExactAlarmExplanation) {
        ExactAlarmPermissionDialog(
            onContinue = {
                showExactAlarmExplanation = false
                val settingsIntent = exactAlarmSettingsIntent(context)
                if (settingsIntent == null) {
                    scheduledSmsPermissionLog(
                        "ScheduledSms permission request launched=false"
                    )
                    pendingScheduleContact = null
                    Toast.makeText(
                        context,
                        "Unable to open Alarms & reminders settings",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    pendingSchedulePermissionRequest = true
                    AdRuntime.suppressNextAppOpen()
                    try {
                        exactAlarmSettingsLauncher.launch(settingsIntent)
                        scheduledSmsPermissionLog(
                            "ScheduledSms permission request launched=true"
                        )
                    } catch (exception: Exception) {
                        pendingSchedulePermissionRequest = false
                        pendingScheduleContact = null
                        scheduledSmsPermissionLog(
                            "ScheduledSms permission request launched=false"
                        )
                        Toast.makeText(
                            context,
                            "Unable to open Alarms & reminders settings",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onCancel = {
                showExactAlarmExplanation = false
                pendingScheduleContact = null
            }
        )
    }

    selectedContact?.let {
            contact ->

        ScheduledSmsEditorDialog(
            title =
                "Schedule message",
            contactName =
                contact.name,
            phoneNumber =
                contact.phoneNumber,
            initialMessage = "",
            initialTime =
                System.currentTimeMillis() +
                        60_000L,
            confirmButtonText =
                "Schedule",
            onDismiss = {

                selectedContact = null
            },
            onConfirm = {
                    message,
                    scheduledTime ->

                val success =
                    scheduledSmsViewModel
                        .scheduleMessage(
                            contactName =
                                contact.name,
                            phoneNumber =
                                contact.phoneNumber,
                            message =
                                message,
                            scheduledTime =
                                scheduledTime
                        )

                if (success) {

                    selectedContact =
                        null

                    Toast.makeText(
                        context,
                        "SMS scheduled",
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

    selectedScheduledSms?.let { scheduledSms ->

        ScheduledMessageOptionsDialog(
            scheduledSms =
                scheduledSms,

            onDismiss = {

                selectedScheduledSms =
                    null
            },

            onEditClick = {

                val editingStarted =
                    scheduledSmsViewModel
                        .beginEditingScheduledMessage(
                            scheduledSms
                        )

                selectedScheduledSms =
                    null

                if (editingStarted) {

                    editingScheduledSms =
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

                val sent =
                    scheduledSmsViewModel
                        .sendNow(
                            scheduledSms
                        )

                selectedScheduledSms =
                    null

                Toast.makeText(
                    context,
                    if (sent) {
                        "Sending SMS now"
                    } else {
                        "Unable to send SMS"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            },

            onCancelScheduleClick = {

                scheduledSmsViewModel
                    .cancelScheduledMessage(
                        scheduledSms
                    )

                selectedScheduledSms =
                    null

                Toast.makeText(
                    context,
                    "Scheduled SMS cancelled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    editingScheduledSms?.let { scheduledSms ->

        ScheduledSmsEditorDialog(
            title =
                "Edit scheduled message",

            contactName =
                scheduledSms.contactName,

            phoneNumber =
                scheduledSms.phoneNumber,

            initialMessage =
                scheduledSms.message,

            initialTime =
                scheduledSms.scheduledTime,

            confirmButtonText =
                "Save changes",

            onDismiss = {

                scheduledSmsViewModel
                    .cancelEditingScheduledMessage(
                        scheduledSms
                    )

                editingScheduledSms =
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
                    message: String,
                    scheduledTime: Long ->

                val success =
                    scheduledSmsViewModel
                        .updateScheduledMessage(
                            oldScheduledSms =
                                scheduledSms,
                            contactName =
                                scheduledSms.contactName,
                            phoneNumber =
                                scheduledSms.phoneNumber,
                            message =
                                message,
                            scheduledTime =
                                scheduledTime
                        )

                if (success) {

                    editingScheduledSms =
                        null

                    Toast.makeText(
                        context,
                        "Scheduled SMS updated",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    Toast.makeText(
                        context,
                        "Select a valid future time",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactPickerContent(
    contacts: List<Contact>,
    searchText: String,
    onSearchChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onContactClick: (Contact) -> Unit
) {

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text(
                        text = "Select contact"
                    )
                },
                navigationIcon = {

                    IconButton(
                        onClick =
                            onBackClick
                    ) {

                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled
                                    .ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    paddingValues
                )
        ) {

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                value =
                    searchText,
                onValueChange =
                    onSearchChange,
                label = {
                    Text(
                        text = "Search contact"
                    )
                },
                singleLine = true,
                trailingIcon = {

                    if (
                        searchText.isNotEmpty()
                    ) {

                        IconButton(
                            onClick = {
                                onSearchChange("")
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Close,
                                contentDescription =
                                    "Clear search"
                            )
                        }
                    }
                }
            )

            if (contacts.isEmpty()) {

                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "No contacts found",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

            } else {

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    items(
                        items =
                            contacts,
                        key = {
                            it.phoneNumber
                        }
                    ) { contact ->

                        ContactPickerItem(
                            contact =
                                contact,
                            onClick = {

                                onContactClick(
                                    contact
                                )
                            }
                        )

                        HorizontalDivider(
                            modifier =
                                Modifier.padding(
                                    start = 72.dp
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPickerItem(
    contact: Contact,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Card(
            modifier =
                Modifier.size(
                    44.dp
                ),
            shape =
                CircleShape,
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                )
        ) {

            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        contact.name
                            .firstOrNull()
                            ?.uppercase()
                            ?: "?",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 14.dp
                )
        ) {

            Text(
                text =
                    contact.name,
                style =
                    MaterialTheme
                        .typography
                        .titleSmall,
                fontWeight =
                    FontWeight.SemiBold,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )

            Spacer(
                modifier =
                    Modifier.height(
                        2.dp
                    )
            )

            Text(
                text =
                    contact.phoneNumber,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduledSmsCard(
    scheduledSms: ScheduledSms,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = scheduledSms.status == ScheduledSmsStatus.SCHEDULED,
                onClick = onClick
            ),
        shape =
            RoundedCornerShape(
                16.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            )
    ) {

        Column(
            modifier =
                Modifier.padding(
                    16.dp
                )
        ) {

            Text(
                text =
                    scheduledSms
                        .contactName
                        .ifBlank {
                            scheduledSms
                                .phoneNumber
                        },
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            if (
                scheduledSms.contactName
                    .isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            2.dp
                        )
                )

                Text(
                    text =
                        scheduledSms
                            .phoneNumber,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            Text(
                text =
                    scheduledSms.message,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium,
                maxLines = 3,
                overflow =
                    TextOverflow.Ellipsis
            )

            Spacer(
                modifier =
                    Modifier.height(
                        10.dp
                    )
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    modifier =
                        Modifier.size(
                            17.dp
                        ),
                    imageVector =
                        Icons.Default.Schedule,
                    contentDescription =
                        null,
                    tint =
                        MaterialTheme
                            .colorScheme
                            .primary
                )

                Spacer(
                    modifier =
                        Modifier.padding(
                            horizontal = 3.dp
                        )
                )

                Text(
                    text =
                        "${scheduledSms.status.displayLabel()} • " +
                            formatScheduledTime(scheduledSms.scheduledTime),
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
            }
        }
    }

}

private fun ScheduledSmsStatus.displayLabel(): String = when (this) {
    ScheduledSmsStatus.SCHEDULED -> "Scheduled"
    ScheduledSmsStatus.SENDING -> "Sending"
    ScheduledSmsStatus.SENT -> "Sent"
    ScheduledSmsStatus.FAILED -> "Failed"
}

@Composable
private fun ScheduledSmsEditorDialog(
    title: String,
    contactName: String,
    phoneNumber: String,
    initialMessage: String,
    initialTime: Long,
    confirmButtonText: String,
    onDismiss: () -> Unit,
    onConfirm: (
        String,
        Long
    ) -> Unit
) {

    val context =
        LocalContext.current

    var messageText by remember(
        initialMessage
    ) {
        mutableStateOf(
            initialMessage
        )
    }

    var selectedTime by remember(
        initialTime
    ) {
        mutableLongStateOf(
            initialTime
        )
    }

    AlertDialog(
        onDismissRequest =
            onDismiss,
        title = {

            Text(
                text = title
            )
        },
        text = {

            Column {

                Text(
                    text =
                        contactName.ifBlank {
                            phoneNumber
                        },
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.SemiBold
                )

                if (
                    contactName.isNotBlank()
                ) {

                    Text(
                        text =
                            phoneNumber,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp
                        )
                )

                OutlinedTextField(
                    modifier =
                        Modifier.fillMaxWidth(),
                    value =
                        messageText,
                    onValueChange = {
                        messageText = it
                    },
                    label = {
                        Text(
                            text = "Message"
                        )
                    },
                    minLines = 3,
                    maxLines = 5
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                12.dp
                            )
                    ) {

                        Text(
                            text =
                                formatScheduledTime(
                                    selectedTime
                                ),
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {

                            Button(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),
                                onClick = {

                                    showDatePicker(
                                        context =
                                            context,
                                        currentTime =
                                            selectedTime,
                                        onDateSelected = {
                                                year,
                                                month,
                                                day ->

                                            selectedTime =
                                                updateDate(
                                                    currentTime =
                                                        selectedTime,
                                                    year =
                                                        year,
                                                    month =
                                                        month,
                                                    day =
                                                        day
                                                )
                                        }
                                    )
                                }
                            ) {

                                Text(
                                    text = "Date"
                                )
                            }

                            Button(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),
                                onClick = {

                                    showTimePicker(
                                        context =
                                            context,
                                        currentTime =
                                            selectedTime,
                                        onTimeSelected = {
                                                hour,
                                                minute ->

                                            selectedTime =
                                                updateTime(
                                                    currentTime =
                                                        selectedTime,
                                                    hour =
                                                        hour,
                                                    minute =
                                                        minute
                                                )
                                        }
                                    )
                                }
                            ) {

                                Text(
                                    text = "Time"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {

            TextButton(
                onClick = {

                    onConfirm(
                        messageText.trim(),
                        selectedTime
                    )
                }
            ) {

                Text(
                    text =
                        confirmButtonText
                )
            }
        },
        dismissButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text(
                    text = "Cancel"
                )
            }
        }
    )
}

private fun showDatePicker(
    context: Context,
    currentTime: Long,
    onDateSelected: (
        Int,
        Int,
        Int
    ) -> Unit
) {

    val calendar =
        Calendar.getInstance().apply {
            timeInMillis =
                currentTime
        }

    DatePickerDialog(
        context,
        {
                _,
                year,
                month,
                day ->

            onDateSelected(
                year,
                month,
                day
            )
        },
        calendar.get(
            Calendar.YEAR
        ),
        calendar.get(
            Calendar.MONTH
        ),
        calendar.get(
            Calendar.DAY_OF_MONTH
        )
    ).apply {

        datePicker.minDate =
            System.currentTimeMillis()

    }.show()
}

private fun showTimePicker(
    context: Context,
    currentTime: Long,
    onTimeSelected: (
        Int,
        Int
    ) -> Unit
) {

    val calendar =
        Calendar.getInstance().apply {
            timeInMillis =
                currentTime
        }

    TimePickerDialog(
        context,
        {
                _,
                hour,
                minute ->

            onTimeSelected(
                hour,
                minute
            )
        },
        calendar.get(
            Calendar.HOUR_OF_DAY
        ),
        calendar.get(
            Calendar.MINUTE
        ),
        false
    ).show()
}

private fun updateDate(
    currentTime: Long,
    year: Int,
    month: Int,
    day: Int
): Long {

    return Calendar
        .getInstance()
        .apply {

            timeInMillis =
                currentTime

            set(
                Calendar.YEAR,
                year
            )

            set(
                Calendar.MONTH,
                month
            )

            set(
                Calendar.DAY_OF_MONTH,
                day
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )
        }
        .timeInMillis
}

private fun updateTime(
    currentTime: Long,
    hour: Int,
    minute: Int
): Long {

    return Calendar
        .getInstance()
        .apply {

            timeInMillis =
                currentTime

            set(
                Calendar.HOUR_OF_DAY,
                hour
            )

            set(
                Calendar.MINUTE,
                minute
            )

            set(
                Calendar.SECOND,
                0
            )

            set(
                Calendar.MILLISECOND,
                0
            )
        }
        .timeInMillis
}

private fun formatScheduledTime(
    time: Long
): String {

    return SimpleDateFormat(
        "dd MMM yyyy, hh:mm a",
        Locale.getDefault()
    ).format(
        Date(time)
    )
}
