package com.atul.messageapp.receiver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsEventBus {

    sealed interface Event {
        data object SmsChanged : Event
        data object ConversationDeleted : Event
        data object ConversationUnblocked : Event
        data object ConversationUnarchived : Event
        data object ConversationRestored : Event
    }

    private val _events =
        MutableSharedFlow<Event>(
            extraBufferCapacity = 1
        )

    val events: SharedFlow<Event> =
        _events.asSharedFlow()

    fun notifySmsReceived() {

        _events.tryEmit(Event.SmsChanged)
    }

    fun notifyConversationDeleted() = emit(Event.ConversationDeleted)

    fun notifyConversationUnblocked() = emit(Event.ConversationUnblocked)

    fun notifyConversationUnarchived() = emit(Event.ConversationUnarchived)

    fun notifyConversationRestored() = emit(Event.ConversationRestored)

    private fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
