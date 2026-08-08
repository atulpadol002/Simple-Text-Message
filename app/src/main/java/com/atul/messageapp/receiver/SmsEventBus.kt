package com.atul.messageapp.receiver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsEventBus {

    sealed interface Event {
        data class SmsChanged(val threadId: Long) : Event
        data class ThreadRead(val threadId: Long) : Event
        data object ConversationDeleted : Event
        data object ConversationUnblocked : Event
        data class ConversationBlocked(val address: String) : Event
        data object ConversationUnarchived : Event
        data object ConversationRestored : Event
    }

    private val _events =
        MutableSharedFlow<Event>(
            extraBufferCapacity = 32
        )

    val events: SharedFlow<Event> =
        _events.asSharedFlow()

    fun notifySmsReceived(threadId: Long) = emit(Event.SmsChanged(threadId))
    fun notifyThreadRead(threadId: Long) = emit(Event.ThreadRead(threadId))

    fun notifyConversationDeleted() = emit(Event.ConversationDeleted)

    fun notifyConversationUnblocked() = emit(Event.ConversationUnblocked)
    fun notifyConversationBlocked(address: String) = emit(Event.ConversationBlocked(address))

    fun notifyConversationUnarchived() = emit(Event.ConversationUnarchived)

    fun notifyConversationRestored() = emit(Event.ConversationRestored)

    private fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
