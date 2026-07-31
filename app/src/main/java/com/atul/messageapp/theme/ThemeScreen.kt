package com.atul.messageapp.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atul.messageapp.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Theme"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored
                                    .Filled
                                    .ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ThemeOption(
                title = "System mode",
                description =
                    "Follow your phone's theme setting",
                selected =
                    selectedTheme == ThemeMode.SYSTEM,
                onClick = {
                    onThemeSelected(
                        ThemeMode.SYSTEM
                    )
                }
            )

            HorizontalDivider()

            ThemeOption(
                title = "Light theme",
                description =
                    "Always use light appearance",
                selected =
                    selectedTheme == ThemeMode.LIGHT,
                onClick = {
                    onThemeSelected(
                        ThemeMode.LIGHT
                    )
                }
            )

            HorizontalDivider()

            ThemeOption(
                title = "Dark theme",
                description =
                    "Always use dark appearance",
                selected =
                    selectedTheme == ThemeMode.DARK,
                onClick = {
                    onThemeSelected(
                        ThemeMode.DARK
                    )
                }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Column {
            Text(
                text = title
            )

            Text(
                text = description,
                style =
                    androidx.compose.material3
                        .MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    androidx.compose.material3
                        .MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }
    }
}