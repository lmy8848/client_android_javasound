/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.soundbackend

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("MagicNumber", "TooGenericExceptionCaught")
class Record(
    private val iRecord: IRecord,
    private val deviceID: String
) : AudioRecord.OnRecordPositionUpdateListener {

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getAudioFormat(sampleRate: Int, channelMask: Int): AudioFormat {
        return AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()
    }

    /** Use a larger buffer size than the minimum required when creating the
     * AudioRecord instance to ensure smooth recording under load. It has been
     * verified that it does not increase the actual recording latency.
     */

    /** the minBufferSize Android reports */
    private var minBufferSizeInBytes: Int = 0

    /** the bufferSize we request */
    private var adjustedMinBufferSizeInBytes: Int = 0

    private var audioRecord: AudioRecord? = null

    init {
        /** prepare sampleRates list, ordered by preference */
        var sampleRates = mutableListOf(
            48000,
            44100,
            32000,
            22050,
            16000,
            8000,
            192000,
            96000,
            88200
        )
        /** prioritize native playback sample rate */
        val playbackSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL)
        val indexOfPlaybackSampleRate = sampleRates.indexOf(playbackSampleRate)
        if (indexOfPlaybackSampleRate == -1) {
            sampleRates = (mutableListOf(playbackSampleRate) + sampleRates).toMutableList()
        } else if (indexOfPlaybackSampleRate != 0) {
            (1..sampleRates.indexOf(indexOfPlaybackSampleRate)).forEach {
                Collections.swap(sampleRates, 0, it)
            }
        }

        /** using stereo last due to bugs (at least known on opensl es for some devices)
         * where the camera mic is then used as right channel */
        for (testChannelMask in intArrayOf(AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO)) {
            for (testSampleRate in sampleRates) {
                minBufferSizeInBytes = AudioRecord.getMinBufferSize(
                        testSampleRate,
                        testChannelMask,
                        AudioFormat.ENCODING_PCM_16BIT)

                if (minBufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) continue

                for (testMultiplicand in intArrayOf(2, 1)) {
                    /** try instantiation of AudioRecord */
                    try {
                        adjustedMinBufferSizeInBytes = testMultiplicand * minBufferSizeInBytes
                        audioRecord = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            AudioRecord.Builder()
                                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                                    .setAudioFormat(getAudioFormat(testSampleRate, testChannelMask))
                                    .setBufferSizeInBytes(adjustedMinBufferSizeInBytes)
                                    .build()
                        } else {
                            AudioRecord(
                                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                                    testSampleRate,
                                    testChannelMask,
                                    AudioFormat.ENCODING_PCM_16BIT,
                                    adjustedMinBufferSizeInBytes)
                        }
                        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) break
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio record instantiation exception:${e.message}")
                    } finally {
                        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                            audioRecord?.release()
                            audioRecord = null
                        }
                    }
                }
                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) break
            }
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) break
        }
    }
    val sampleRate: Int = audioRecord?.sampleRate ?: 48000
    val channelCount: Int = audioRecord?.channelCount ?: 1

    private var paused = AtomicBoolean(false)
    private var handlerThread: HandlerThread? = null

    //region OnRecordPositionUpdateListener
    override fun onMarkerReached(recorder: AudioRecord?) {
        // Do nothing
    }

    override fun onPeriodicNotification(recorder: AudioRecord?) {
        if (recorder != null) {
            processAudio(recorder)
        }
    }
    //endregion

    val buffer: ByteBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        /** Elvis operator fallback creates minimal buffer so we can stay at val */
        ByteBuffer.allocateDirect((audioRecord?.bufferSizeInFrames ?: 1) * (audioRecord?.channelCount ?: 1) * 2)
    } else {
        ByteBuffer.allocateDirect(adjustedMinBufferSizeInBytes)
    }

    private fun processAudio(recorder: AudioRecord) {
        if (!paused.get() && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            /** read and request size are determined by the array, i.e. shorts for shortArrays, bytes for byteArrays */
            val expectedSize = recorder.positionNotificationPeriod * recorder.channelCount * 2
            val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                recorder.read(buffer, buffer.capacity(), AudioRecord.READ_NON_BLOCKING)
            else /** we use a large buffer, don't request it all in this blocking read */
                recorder.read(buffer, expectedSize)

            if (read < 0) {
                Log.w("AudioThread", "status: $read during recording!")
            } else if (read > 0) {
                /** in case during read we're about to be stopped,
                 * let's not call native anymore */
                if (!paused.get()) {
                    val samples = read / (2 * recorder.channelCount)
                    iRecord.processCustomCaptureData(deviceID, samples) /** client uses FRAMES instead of samples */
                }
            }
        }
    }

    companion object {
        val TAG: String = Record::class.java.simpleName
        @IntDef(START, PAUSE, STOP, SHUTDOWN)
        @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
        annotation class RecordingState
        const val START = 0
        const val PAUSE = 1
        const val STOP = 2
        const val SHUTDOWN = 3
    }

    fun setRecording(@RecordingState desiredState: Int) = try {
        when (desiredState) {
            START -> handleStartState()
            PAUSE -> handlePausedState()
            else -> handleOtherState(desiredState)
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    private fun handleOtherState(desiredState: Int) {
        paused.set(true)

        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord?.stop()
            audioRecord?.positionNotificationPeriod = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            handlerThread?.quitSafely()
        } else {
            handlerThread?.quit()
        }
        handlerThread = null

        if (desiredState == SHUTDOWN) {
            audioRecord?.release()
            audioRecord = null
        }
    }

    private fun handlePausedState() {
        if (!paused.compareAndSet(false, true)) return

        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord?.stop()
            audioRecord?.positionNotificationPeriod = 0
        }
    }

    private fun handleStartState() {
        paused.set(false)

        if (handlerThread == null) {
            handlerThread = HandlerThread("audio record thread",
                Process.THREAD_PRIORITY_URGENT_AUDIO)
        }
        if (!handlerThread!!.isAlive)
            handlerThread!!.start()

        /* getLooper() returns null if thread isn't running */
        while (!handlerThread!!.isAlive)
            Thread.yield()

        /** according to docs, this is in FRAMES */
        audioRecord?.positionNotificationPeriod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /** for non-blocking read we simply go for 20ms */
            (audioRecord?.sampleRate ?: 48000) * 2 / (100 * (audioRecord?.channelCount
                ?: 1))
        } else {
            /** for blocking read, we aim to read minBufferSize */
            minBufferSizeInBytes / (2 * (audioRecord?.channelCount ?: 1))
        }
        Log.d(TAG, "Update frequency: " + audioRecord?.positionNotificationPeriod + " frames")

        audioRecord?.setRecordPositionUpdateListener(
            this, Handler(handlerThread!!.looper))

        audioRecord?.startRecording()

        /* Workaround: needed to trigger OnRecordPositionUpdateListener on Android < 5.0.1
                   Otherwise capture wont work on devices < 5.0 */
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            audioRecord?.read(buffer, minBufferSizeInBytes)
        }

        Log.d(TAG, "Audio capture started. Sample Rate: $sampleRate Channel Count: $channelCount")
    }

    /**
     * Sets the routing of this audio record to a device
     *
     * @return true if successful, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?): Boolean {
        return audioRecord?.setPreferredDevice(audioDeviceInfo) ?: false
    }

    /**
     * Returns the routing of this audio record
     *
     * @return the device, which audio is routed to
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getRoutedDevice(): AudioDeviceInfo? {
        return audioRecord?.routedDevice
    }
}
