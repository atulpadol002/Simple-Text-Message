package com.ap.simpletextmessage.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import com.ap.simpletextmessage.data.model.Message
import com.ap.simpletextmessage.data.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isStarred: Boolean = false,
    isSelected: Boolean = false,
    isSearchMatch: Boolean = false,
    isCurrentSearchMatch: Boolean = false,
    onClick: (Message) -> Unit = {},
    onRetryClick: (Message) -> Unit = {},
    retryEnabled: Boolean = true,
    onLongClick: (Message) -> Unit = {}
) {

    val bubbleAlignment =
        if (message.isIncoming) {
            Alignment.Start
        } else {
            Alignment.End
        }

    val bubbleColor =
        if (message.isIncoming) {
            MaterialTheme.colorScheme
                .surfaceVariant
        } else {
            MaterialTheme.colorScheme
                .primaryContainer
        }

    val textColor =
        if (message.isIncoming) {
            MaterialTheme.colorScheme
                .onSurfaceVariant
        } else {
            MaterialTheme.colorScheme
                .onPrimaryContainer
        }

    val bubbleShape =
        if (message.isIncoming) {

            RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 18.dp,
                bottomEnd = 18.dp,
                bottomStart = 18.dp
            )

        } else {

            RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 6.dp,
                bottomEnd = 18.dp,
                bottomStart = 18.dp
            )
        }

    val formattedTime =
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(
            Date(message.timestamp)
        )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    isCurrentSearchMatch -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
                    isSearchMatch -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                RoundedCornerShape(12.dp)
            )
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment =
            bubbleAlignment
    ) {

        Surface(
            modifier = Modifier
                .widthIn(
                    min = 70.dp,
                    max = 300.dp
                )
                .combinedClickable(
                    onClick = {
                        onClick(message)
                    },
                    onLongClick = {
                        onLongClick(message)
                    }
                ),
            color = bubbleColor,
            shape = bubbleShape,
            tonalElevation = 1.dp
        ) {

            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                Text(
                    text = message.body,
                    color = textColor,
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )

                Row(
                    modifier =
                        Modifier.align(
                            Alignment.End
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            5.dp
                        )
                ) {

                    if (isStarred) {

                        Icon(
                            imageVector =
                                Icons.Default.Star,
                            contentDescription =
                                "Starred message",
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            modifier =
                                Modifier.widthIn(
                                    max = 14.dp
                                )
                        )
                    }

                    Text(
                        text = formattedTime,
                        color =
                            textColor.copy(
                                alpha = 0.65f
                            ),
                        fontSize = 10.sp
                    )

                    if (!message.isIncoming) {

                        when (message.status) {

                            MessageStatus.NONE ->
                                Unit

                            MessageStatus.SENDING -> {

                                Text(
                                    text = "Sending",
                                    color =
                                        textColor.copy(
                                            alpha = 0.65f
                                        ),
                                    fontSize = 10.sp
                                )
                            }

                            MessageStatus.SENT -> {

                                Text(
                                    text = "✓ Sent",
                                    color =
                                        textColor.copy(
                                            alpha = 0.7f
                                        ),
                                    fontSize = 10.sp
                                )
                            }

                            MessageStatus.DELIVERED -> {

                                Text(
                                    text =
                                        "✓✓ Delivered",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    fontSize = 10.sp
                                )
                            }

                            MessageStatus.FAILED -> {

                                Text(
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                enabled = retryEnabled,
                                                onClick = {
                                                    onRetryClick(
                                                        message
                                                    )
                                                },
                                                onLongClick = {
                                                    onLongClick(
                                                        message
                                                    )
                                                }
                                            ),
                                    text =
                                        if (retryEnabled) "Failed • Retry" else "Failed",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error,
                                    fontSize = 10.sp,
                                    fontWeight =
                                        FontWeight
                                            .SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
