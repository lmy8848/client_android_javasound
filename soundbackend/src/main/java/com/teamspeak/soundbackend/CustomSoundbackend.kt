/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.soundbackend

/**
 * Object CustomSoundBackend (Singleton)
 * Use this object to init and uninit the soundbackend.
 */
object CustomSoundbackend {

    private lateinit var iCustomSoundbackend: ICustomSoundbackend
    private const val deviceID = "Java"
    private const val displayName = "Java"
    lateinit var playback: Playback
    lateinit var record: Record
    var isInitialized = false

    /**
     * Call this method to initialize the soundbackend. This is necessary, otherwise the
     * soundbackend is not functional.
     *
     * @param iCustomSoundbackend object which implemented the ICustomSoundbackend
     * @param iPlayback object which implemented the IPlayback
     * @param iRecord object which implemented the IRecord
     */
    fun prepareAudio(
        iCustomSoundbackend: ICustomSoundbackend,
        iPlayback: IPlayback,
        iRecord: IRecord
    ): CustomSoundbackend {

        // assign member
        this.iCustomSoundbackend = iCustomSoundbackend

        // init process
        playback = Playback(iPlayback, deviceID)
        record = Record(iRecord, deviceID)
        this.iCustomSoundbackend.registerCustomDevice(
                deviceID = deviceID,
                deviceDisplayName = displayName,
                capFrequency = record.sampleRate,
                capChannels = record.channelCount,
                capByteBuffer = record.buffer,
                playFrequency = playback.sampleRate,
                playChannels = playback.channelCount,
                playByteBuffer = playback.byteBuffer
        )
        isInitialized = true

        // return object
        return this
    }

    /**
     * Unregisters the soundbackend and set its state to uninitialized. After this you need to init
     * the soundbackend again.
     */
    fun unregister() {
        this.iCustomSoundbackend.unregisterCustomDevice(deviceID)
        isInitialized = false
    }
}
