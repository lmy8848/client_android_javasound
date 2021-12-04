package com.teamspeak.ts3sdkclient.connection

/**
 * TeamSpeak SDK client sample
 *
 * Copyright (c) 2007-2018 TeamSpeak Systems
 *
 * Required parameters to establish a server connection
 */
data class ConnectionParams(
        var identity: String,
        var address: String = "39.105.77.85",
        var port: Int = 10011,
        var nickname: String = "TeamSpeakUser",
        var serverPassword: String = "F7d8iQyd")
