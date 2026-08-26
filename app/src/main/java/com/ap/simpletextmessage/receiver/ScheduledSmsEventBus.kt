package com.ap.simpletextmessage.receiver

import com.ap.simpletextmessage.data.model.ScheduledSmsStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ScheduledSmsEventBus {
    data class Event(val messageId: Long, val status: ScheduledSmsStatus)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun notify(messageId: Long, status: ScheduledSmsStatus) {
        _events.tryEmit(Event(messageId, status))
    }
}
