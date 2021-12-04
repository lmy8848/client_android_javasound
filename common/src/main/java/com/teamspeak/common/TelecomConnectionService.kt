/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.common

import android.os.Build
import android.telecom.Connection
import android.telecom.Connection.CAPABILITY_HOLD
import android.telecom.Connection.CAPABILITY_SUPPORT_HOLD
import android.telecom.Connection.PROPERTY_SELF_MANAGED
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class TelecomConnectionService : ConnectionService() {

    /** Telecom calls this method in response to your app calling placeCall to create a new outgoing call. */
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val ourConnection = MessengerConnection()

        // TODO will likely crash on N_MR1
        @RequiresApi(Build.VERSION_CODES.N_MR1)
        ourConnection.connectionProperties = PROPERTY_SELF_MANAGED

        ourConnection.connectionCapabilities = CAPABILITY_HOLD
        ourConnection.connectionCapabilities = CAPABILITY_SUPPORT_HOLD

        return ourConnection
    }

    /** called in response to addIncomingConnection, if Android allows */
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val ourConnection = MessengerConnection()

        // TODO will likely crash on N_MR1
        @RequiresApi(Build.VERSION_CODES.N_MR1)
        ourConnection.connectionProperties = PROPERTY_SELF_MANAGED

        ourConnection.connectionCapabilities = CAPABILITY_HOLD
        ourConnection.connectionCapabilities = CAPABILITY_SUPPORT_HOLD

        return ourConnection
    }

    inner class MessengerConnection : Connection() {

        /*override fun onAnswer(videoState: Int) {
            super.onAnswer(videoState)
        }*/

        /** Telecom calls this method when it wants to reject an incoming call.
         * Once your app has rejected the call, it should call
         * setDisconnected(android.telecom.DisconnectCause) and specify REJECTED.
         * Your app should then call destroy() to inform Telecom you are done with the call.
         * Similar to onAnswer(), Telecom will call this method when the user has rejected an
         * incoming call from your app. */
        override fun onReject() {
            // TODO

            this.setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        }

        /** Telecom calls this method when it wants to disconnect a call. Once the call has ended,
         * your app should call setDisconnected(android.telecom.DisconnectCause) and specify LOCAL
         * to indicate that a user request caused the call to be disconected.
         * Your app should then call destroy() to inform Telecom you are done with the call.
         * Telecom may call this method when the user has disconnected a call through another
         * InCallService such as Android Auto.*/
        override fun onDisconnect() {
            this.destroy()
        }
    }
}
