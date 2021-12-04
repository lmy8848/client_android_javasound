package com.teamspeak.common

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.teamspeak.common.AudioEvents
import com.teamspeak.common.IEvent
import com.teamspeak.common.IEventListener
import com.teamspeak.common.android.AudioFocusEvent
import com.teamspeak.common.android.AudioFocusRequestResultEvent

class AndroidManagement(val context: Context) : IEventListener {

    companion object {
        private val TAG : String = AndroidManagement::class.java.simpleName
    }
    private val audioEvents = AudioEvents
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val proximity: Proximity = Proximity(context, object: Proximity.Delegate{

        override fun onProximitySensorNear() {
            Log.d(TAG, "Proximity near")
            setSpeakerphoneOn(false)
        }

        override fun onProximitySensorFar() {
            Log.d(TAG, "Proximity far")
            setSpeakerphoneOn(true)
        }

    })

    fun setSpeakerphoneOn(on: Boolean) {
        if (audioManager.isSpeakerphoneOn != on)
            audioManager.isSpeakerphoneOn = on
    }

    fun startProximityActions() {
        proximity.acquireProximityScreenWakeLock()
        //proximity.startCustomSensor()
    }

    fun stopProximityActions() {
        proximity.releaseProximityScreenWakeLock()
        //proximity.stopCustomSensor()
    }

    override fun onEvent(event: IEvent) {
        when (event) {
            is AudioFocusRequestResultEvent -> {
                if (event.granted) {
                    /** We don't need to do anything here */
                    Log.d(TAG, "Audio Focus granted")
                } else {
                    Log.d(TAG, "Couldn't get Audio Focus")
                }
            }
            is AudioFocusEvent -> {
                when (event.audioFocusChange) {
                    AudioFocusEvent.AUDIOFOCUS_GAIN -> {
                        /** unlike the android API, we fire this always as complete async api
                         * not a mixture
                         */
                        // TODO start the audio for the call
                        Log.d(TAG,"AudioFocus Gain")
                    }
                    AudioFocusEvent.AUDIOFOCUS_LOSS -> {
                        Log.d(TAG, "AudioFocus Loss")
                        // TODO silence
                    }
                    AudioFocusEvent.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Log.d(TAG, "AudioFocus Loss Transient")
                        // TODO silence
                    }
                    AudioFocusEvent.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        Log.d(TAG, "AudioFocus Loss Transient Can Duck")
                        // TODO silence-y?
                    }
                }
            }
        }
    }

    fun requestAudioFocus() {
        audioEvents.requestAudioFocus(context, this, this)
    }

    fun abandonAudioFocus() {
        audioEvents.abandonAudioFocus(context)
    }

    fun setMode(mode: Int) {
        audioManager.mode = mode
    }
}