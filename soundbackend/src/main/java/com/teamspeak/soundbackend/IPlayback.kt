/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.soundbackend

interface IPlayback {
    fun acquireCustomPlaybackData(
        deviceID: String,
        samples: Int
    ):
            Int
}
