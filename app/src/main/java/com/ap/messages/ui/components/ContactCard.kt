package com.ap.messages.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ap.messages.data.model.Contact
import com.ap.messages.utils.AvatarColorResolver

@Composable
fun ContactCard(contact: Contact, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarColor = AvatarColorResolver.background(contact.name.ifBlank { contact.phoneNumber }, MaterialTheme.colorScheme)
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            val photo = contact.photo
            if (photo != null) {
                Image(photo.asImageBitmap(), "Contact photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    contact.name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
                        ?: contact.phoneNumber.firstOrNull { !it.isWhitespace() }?.toString() ?: "?",
                    color = AvatarColorResolver.foreground(avatarColor, MaterialTheme.colorScheme),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
