package com.mindflakes.ridewatch.core

/** A focus callback belongs to one event and one playback phase or retry attempt. */
class AudioFocusPolicy {
    enum class Content {
        SONIFICATION,
        SPEECH,
    }

    enum class Gain {
        MAY_DUCK,
        TRANSIENT,
    }

    data class Request(
        val event: Long,
        val attempt: Long,
        val gain: Gain,
        val content: Content,
    )

    private var attempt = 0L
    private var active: Request? = null

    fun request(event: Long, pauseOthers: Boolean, content: Content): Request =
        Request(event, ++attempt, if (pauseOthers) Gain.TRANSIENT else Gain.MAY_DUCK, content)
            .also { active = it }

    fun isCurrent(request: Request, event: Long): Boolean =
        request.event == event && active == request

    fun mayRetry(request: Request, now: Long, deadline: Long): Boolean =
        active == request && request.gain == Gain.TRANSIENT && now < deadline

    fun release() {
        active = null
    }
}
