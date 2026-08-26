package com.ap.simpletextmessage.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class EmojiCategory(
    val name: String,
    val icon: String,
    val emojis: List<String>
)

@Composable
fun EmojiCategoryBar(
    categories: List<EmojiCategory>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        categories.forEachIndexed { index, category ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onCategorySelected(index) },
                modifier = Modifier
                    .semantics { contentDescription = category.name }
                    .padding(horizontal = 2.dp),
                text = {
                    Text(
                        text = category.icon,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
