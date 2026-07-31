package com.atul.messageapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.navigation.AppNavigation
import com.atul.messageapp.theme.MessageAppTheme
import com.atul.messageapp.viewmodel.ThemeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            val themeViewModel: ThemeViewModel =
                viewModel()

            val themeMode by
            themeViewModel
                .themeMode
                .collectAsState()

            MessageAppTheme(
                themeMode = themeMode
            ) {
                AppNavigation(
                    themeMode = themeMode,
                    onThemeSelected = { selectedTheme ->
                        themeViewModel.changeTheme(
                            selectedTheme
                        )
                    }
                )
            }
        }
    }
}