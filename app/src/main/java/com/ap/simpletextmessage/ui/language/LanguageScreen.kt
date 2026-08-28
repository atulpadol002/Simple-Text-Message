package com.ap.simpletextmessage.ui.language

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ap.simpletextmessage.R
import com.ap.simpletextmessage.ads.AdPlacement
import com.ap.simpletextmessage.ads.AdRemoteConfigManager
import com.ap.simpletextmessage.ads.AdType
import com.ap.simpletextmessage.ads.AdTypePlacement
import com.ap.simpletextmessage.ads.NativeAdCard
import com.ap.simpletextmessage.localization.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    initialLanguage: AppLanguage,
    onBack: () -> Unit,
    onDone: (AppLanguage) -> Unit
) {
    var selectedLanguage by remember(initialLanguage) { mutableStateOf(initialLanguage) }
    val adConfig by AdRemoteConfigManager.config.collectAsState()
    val adTypeConfig by AdRemoteConfigManager.adTypeConfig.collectAsState()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onDone(selectedLanguage) }) {
                        Text(stringResource(R.string.done))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            contentPadding = padding
        ) {
            items(AppLanguage.entries, key = AppLanguage::languageTag) { language ->
                val selected = language == selectedLanguage
                val displayName = if (language == AppLanguage.SYSTEM_DEFAULT) {
                    stringResource(R.string.system_default)
                } else {
                    language.nativeName
                }
                val selectionDescription = stringResource(
                    if (selected) R.string.language_selected else R.string.language_not_selected,
                    displayName
                )
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { selectedLanguage = language }
                        )
                        .semantics { contentDescription = selectionDescription },
                    headlineContent = {
                        Text(
                            text = displayName
                        )
                    },
                    supportingContent = language.englishName?.let { englishName ->
                        { Text(englishName, style = MaterialTheme.typography.bodySmall) }
                    },
                    trailingContent = {
                        RadioButton(selected = selected, onClick = null)
                    }
                )
                HorizontalDivider()
            }
            item(key = "language_native_ad") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.height(6.dp))
                    if (adTypeConfig.allows(AdTypePlacement.LANGUAGE, AdType.NATIVE)) {
                        NativeAdCard(
                            placement = AdPlacement.LANGUAGE_NATIVE,
                            enabled = adConfig.languageNative.enabled,
                            maxPerSession = adConfig.languageNative.maxPerSession,
                            compact = false,
                            cacheKey = "language_screen"
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
