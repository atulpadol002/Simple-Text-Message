@file:OptIn(ExperimentalMaterial3Api::class)

package com.atul.messageapp.ui.newmessage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atul.messageapp.ui.components.ContactCard
import com.atul.messageapp.viewmodel.ContactViewModel

@Composable
fun NewMessageScreen(
    onContactClick: (String, String) -> Unit
) {

    val viewModel: ContactViewModel = viewModel()

    var search by remember {
        mutableStateOf("")
    }

    val contacts by viewModel.contacts.collectAsState()

    val filtered = remember(search, contacts) {

        if (search.isBlank()) {

            contacts

        } else {

            contacts.filter {

                it.name.contains(search, ignoreCase = true) ||
                        it.phoneNumber.contains(search)

            }

        }

    }

    Scaffold(

        topBar = {

            TopAppBar(
                title = {
                    Text("New Message")
                }
            )

        }

    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            item {

                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                    },
                    label = {
                        Text("Search Contact")
                    }
                )

            }

            items(
                items = filtered,
                key = { it.phoneNumber }
            ) { contact ->

                ContactCard(
                    contact = contact,
                    onClick = {

                        onContactClick(
                            contact.name,
                            contact.phoneNumber
                        )

                    }
                )

            }

        }

    }

}