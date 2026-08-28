package com.ap.simpletextmessage.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import com.ap.simpletextmessage.data.model.ScheduledSms
import android.text.format.DateFormat
import java.util.Date

@Composable
fun ScheduledMessageOptionsDialog(
    scheduledSms: ScheduledSms,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onSendNowClick: () -> Unit,
    onCancelScheduleClick: () -> Unit
) {
    val context = LocalContext.current

    val formattedTime = buildString {
        append(DateFormat.getMediumDateFormat(context).format(Date(scheduledSms.scheduledTime)))
        append(", ")
        append(DateFormat.getTimeFormat(context).format(Date(scheduledSms.scheduledTime)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.scheduled_message)
            )
        },
        text = {

            Column {

                Text(
                    text =
                        scheduledSms.contactName
                            .ifBlank {
                                scheduledSms.phoneNumber
                            },
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall
                )

                Text(
                    text = scheduledSms.message,
                    modifier =
                        Modifier.padding(
                            top = 8.dp
                        ),
                    maxLines = 4,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    text = formattedTime,
                    modifier =
                        Modifier.padding(
                            top = 8.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )

                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            vertical = 10.dp
                        )
                )

                ScheduledOptionButton(
                    icon = {
                        Icon(
                            imageVector =
                                Icons.Default.Edit,
                            contentDescription =
                                null
                        )
                    },
                    text = stringResource(R.string.edit),
                    onClick = onEditClick
                )

                ScheduledOptionButton(
                    icon = {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled.Send,
                            contentDescription =
                                null
                        )
                    },
                    text = stringResource(R.string.send_now),
                    onClick =
                        onSendNowClick
                )

                ScheduledOptionButton(
                    icon = {
                        Icon(
                            imageVector =
                                Icons.Default
                                    .DeleteOutline,
                            contentDescription =
                                null,
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    },
                    text =
                        stringResource(R.string.cancel_schedule),
                    textColor =
                        MaterialTheme
                            .colorScheme
                            .error,
                    onClick =
                        onCancelScheduleClick
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ScheduledOptionButton(
    icon: @Composable () -> Unit,
    text: String,
    textColor:
    androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {

    TextButton(
        modifier =
            Modifier.fillMaxWidth(),
        onClick = onClick
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            icon()

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            Text(
                text = text,
                color = textColor
            )
        }
    }
}
