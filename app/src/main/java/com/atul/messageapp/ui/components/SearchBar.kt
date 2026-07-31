package com.atul.messageapp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchBar(
    searchText: String,
    onValueChange: (String) -> Unit
) {

    OutlinedTextField(
        value = searchText,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        placeholder = {
            Text("Search conversations")
        },
        singleLine = true
    )
}