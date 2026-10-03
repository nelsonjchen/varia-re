package com.mindflakes.ridewatch.core

import com.mindflakes.ridewatch.core.AudioFocusPolicy.Content
import com.mindflakes.ridewatch.core.AudioFocusPolicy.Gain
import org.junit.Assert.*
import org.junit.Test

class AudioFocusPolicyTest {
    @Test
    fun chimeToSpeechKeepsWarningGainAndRejectsLateChimeLoss() {
        val policy = AudioFocusPolicy()
        val chime = policy.request(7, true, Content.SONIFICATION)
        assertTrue(policy.isCurrent(chime, 7))
        val speech = policy.request(7, true, Content.SPEECH)
        assertFalse(policy.isCurrent(chime, 7))
        assertTrue(policy.isCurrent(speech, 7))
        assertEquals(Gain.TRANSIENT, speech.gain)
        assertEquals(Content.SPEECH, speech.content)
    }

    @Test
    fun canceledWarningCannotAffectTheClearEventOrRetryLater() {
        val policy = AudioFocusPolicy()
        val warning = policy.request(7, true, Content.SONIFICATION)
        assertTrue(policy.mayRetry(warning, 1000, 2000))
        policy.release()
        assertFalse(policy.isCurrent(warning, 7))
        assertFalse(policy.mayRetry(warning, 1100, 2000))
        val clear = policy.request(8, false, Content.SONIFICATION)
        assertFalse(policy.isCurrent(warning, 8))
        assertTrue(policy.isCurrent(clear, 8))
        assertEquals(Gain.MAY_DUCK, clear.gain)
        assertFalse(policy.mayRetry(clear, 1100, 2000))
    }

    @Test
    fun retryAttemptRejectsEarlierCallbacksAndStopsAtDeadline() {
        val policy = AudioFocusPolicy()
        val denied = policy.request(7, true, Content.SPEECH)
        val retry = policy.request(7, true, Content.SPEECH)
        assertFalse(policy.isCurrent(denied, 7))
        assertTrue(policy.isCurrent(retry, 7))
        assertTrue(policy.mayRetry(retry, 1999, 2000))
        assertFalse(policy.mayRetry(retry, 2000, 2000))
        assertFalse(policy.isCurrent(retry, 8))
    }
}
