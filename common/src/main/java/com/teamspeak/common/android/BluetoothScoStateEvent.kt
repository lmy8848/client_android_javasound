package com.teamspeak.common.android

import android.media.AudioManager
import androidx.annotation.IntDef
import com.teamspeak.common.IEvent

data class BluetoothScoStateEvent(
        val previousStateVal : Int,
        val stateVal: Int
        ) : IEvent {

    @ScoAudioState
    val previousState = previousStateVal.toLong()

    @ScoAudioState
    val state = stateVal.toLong()

    companion object {
        @Target(AnnotationTarget.TYPE, AnnotationTarget.PROPERTY)
        @IntDef(DISCONNECTED, CONNECTED, CONNECTING, ERROR)
        @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
        annotation class ScoAudioState

        const val DISCONNECTED = AudioManager.SCO_AUDIO_STATE_DISCONNECTED
        const val CONNECTED = AudioManager.SCO_AUDIO_STATE_CONNECTED
        const val CONNECTING = AudioManager.SCO_AUDIO_STATE_CONNECTING
        const val ERROR = AudioManager.SCO_AUDIO_STATE_ERROR
    }
}
