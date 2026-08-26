package com.rudimentor.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.rudimentor.app.data.OutputDevice
import com.rudimentor.app.data.OutputKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tells whether the sound leaves the phone through headphones rather than the
 * speaker.
 *
 * The click is what needs this: with the speaker open the microphone hears the
 * click and scores it as a stroke (decision 88), so the click can only be on when
 * the sound is private. The app follows this automatically until the learner
 * touches the switch by hand (decision 114).
 */
object AudioOutputMonitor {
    /** True while at least one private output -- wired, USB or Bluetooth -- is attached. */
    fun isConnected(context: Context): Boolean {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        return isConnected(manager)
    }

    /**
     * Emits the current state at once and then on every device change. The callback
     * is registered on the main looper, which is where [AudioManager] delivers it.
     */
    fun connectedFlow(context: Context): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(AudioManager::class.java)
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        trySend(isConnected(manager))
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(isConnected(manager))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(isConnected(manager))
            }
        }
        val handler = Handler(Looper.getMainLooper())
        manager.registerAudioDeviceCallback(callback, handler)
        awaitClose { manager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    /**
     * The private output the sound goes through right now, or null when it is the speaker.
     *
     * With several attached -- headphones plugged in while earbuds are still paired -- the
     * one Android actually routes to is not readable without owning the stream, so the
     * order below is a guess that matches how the system behaves in practice: Bluetooth
     * wins, then USB, then the jack.
     */
    fun currentDevice(context: Context): OutputDevice? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        return currentDevice(manager)
    }

    /** Emits the routed output at once and then on every device change. */
    fun deviceFlow(context: Context): Flow<OutputDevice?> = callbackFlow {
        val manager = context.getSystemService(AudioManager::class.java)
        if (manager == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        trySend(currentDevice(manager))
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(currentDevice(manager))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(currentDevice(manager))
            }
        }
        val handler = Handler(Looper.getMainLooper())
        manager.registerAudioDeviceCallback(callback, handler)
        awaitClose { manager.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    private fun currentDevice(manager: AudioManager): OutputDevice? {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in privateTypes() }
        if (devices.isEmpty()) return null
        val chosen = devices.minByOrNull { rank(kindOf(it.type)) } ?: return null
        val kind = kindOf(chosen.type)
        // productName falls back to the phone model when the port reports no name of its
        // own, which would key every nameless headset alike; the type name is more honest.
        val reported = runCatching { chosen.productName?.toString() }.getOrNull().orEmpty().trim()
        val name = reported.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: kind.fallbackName
        return OutputDevice(kind = kind, name = name, key = "${chosen.type}|$name")
    }

    private fun kindOf(type: Int): OutputKind = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
            OutputKind.Wired
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> OutputKind.Usb
        else -> OutputKind.Bluetooth
    }

    private fun rank(kind: OutputKind): Int = when (kind) {
        OutputKind.Bluetooth -> 0
        OutputKind.Usb -> 1
        OutputKind.Wired -> 2
        OutputKind.Default -> 3
    }

    private fun isConnected(manager: AudioManager): Boolean =
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in privateTypes() }

    /**
     * Output types that keep the sound off the speaker. Built per run because two of
     * them only exist on newer systems.
     */
    private fun privateTypes(): Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(AudioDeviceInfo.TYPE_HEARING_AID)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
