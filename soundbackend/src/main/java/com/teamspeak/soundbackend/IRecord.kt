/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.soundbackend

interface IRecord {
    fun processCustomCaptureData(
        deviceID: String,
        samples: Int
    ):
            Int
}
