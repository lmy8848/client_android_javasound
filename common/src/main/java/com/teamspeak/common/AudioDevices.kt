/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.common

import android.content.Context
import android.media.AudioManager
import androidx.annotation.IntDef
import com.teamspeak.common.internal.AudioBluetooth
import java.util.Arrays
import android.util.Log

class AudioDevices constructor(
    context: Context,
    private val audioManager: AudioManager
) : IAudioBluetooth {

    companion object {

        private val TAG = AudioDevices::class.java.simpleName

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(DEVICE_NORMAL, DEVICE_INTERNAL_SPEAKER, DEVICE_HEADSET, DEVICE_BLUETOOTH_HEADSET)
        annotation class DeviceType
        const val DEVICE_NORMAL = 0 // internal mic and speaker
        const val DEVICE_INTERNAL_SPEAKER = 1 // internal mic and loud speaker
        const val DEVICE_HEADSET = 2 // plugged in headset
        const val DEVICE_BLUETOOTH_HEADSET = 3 // headset connected with bluetooth
    }

    private var audioBluetooth: AudioBluetooth? = null
//    private val audioWired = AudioWired()

    private val outputDevices = BooleanArray(4)

    init {
        Arrays.fill(outputDevices, false)
        outputDevices[DEVICE_NORMAL] = true

        // init AudioBluetooth
        audioBluetooth = AudioBluetooth(context, audioManager, this)
    }

    fun isOutputDeviceEnabled(@DeviceType device: Int): Boolean {
        return outputDevices[device]
    }

    private fun enableOutputDevice(@DeviceType device: Int) {
        outputDevices[device] = true
        Log.i(TAG, "${deviceToString(device)} became available")

        when (device) {
            DEVICE_BLUETOOTH_HEADSET -> {
                // enable bluetooth
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.isBluetoothScoOn = true
                    audioManager.startBluetoothSco()
                }
            }
            DEVICE_INTERNAL_SPEAKER -> {
                // enable speaker
                audioManager.isSpeakerphoneOn = true
            }
        }
    }

    fun disableOutputDevice(@DeviceType device: Int) {
        outputDevices[device] = false
        Log.i(TAG, "${deviceToString(device)} became unavailable")

        when (device) {
            DEVICE_BLUETOOTH_HEADSET -> {
                // disable bluetooth if not used anymore
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
            }
            DEVICE_INTERNAL_SPEAKER -> {
                // disable speaker
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    /*public
    @AudioDevices.AudioDevice
    int chooseBestOutputDevice() {
        if (outputDevices[DEVICE_INTERNAL_SPEAKER]) {
            return DEVICE_INTERNAL_SPEAKER;
        } else if (outputDevices[DEVICE_HEADSET]) {
            return DEVICE_HEADSET;
        } else if (outputDevices[DEVICE_BLUETOOTH_HEADSET] && sharedPreferencesHelper.isBtActive()) {
            return DEVICE_BLUETOOTH_HEADSET;
        } else {
            return DEVICE_NORMAL;
        }
    }*/

    fun start() {
//        listenToHeadsetConnection()
        audioBluetooth?.start()
    }

    fun deviceToString(@DeviceType device: Int): String {
        return when (device) {
            DEVICE_INTERNAL_SPEAKER -> "Speaker"
            DEVICE_BLUETOOTH_HEADSET -> "Bluetooth Headset"
            DEVICE_HEADSET -> "Cable Headset"
            DEVICE_NORMAL -> "Earpiece"
            else -> "Earpiece"
        }
    }

//    private fun listenToHeadsetConnection() {
//        val filter: IntentFilter = IntentFilter()
//        filter.addAction(Intent.ACTION_HEADSET_PLUG);
//        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
//        disposableContainer.add(
//                RxBroadcast.fromBroadcast(app, filter)
//                        .subscribeOn(Schedulers.io())
//                        .observeOn(Schedulers.io())
//                        .subscribe(new Consumer<Intent>() {
//                            @Override
//                            public void accept(Intent intent) throws Exception {
//                                if (intent.hasExtra("state")) {
//                                    int state = intent.getIntExtra("state", 0);
//                                    if (isOutputDeviceEnabled(DEVICE_HEADSET) && state == 0) {
//                                        disableOutputDevice(DEVICE_HEADSET);
//                                    } else if (!isOutputDeviceEnabled(DEVICE_HEADSET) && state == 1) {
//                                        sharedPreferencesHelper.setHandsFree(false);
//                                        enableOutputDevice(DEVICE_HEADSET);
//                                    }
//                                }
//                            }
//                        })
//        );
//    }

    override fun onBluetoothHeadsetConnectStatusChange(connected: Boolean) {
        if (connected) {
            enableOutputDevice(DEVICE_BLUETOOTH_HEADSET)
        } else {
            disableOutputDevice(DEVICE_BLUETOOTH_HEADSET)
        }
    }
}
