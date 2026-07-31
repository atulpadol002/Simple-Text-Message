package com.atul.messageapp.receiver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsEventBus {

    private val _events =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1
        )

    val events: SharedFlow<Unit> =
        _events.asSharedFlow()

    fun notifySmsReceived() {

        _events.tryEmit(Unit)
    }
}