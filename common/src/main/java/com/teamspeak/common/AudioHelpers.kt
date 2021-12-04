/*
 * Copyright 2019 TeamSpeak Systems, Inc.
 */
package com.teamspeak.common

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log

object AudioHelpers {

    private const val TAG = "AudioHelpers"

    fun getConnectedDeviceTypes(audioManager: AudioManager, inOrOut: Boolean): Pair<Boolean, Boolean> {
        var hasHeadDevice = false
        var hasBtDevice = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val flag = if (inOrOut)
                AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS

            val devices = audioManager.getDevices(flag)

            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET)
                    hasHeadDevice = true
                else if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                    hasBtDevice = true
            }
        } else {
            // other option for headset plug:
            /* val iFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        val iStatus = context.registerReceiver(null, iFilter)
        val isConnected = iStatus.getIntExtra("state", 0) == 1 */
            // TODO dunno about those bluetooth
            hasHeadDevice = audioManager.isWiredHeadsetOn
            hasBtDevice = audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn || audioManager.isBluetoothScoAvailableOffCall
        }
        return Pair(hasHeadDevice, hasBtDevice)
    }

    // TODO: test if this applies to opensl es, otherwise use key handling like in TS android app
    fun setVolumeControlStreamVoiceCall(activity: Activity) {
        activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL
    }

    fun setVolumeControlStreamRinging(activity: Activity) {
        activity.volumeControlStream = AudioManager.STREAM_RING
    }

    fun setSpeakerphoneOn(audioManager: AudioManager, on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    /**
     * This method will start and stop the bluetooth sco connection.
     *
     * @param audioManager used to start and stop bluetooth sco connection
     * @param on if true the bluetooth sco connection will be started, if false it will be stopped
     */
    fun setBluetoothSco(audioManager: AudioManager, on: Boolean) {
        if (audioManager.isBluetoothScoAvailableOffCall) {
            if (on && !audioManager.isBluetoothScoOn) {
                Log.d(TAG, "bt sco off, starting sco connection")
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
            } else if (!on && audioManager.isBluetoothScoOn) {
                Log.d(TAG, "bt sco on, stopping sco connection")
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build()
    }

    /**
     * Returns an array of available audio device infos (bluetooth headset, speaker etc.). Input
     * and the telephony devices will not be included.
     *
     * This function is only available on Android devices >= Android M (Api 23). On devices lower
     * than api 23 null will be returned.
     *
     * @param audioManager is needed to retrieve the list of available output devices
     * @return an array with available audio device infos
     */
    fun getAvailableOutputAudioDevices(audioManager: AudioManager): ArrayList<AudioDeviceInfo> {
        val audioDeviceInfos = ArrayList<AudioDeviceInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach {
                // only add sources and prevent adding the telephony type
                if (it.type != AudioDeviceInfo.TYPE_TELEPHONY && it.isSink) {
                    audioDeviceInfos.add(it)
                }
            }
        }
        return audioDeviceInfos
    }



    /**
     * Simple method for getting a readable string from the audio device type used in
     * AudioDeviceInfo
     *
     * @param audioDeviceInfo the audio device to get the typ from
     * @return a redable String from the type of the device (e.g. TYPE_BLUETOOTH_SCO,
     * TYPE_BUILTIN_SPEAKER, etc.)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getNameOfAudioDeviceType(audioDeviceInfo: AudioDeviceInfo): String {
        var name = ""
        when (audioDeviceInfo.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> name = "Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> name = "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> name = "Microphone"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> name = "Earpiece"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> name = "Speaker"
            AudioDeviceInfo.TYPE_TELEPHONY -> name = "Telephone"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> name = "Headphone"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> name = "Headset"
        }
        return name
    }

    /**
     * This method will look up the requested type in the available audio devices and return the
     * audio device info if a match has been found.
     *
     * @param audioManager is an instance of the AudioManager used for retrieving the list of
     * available input or output devices
     * @param type is the type of the requested audio device
     * (e.g. AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
     * @param deviceFlag specifies the list of available devices
     * (e.g. AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS)
     *
     * @return the matched AudioDeviceInfo or null
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getAudioDeviceInfoFromType(audioManager: AudioManager, type: Int, deviceFlag: Int):
            AudioDeviceInfo? {
        val audioDevices = audioManager.getDevices(deviceFlag)
        (audioDevices).forEach {
            if (it.type == type) return it
        }
        return null
    }

    /**
     * This method will look up the requested id in the available audio devices and return the
     * audio device info if a match has been found.
     *
     * @param audioManager is an instance of the AudioManager used for retrieving the list of
     * available input or output devices
     * @param id is the id of the requested audio device
     * @param deviceFlag specifies the list of available devices
     * (e.g. AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS)
     *
     * @return the matched AudioDeviceInfo or null
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getAudioDeviceInfoFromId(audioManager: AudioManager, id: Int, deviceFlag: Int):
            AudioDeviceInfo? {
        val audioDevices = audioManager.getDevices(deviceFlag)
        (audioDevices).forEach {
            if (it.id == id) return it
        }
        return null
    }

    /**
     * This will return the appropriate audio device for input depending on the passed audio output
     * device. This means this method will try to return a matching audio input device depending on
     * the following cases:
     * - Bluetooth: Return bluetooth mic, if existing
     * - Wired headset: Return the mic of the wired headset, if existing
     *
     * @param audioManager the audioManager is used to retrieve the audio input devices
     * @param audioDeviceInfo this is the audio output device
     * @return the appropriate audio input device or null
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getAppropriateInputDeviceFromAudioDevice(
        audioManager: AudioManager,
        audioDeviceInfo: AudioDeviceInfo
    ):
            AudioDeviceInfo? {
        val audioInputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        if (isBluetoothDevice(audioDeviceInfo)) {
            // output device is bluetooth, return bluetooth or default mic for input
            audioInputDevices.forEach {
                if (isBluetoothDevice(it)) return it
            }
        } else if (isWiredHeadset(audioDeviceInfo)) {
            // output is wired headset, return wired headset mic or default mic
            audioInputDevices.forEach {
                if (isWiredHeadset(it)) return it
            }
        }
        // no matching devices found, return default device mic
        return getDefaultDeviceMic(audioManager)
    }

    /**
     * This will return the default device mic for input or null
     *
     * @param audioManager the audioManager is used to retrieve the audio input devices
     * @return the default device mic as AudioDeviceInfo or null
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getDefaultDeviceMic(audioManager: AudioManager): AudioDeviceInfo? {
        val audioInputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        audioInputDevices.forEach {
            if (it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) return it
        }
        return null
    }

    /**
     * This method will check if the requested audio device info is a bluetooth device.
     *
     * @param audioDeviceInfo the device to check if its a bluetooth device
     *
     * @return true if the audio device is a bluetooth device, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun isBluetoothDevice(audioDeviceInfo: AudioDeviceInfo): Boolean {
        when (audioDeviceInfo.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                return true
        }
        return false
    }

    /**
     * This method will check if the requested audio device info is a wired headset.
     *
     * @param audioDeviceInfo the device to check if its a wired headset
     *
     * @return true if the audio device is a wired headset, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun isWiredHeadset(audioDeviceInfo: AudioDeviceInfo): Boolean {
        when (audioDeviceInfo.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> return true
        }
        return false
    }
}
