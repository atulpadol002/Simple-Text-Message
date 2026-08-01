package com.atul.messageapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atul.messageapp.data.model.SmsConversation
import com.atul.messageapp.data.preferences.ArchivePreferences
import com.atul.messageapp.data.preferences.BlockedNumbersPreferences
import com.atul.messageapp.data.repository.SmsRepository
import com.atul.messageapp.receiver.SmsEventBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val smsRepository =
        SmsRepository(application)

    private val archivePreferences =
        ArchivePreferences(application)

    private val blockedNumbersPreferences =
        BlockedNumbersPreferences(
            application
        )

    private val _conversations =
        MutableStateFlow<List<SmsConversation>>(
            emptyList()
        )

    val conversations:
            StateFlow<List<SmsConversation>> =
        _conversations.asStateFlow()

    private val _isLoading =
        MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private var loadJob:
            Job? = null

    init {
        observeIncomingSms()
    }

    private fun observeIncomingSms() {

        viewModelScope.launch {

            SmsEventBus.events
                .collectLatest {

                    loadConversations()
                }
        }
    }

    fun loadConversations() {

        loadJob?.cancel()

        val newLoadJob =
            viewModelScope.launch(
                start = CoroutineStart.LAZY
            ) {

            val currentLoadJob =
                coroutineContext[Job]

            _isLoading.value = true

            try {

                val result =
                    withContext(
                        Dispatchers.IO
                    ) {

                        val archivedThreadIds =
                            archivePreferences
                                .getArchivedThreadIds()

                        smsRepository
                            .getConversations()
                            .filterNot {
                                    conversation ->

                                val isArchived =
                                    archivedThreadIds
                                        .contains(
                                            conversation
                                                .threadId
                                        )

                                val isBlocked =
                                    blockedNumbersPreferences
                                        .isNumberBlocked(
                                            conversation
                                                .address
                                        )

                                isArchived ||
                                        isBlocked
                            }
                    }

                if (loadJob === currentLoadJob) {

                    _conversations.value =
                        result
                }

            } catch (
                exception: CancellationException
            ) {

                throw exception

            } catch (
                exception: SecurityException
            ) {

                exception.printStackTrace()

                if (loadJob === currentLoadJob) {

                    _conversations.value =
                        emptyList()
                }

            } catch (
                exception: Exception
            ) {

                exception.printStackTrace()

                if (loadJob === currentLoadJob) {

                    _conversations.value =
                        emptyList()
                }

            } finally {

                if (loadJob === currentLoadJob) {

                    _isLoading.value = false
                }
            }
        }

        loadJob = newLoadJob
        newLoadJob.start()
    }

    fun archiveConversation(
        conversation: SmsConversation
    ) {

        archivePreferences
            .archiveConversation(
                conversation.threadId
            )

        _conversations.value =
            _conversations.value
                .filterNot {

                    it.threadId ==
                            conversation.threadId
                }
    }
}
