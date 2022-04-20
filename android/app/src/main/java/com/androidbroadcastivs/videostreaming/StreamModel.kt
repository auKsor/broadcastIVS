package com.androidbroadcastivs.videostreaming

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.amazonaws.ivs.broadcast.*
import com.facebook.react.bridge.ReactApplicationContext

private const val TAG = "AmazonIVS"

class StreamModel(private val context: Context) : ViewModel() {
    private var cameraDevice: Device.Descriptor? = null
    private var microphoneDevice: Device.Descriptor? = null
    private var attachedCameraSize: Int = 0
    private var attachedMicrophoneSize: Int = 0
    private var isMuted: Boolean = false

    var session: BroadcastSession? = null
    var paused = false


    val screenCaptureEnabled get() = captureMode.value ?: false
    private val configuration
        get() = if (screenCaptureEnabled)
            Presets.Configuration.GAMING_PORTRAIT
        else Presets.Configuration.STANDARD_PORTRAIT

    val preview = MutableLiveData<ImagePreviewView>()
    val clearPreview = MutableLiveData<Boolean>()
    private val captureMode = MutableLiveData<Boolean>()

    private val broadcastListener by lazy {
        (object : BroadcastSession.Listener() {
            override fun onStateChanged(state: BroadcastSession.State) {
                    when (state) {
                        BroadcastSession.State.CONNECTED -> {
                            Log.d(TAG, "Connected state")
                        }
                        BroadcastSession.State.DISCONNECTED -> {
                            Log.d(TAG, "Disconnected state")
                        }
                        BroadcastSession.State.CONNECTING -> {
                            Log.d(TAG, "Connecting state")
                        }
                        BroadcastSession.State.ERROR -> {
                            Log.d(TAG, "Error state")
                        }
                        BroadcastSession.State.INVALID -> {
                            Log.d(TAG, "Invalid state")
                        }
                    }
            }

            override fun onError(error: BroadcastException) {
                Log.d(TAG, "Error is: ${error.detail} Error code: ${error.code} Error source: ${error.source}")
                if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && error.source == microphoneDevice?.urn) {
                    microphoneDevice?.let {
                        try {
                            session?.exchangeDevices(it, it) { microphone ->
                                Log.d(TAG, "Device with id ${microphoneDevice?.deviceId} reattached")
                                microphoneDevice = microphone.descriptor
                            }
                        } catch (e: BroadcastException) {
                            Log.d(TAG, "Camera exchange exception $e")
                        }
                    }
                } else if (error.error == ErrorType.ERROR_DEVICE_DISCONNECTED && microphoneDevice == null) {
                    Toast.makeText(context, "External device ${error.source} disconnected", Toast.LENGTH_SHORT).show()
                } else {
                    error.printStackTrace()
                }
            }

        })
    }

    /**
     * Create and start new session
     */
    fun createSession(onReady: () -> Unit = {}) {
        session?.release()
        BroadcastSession(context, broadcastListener, configuration, listOf(cameraDevice, microphoneDevice).toTypedArray()).apply {
            session = this
            awaitDeviceChanges {
                listAttachedDevices().run {
                    forEach { device ->
                        device?.let {
                            if (it.descriptor.type == Device.Descriptor.DeviceType.CAMERA) {
                                cameraDevice = it.descriptor
                                displayCameraOutput(it)
                            }
                            if (it.descriptor.type == Device.Descriptor.DeviceType.MICROPHONE) {
                                microphoneDevice = it.descriptor
                                // By default, audio devices start with a gain of 1, so we only
                                // need to change the gain on starting the session if we already know
                                // the device should be muted.
                                if (isMuted) {
                                    it as AudioDevice
                                    it.setGain(0f)
                                }
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Broadcast session ready: $isReady")
            if (isReady) {
                onReady()
            } else {
                Log.d(TAG, "Broadcast session not ready")
                Toast.makeText(context, "Broadcast session not ready", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startSession(endpoint: String, key: String) {
        try {
            session?.start(endpoint, key)
        } catch (e: BroadcastException) {
            e.printStackTrace()
        }
    }

    /**
     * Camera output display
     */
    private fun displayCameraOutput(device: Device) {
        device as ImageDevice
        Log.d(TAG, "Displaying camera output")
        device.previewView?.run {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            clearPreview.value = true
            preview.value = this
        }
    }


}