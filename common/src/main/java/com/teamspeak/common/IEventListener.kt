/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.common

interface IEventListener {
    fun onEvent(event: IEvent)
}
