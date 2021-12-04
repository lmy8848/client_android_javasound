/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.soundbackend

import java.nio.ByteBuffer

interface ICustomSoundbackend {
    fun unregisterCustomDevice(deviceID: String): Int

    @Suppress("LongParameterList")
    fun registerCustomDevice(
        deviceID: String,
        deviceDisplayName: String,
        capFrequency: Int,
        capChannels: Int,
        capByteBuffer: ByteBuffer,
        playFrequency: Int,
        playChannels: Int,
        playByteBuffer: ByteBuffer
    ): Int
}
