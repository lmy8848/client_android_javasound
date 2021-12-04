package com.teamspeak.ts3sdkclient.ts3sdk.events

import com.teamspeak.ts3sdkclient.eventsystem.TsEvent

data class PlaybackShutdownComplete(val serverConnectionHandlerID : Long = 0)
    : TsEvent()