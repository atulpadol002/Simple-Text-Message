package com.atul.messageapp.ui.blocked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.utils.getContactName
import com.atul.messageapp.viewmodel.BlockedNumbersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBackClick: () -> Unit,
    blockedNumbersViewModel:
    BlockedNumbersViewModel = viewModel()
) {

    val context = LocalContext.current

    val blockedNumbers by
    blockedNumbersViewModel
        .blockedNumbers
        .collectAsState()

    var phoneNumber by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Blocked Numbers"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    label = {
                        Text(
                            text = "Phone number"
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Enter number to block"
                        )
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Phone
                        ),
                    isError =
                        errorMessage != null,
                    supportingText = {

                        errorMessage?.let { message ->

                            Text(
                                text = message
                            )
                        }
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Button(
                    onClick = {

                        val cleanedNumber =
                            phoneNumber.trim()

                        when {

                            cleanedNumber.isBlank() -> {

                                errorMessage =
                                    "Enter a phone number"
                            }

                            blockedNumbersViewModel
                                .isNumberBlocked(
                                    cleanedNumber
                                ) -> {

                                errorMessage =
                                    "This number is already blocked"
                            }

                            else -> {

                                val blocked =
                                    blockedNumbersViewModel
                                        .blockNumber(
                                            cleanedNumber
                                        )

                                if (blocked) {

                                    phoneNumber = ""
                                    errorMessage = null

                                } else {

                                    errorMessage =
                                        "Unable to block number"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {

                    Text(
                        text = "Block Number"
                    )
                }
            }

            HorizontalDivider()

            if (blockedNumbers.isEmpty()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                    verticalArrangement =
                        Arrangement.Center
                ) {

                    Text(
                        text = "No blocked numbers",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Numbers you block will appear here.",
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )
                }

            } else {

                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            vertical = 8.dp
                        )
                ) {

                    items(
                        items = blockedNumbers,
                        key = { number ->
                            number
                        }
                    ) { number ->

                        val contactName =
                            getContactName(
                                context = context,
                                phoneNumber = number
                            )

                        BlockedNumberItem(
                            contactName = contactName,
                            phoneNumber = number,
                            onUnblockClick = {

                                blockedNumbersViewModel
                                    .unblockNumber(
                                        number
                                    )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedNumberItem(
    contactName: String,
    phoneNumber: String,
    onUnblockClick: () -> Unit
) {

    val hasSavedContact =
        contactName.isNotBlank() &&
                contactName != phoneNumber

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
        ) {

            Text(
                text =
                    if (hasSavedContact) {
                        contactName
                    } else {
                        phoneNumber
                    },
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            if (hasSavedContact) {

                Text(
                    text = phoneNumber,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }

            Text(
                text = "Blocked",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }

        TextButton(
            onClick = onUnblockClick
        ) {

            Icon(
                imageVector =
                    Icons.Default.Delete,
                contentDescription =
                    "Unblock number"
            )

            Text(
                text = "Unblock",
                modifier =
                    Modifier.padding(
                        start = 6.dp
                    )
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(
            start = 16.dp
        )
    )
}