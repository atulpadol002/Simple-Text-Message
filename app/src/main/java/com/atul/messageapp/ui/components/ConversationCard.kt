package com.atul.messageapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atul.messageapp.data.model.SmsConversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationCard(
    conversation: SmsConversation,
    displayName: String,
    selected: Boolean = false,
    isPinned: Boolean = false,
    contactPhoto: ImageBitmap? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val formattedTime = remember(conversation.date) {
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(
            Date(conversation.date)
        )
    }

    val firstLetter = displayName.firstOrNull { it.isLetterOrDigit() }
        ?.uppercaseChar()?.toString()
        ?: conversation.address.firstOrNull { !it.isWhitespace() }?.toString()
        ?: "?"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    if (contactPhoto != null) {
                        Image(contactPhoto, "Contact photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            text = firstLetter,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    fontSize = 14.sp,
                    fontWeight = if (conversation.read) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Bold
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = conversation.body,
                    fontSize = 12.sp,
                    fontWeight = if (conversation.read) {
                        FontWeight.Normal
                    } else {
                        FontWeight.SemiBold
                    },
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                )

                if (isPinned) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (conversation.unreadCount > 0) {
                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (
                                conversation.unreadCount > 99
                            ) {
                                "99+"
                            } else {
                                conversation.unreadCount.toString()
                            },
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 66.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
