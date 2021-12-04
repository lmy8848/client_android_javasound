/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.soundbackend

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@Suppress("TooManyFunctions", "MagicNumber")
class Playback(
    private val iPlayback: IPlayback,
    private val deviceID: String,
    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL),
    val channelCount: Int = 2
) : AudioTrack.OnPlaybackPositionUpdateListener {
    private var handlerThread: HandlerThread? = null
    private var paused = AtomicBoolean(false)

    private var lastPlaybackHeadPosition = 0

    //region OnPlaybackPositionUpdateListener
    override fun onMarkerReached(track: AudioTrack?) {
        // alternative if PeriodicNotification turns out to be unreliable
    }

    override fun onPeriodicNotification(track: AudioTrack?) {
        if (paused.get())
            return

        if (track == null)
            return

        if (track.state == AudioTrack.STATE_UNINITIALIZED)
            return

        val playbackHeadPosition = track.playbackHeadPosition
        val playedFramesSinceLastLoop = playbackHeadPosition - lastPlaybackHeadPosition
        if (playedFramesSinceLastLoop == 0)
            return

        val frames = min(byteBuffer.capacity() / 2, playedFramesSinceLastLoop)
        processAudio(track, frames)
        lastPlaybackHeadPosition = playbackHeadPosition
    }
    //endregion

    companion object {
        val TAG: String = Playback::class.java.simpleName

        @Suppress("ComplexMethod")
        fun getChannelConfig(channelCount: Int): Int {
            return when (channelCount) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                3 -> AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER
                4 -> AudioFormat.CHANNEL_OUT_QUAD
                5 -> AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_FRONT_CENTER
                6 -> AudioFormat.CHANNEL_OUT_5POINT1
                7 -> AudioFormat.CHANNEL_OUT_5POINT1 or AudioFormat.CHANNEL_OUT_BACK_CENTER
                8 -> getAudioFormatFor8ChannelCount()
                else -> -1
            }
        }

        private fun getAudioFormatFor8ChannelCount(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            } else {
                @Suppress("DEPRECATION")
                AudioFormat.CHANNEL_OUT_7POINT1
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun getAudioAttributes(): AudioAttributes {
            return AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
        }

        @IntDef(START, PAUSE, STOP, SHUTDOWN)
        @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
        annotation class PlaybackState

        const val START = 0
        const val PAUSE = 1
        const val STOP = 2
        const val SHUTDOWN = 3
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getAudioFormat(): AudioFormat {
        return AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
    }

    private val adjustedMinBufferSizeInBytes: Int
    init {
        val minBufferSizeInBytes = AudioTrack.getMinBufferSize(
                sampleRate, getChannelConfig(channelCount), AudioFormat.ENCODING_PCM_16BIT)
        /** let's make sure we're above 20ms */
        val tenMsInBytes = channelCount * 2 * sampleRate / 100
        val minBuffersInTwentyMs = 2 * tenMsInBytes / minBufferSizeInBytes
        adjustedMinBufferSizeInBytes = if (minBuffersInTwentyMs > 0)
            (minBuffersInTwentyMs + 1) * minBufferSizeInBytes
        else
            minBufferSizeInBytes
    }

    private var audioTrack: AudioTrack
    var byteBuffer: ByteBuffer

    init {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val audioTrackBuilder = AudioTrack.Builder()
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setAudioFormat(getAudioFormat())
                        .setAudioAttributes(getAudioAttributes())
                        .setBufferSizeInBytes(adjustedMinBufferSizeInBytes)

                /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    //audioTrackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    audioTrackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                }*/
                audioTrack = audioTrackBuilder.build()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> audioTrack = AudioTrack(
                    getAudioAttributes(),
                    getAudioFormat(),
                    adjustedMinBufferSizeInBytes,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE)
            else -> audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                getChannelConfig(channelCount),
                AudioFormat.ENCODING_PCM_16BIT,
                adjustedMinBufferSizeInBytes,
                AudioTrack.MODE_STREAM)
        }

        /** byte buffer cannot grow, make it reasonable big
         * (audiotrack buffersize can grow on reroute plus get current size is api 26+)
         * if an overrun occurs, the oldest data will be dropped
         */
        val tenMsInBytes = channelCount * 2 * sampleRate / 100
        val halfSecondInBytes = 50 * tenMsInBytes
        val adjustedBuffersInHalfSecond = halfSecondInBytes / adjustedMinBufferSizeInBytes
        val byteBufferSize = if (adjustedBuffersInHalfSecond > 0)
            (adjustedBuffersInHalfSecond + 1) * adjustedMinBufferSizeInBytes
        else
            adjustedMinBufferSizeInBytes

        byteBuffer = ByteBuffer.allocateDirect(byteBufferSize)
    }

    /*@RequiresApi(Build.VERSION_CODES.M)
    private fun getBufferSizeInBytes() : Int {
        return audioTrack.bufferSizeInFrames * audioTrack.channelCount * 2
    }*/

    @Suppress("MagicNumber")
    private fun startAudioTrack(): Int {
        var slept = 0
        while (true) {
            try {
                audioTrack.play()
                return slept
            } catch (e: IllegalStateException) {
                try {
                    Log.e(TAG, "Audio Play IllegalStateException, retry, error: " + e.message)
                    Thread.sleep(20)
                    slept += 20
                } catch (e1: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return -1
                }
            }
        }
    }

    private fun prefillWithSilence() {
        val silentStartArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ShortArray(audioTrack.bufferSizeInFrames * audioTrack.channelCount) { 0 }
        } else {
            ShortArray(adjustedMinBufferSizeInBytes / 2) { 0 }
        }
        audioTrack.write(silentStartArray, 0, silentStartArray.size)
    }

    private fun processAudio(track: AudioTrack, frames: Int) {
        val error = iPlayback.acquireCustomPlaybackData(deviceID, frames)
        if (error == 0 || error == 0x0917) { // ERROR_sound_no_data, byteBuffer should be zeroed
            val amountBytes = frames * 2 * track.channelCount
            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                track.write(byteBuffer, amountBytes, AudioTrack.WRITE_BLOCKING)
            } else {
                track.write(byteBuffer.array(), byteBuffer.arrayOffset(), amountBytes)
            }
            if (written < 0) {
                // TODO an error occurred
                Log.e(TAG, "Written: $written")
            }
            byteBuffer.rewind()
        } else {
            Log.e(TAG, "processAudio ERROR: $error") // 1537: requested more than buffer size
            if (error == 1537) {
                val playbackHeadPosition = track.playbackHeadPosition
                val byteBufferCapacity = byteBuffer.capacity()
                Log.d(TAG, "Requested too many frames: $frames")
                Log.d(TAG, "lastPlaybackHeadPosition: $lastPlaybackHeadPosition playbackHeadPosition: $playbackHeadPosition")
                Log.d(TAG, "byteBuffer capacity: $byteBufferCapacity")
            }
            try {
                track.stop()
                track.flush()
                track.release()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Audio Play STOP IllegalStateException, retry, error: " + e.message)
            }
            Thread.currentThread().interrupt()
        }
    }

    /** PAUSE keeps the thread but pauses audio actions, will resume on START
     * STOP also stops the thread, will be recreated on START
     * SHUTDOWN is to be called when not needed anymore; class instance cannot recover **/
    fun setPlayback(@PlaybackState desiredState: Int) = when (desiredState) {
        START -> handleStartState()
        PAUSE -> handlePausedState()
        else -> handleOtherState(desiredState)
    }

    private fun handleOtherState(desiredState: Int) {
        paused.set(true)
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.stop()
            audioTrack.positionNotificationPeriod = 0
            lastPlaybackHeadPosition = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            handlerThread?.quitSafely()
        } else {
            handlerThread?.quit()
        }
        handlerThread = null

        if (desiredState == SHUTDOWN)
            audioTrack.release()
    }

    private fun handlePausedState() {
        if (!paused.compareAndSet(false, true))
            return

        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.stop() // TODO Why didn't I use pause?
            audioTrack.positionNotificationPeriod = 0
            lastPlaybackHeadPosition = 0
        }
    }

    private fun handleStartState() {
        paused.set(false)

        if (handlerThread == null) {
            handlerThread = HandlerThread("audio playback thread",
                Process.THREAD_PRIORITY_URGENT_AUDIO)
        }
        if (!handlerThread!!.isAlive)
            handlerThread!!.start()

        /* getLooper() returns null if thread isn't running */
        while (!handlerThread!!.isAlive)
            Thread.yield()

        audioTrack.positionNotificationPeriod = audioTrack.sampleRate * 2 / 100
        audioTrack.setPlaybackPositionUpdateListener(
            this, Handler(handlerThread!!.looper))

        prefillWithSilence()
        startAudioTrack()
    }

    /**
     * Sets the preferred device to route audio to. The default routing will be used if the passed
     * device is null.
     *
     * @return true if successful, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?): Boolean {
        return audioTrack.setPreferredDevice(audioDeviceInfo)
    }

    /**
     * Returns the audio device info which output is routed to
     *
     * @return audio device info or null
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun gerRoutedDevice(): AudioDeviceInfo? {
        return audioTrack.routedDevice
    }
}
