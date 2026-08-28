package com.ap.simpletextmessage.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import com.ap.simpletextmessage.R

@Composable
fun ScheduledMessageEditorDialog(
    title: String,
    contactName: String,
    phoneNumber: String,
    initialMessage: String,
    initialScheduledTime: Long,
    confirmButtonText: String,
    allowMessageEditing: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (
        message: String,
        scheduledTime: Long
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
        initialScheduledTime
    ) {
        mutableLongStateOf(
            initialScheduledTime
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

                if (allowMessageEditing) Text(
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

                if (allowMessageEditing &&
                    contactName.isNotBlank()
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(2.dp)
                    )

                    Text(
                        text = phoneNumber,
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

                if (allowMessageEditing) Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )

                if (allowMessageEditing) OutlinedTextField(
                    modifier =
                        Modifier.fillMaxWidth(),
                    value = messageText,
                    onValueChange = {
                        messageText = it
                    },
                    label = {
                        Text(stringResource(R.string.message))
                    },
                    minLines = 3,
                    maxLines = 5
                )

                if (allowMessageEditing) Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(12.dp),
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
                            Modifier.padding(12.dp)
                    ) {

                        Text(
                            text =
                                formatScheduledTime(
                                    selectedTime
                                ),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
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
                                    Modifier.weight(1f),
                                onClick = {

                                    showDatePicker(
                                        context = context,
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
                                                    year = year,
                                                    month = month,
                                                    day = day
                                                )
                                        }
                                    )
                                }
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default
                                            .CalendarMonth,
                                    contentDescription =
                                        null
                                )

                                Text(
                                    modifier =
                                        Modifier.padding(
                                            start = 6.dp
                                        ),
                                    text = stringResource(R.string.date)
                                )
                            }

                            Button(
                                modifier =
                                    Modifier.weight(1f),
                                onClick = {

                                    showTimePicker(
                                        context = context,
                                        currentTime =
                                            selectedTime,
                                        onTimeSelected = {
                                                hour,
                                                minute ->

                                            selectedTime =
                                                updateTime(
                                                    currentTime =
                                                        selectedTime,
                                                    hour = hour,
                                                    minute = minute
                                                )
                                        }
                                    )
                                }
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default
                                            .Schedule,
                                    contentDescription =
                                        null
                                )

                                Text(
                                    modifier =
                                        Modifier.padding(
                                            start = 6.dp
                                        ),
                                    text = stringResource(R.string.time)
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
                    text = confirmButtonText
                )
            }
        },
        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun showDatePicker(
    context: Context,
    currentTime: Long,
    onDateSelected: (
        year: Int,
        month: Int,
        day: Int
    ) -> Unit
) {

    val calendar =
        Calendar.getInstance().apply {
            timeInMillis =
                currentTime
        }

    DatePickerDialog(
        context,
        { _, year, month, day ->

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
        hour: Int,
        minute: Int
    ) -> Unit
) {

    val calendar =
        Calendar.getInstance().apply {
            timeInMillis =
                currentTime
        }

    TimePickerDialog(
        context,
        { _, hour, minute ->

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

    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
}
