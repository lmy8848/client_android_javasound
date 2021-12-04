/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.common

interface IAudioBluetooth {
    fun onBluetoothHeadsetConnectStatusChange(connected: Boolean)
}
