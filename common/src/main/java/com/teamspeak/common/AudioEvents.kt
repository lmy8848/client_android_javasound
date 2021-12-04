package com.teamspeak.common

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import android.util.Log
import com.teamspeak.common.android.AudioFocusEvent
import com.teamspeak.common.android.AudioFocusRequestResultEvent
import com.teamspeak.common.android.BluetoothScoStateEvent
import com.teamspeak.common.android.HeadsetPlugEvent

object AudioEvents {

    private var TAG: String = AudioEvents.javaClass.simpleName

    //region AUDIO_BECOMING_NOISY
    private var mNoisyReceiver : BroadcastReceiver? = null

    /** A hint for applications that audio is about to become 'noisy' due to a change in audio
     * outputs. For example, this intent may be sent when a wired headset is unplugged, or when
     * an A2DP audio sink is disconnected, and the audio system is about to automatically switch
     * audio route to the speaker. Applications that are controlling audio streams may consider
     * pausing, reducing volume or some other action on receipt of this intent so as not to
     * surprise the user with audio from the speaker. */
    fun registerNoisyReceiver(context: Context, callback: IVoidEventListener) : Boolean {
        if (mNoisyReceiver != null)
            return false

        mNoisyReceiver = object : BroadcastReceiver(), IVoidEventListener {
            override fun onReceive(context: Context, intent: Intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                    callback.onEvent()
                }
            }
        }
        context.registerReceiver(mNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        return true
    }

    fun unregisterNoisyReceiver(context: Context) {
        if (mNoisyReceiver != null) {
            context.unregisterReceiver(mNoisyReceiver)
            mNoisyReceiver = null
        }
    }
    //endregion

    // TODO: Leaving out ACTION_HDMI_AUDIO_PLUG for now

    //region HEADSET_PLUG
    private var mHeadsetPlugReceiver : BroadcastReceiver? = null

    /** Wired Headset plugged in or unplugged. */
    @RequiresApi(22)
    fun registerHeadsetPlug(context: Context, callback: IEventListener) : Boolean {
        if (mHeadsetPlugReceiver != null)
            return false

        mHeadsetPlugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    Log.w(TAG,"headset plug event is missing intent")
                    return
                }

                if (AudioManager.ACTION_HEADSET_PLUG  == intent.action) {
                    if (!(intent.hasExtra("state") &&
                                    intent.hasExtra("name") &&
                                    intent.hasExtra("microphone"))) {
                        Log.w(TAG,"headset plug event is missing information")
                        return
                    }

                    val event = HeadsetPlugEvent(
                            state = intent.getIntExtra("state", 0) == 1,
                            name = intent.getStringExtra("name")!!,
                            microphone = intent.getIntExtra("microphone", 0) == 1
                    )
                    callback.onEvent(event)
                }
            }
        }
        context.registerReceiver(mHeadsetPlugReceiver,
                IntentFilter(AudioManager.ACTION_HEADSET_PLUG))

        return true
    }

    fun unregisterHeadsetPlug(context: Context) {
        if (mHeadsetPlugReceiver != null) {
            context.unregisterReceiver(mHeadsetPlugReceiver)
            mHeadsetPlugReceiver = null
        }
    }
    //endregion

    //region SCO_AUDIO_STATE_UPDATED
    /**
     * Registers receiver for the broadcasted intent related the existence
     * of a BT SCO channel. Indicates if BT SCO streaming is on or off.
     */
    private var mBluetoothScoReceiver : BroadcastReceiver? = null

    fun registerBluetoothSco(context: Context, callback: IEventListener) : Boolean {
        if (mBluetoothScoReceiver != null)
            return false

        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)

        /** BroadcastReceiver implementation which handles changes in BT SCO.  */
        mBluetoothScoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED)

                val previousState = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED)

                val event = BluetoothScoStateEvent(
                        previousStateVal = previousState,
                        stateVal = state)

                callback.onEvent(event)
            }
        }

        context.registerReceiver(mBluetoothScoReceiver, filter)
        return true
    }

    fun unregisterBluetoothSco(context: Context) {
        if (mBluetoothScoReceiver == null)
            return

        context.unregisterReceiver(mBluetoothScoReceiver)
        mBluetoothScoReceiver = null
    }
    //endregion

    //region AudioFocus
    private var mAudioFocusRequestResultCallback: IEventListener? = null
    private var mAudioFocusCallback: IEventListener? = null
    private val mAudioFocusRequestResultHandler = Handler()

    @RequiresApi(Build.VERSION_CODES.O)
    private var mAudioFocusRequest : AudioFocusRequest? = null

    private val onAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            Log.d(TAG, "In onAudioFocusChangeListener focus changed to = $focusChange")

            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                val requestResultEvent = AudioFocusRequestResultEvent(true)
                mAudioFocusRequestResultCallback?.onEvent(requestResultEvent)
            }

            val event = AudioFocusEvent(focusChange)
            mAudioFocusCallback?.onEvent(event)
        }

    fun requestAudioFocus(context: Context, requestResultCallback:IEventListener, focusChangeCallback:IEventListener) {
        if (mAudioFocusCallback != null)
            return

        mAudioFocusCallback = focusChangeCallback

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        var requestResult: Int = -99
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAcceptsDelayedFocusGain(true)
                    .setAudioAttributes(AudioHelpers.getAudioAttributes())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(onAudioFocusChangeListener)
                    .build()

            requestResult = audioManager.requestAudioFocus(mAudioFocusRequest!!)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            requestResult = audioManager.requestAudioFocus(
                    onAudioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN)
        }

        when (requestResult) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                val event = AudioFocusRequestResultEvent(true)
                mAudioFocusRequestResultHandler.postDelayed({
                    requestResultCallback.onEvent(event)
                },1)
            }
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                val event = AudioFocusRequestResultEvent(false)
                mAudioFocusRequestResultHandler.postDelayed({
                    requestResultCallback.onEvent(event)
                }, 1)
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Log.d(TAG, "AUDIOFOCUS_REQUES_DELAYED")
            }
            else -> {
                Log.e(TAG, "unknown Audiofocus request result.")
                val event = AudioFocusRequestResultEvent(false)
                mAudioFocusRequestResultHandler.postDelayed({
                    requestResultCallback.onEvent(event)
                }, 1)
            }
        }
    }

    fun abandonAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest != null)
                audioManager.abandonAudioFocusRequest(mAudioFocusRequest!!)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            audioManager.abandonAudioFocus(onAudioFocusChangeListener)
        }
    }
    //endregion

}