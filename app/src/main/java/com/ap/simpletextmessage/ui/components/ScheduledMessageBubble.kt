package com.ap.simpletextmessage.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import com.ap.simpletextmessage.data.model.ScheduledSms
import com.ap.simpletextmessage.data.model.ScheduledSmsStatus
import android.text.format.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledMessageBubble(
    scheduledSms: ScheduledSms,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val formattedTime = buildString {
        append(DateFormat.getMediumDateFormat(context).format(Date(scheduledSms.scheduledTime)))
        append(" • ")
        append(DateFormat.getTimeFormat(context).format(Date(scheduledSms.scheduledTime)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.End
    ) {

        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onClick
                ),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 6.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = scheduledSms.message,
                    style = MaterialTheme.typography.bodyLarge
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = when (scheduledSms.status) {
                            ScheduledSmsStatus.SCHEDULED -> stringResource(R.string.scheduled)
                            ScheduledSmsStatus.SENDING -> stringResource(R.string.sending)
                            ScheduledSmsStatus.SENT -> stringResource(R.string.sent)
                            ScheduledSmsStatus.FAILED -> stringResource(R.string.failed)
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
