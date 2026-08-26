package com.ap.simpletextmessage.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    onNavigationClick: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title
            )
        },
        navigationIcon = {
            if (onNavigationClick != null) {
                IconButton(
                    onClick = onNavigationClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation menu"
                    )
                }
            }
        }
    )
}