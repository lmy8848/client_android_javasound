/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.common.android

import com.teamspeak.common.IEvent
data class AudioFocusRequestResultEvent(val granted: Boolean) : IEvent
