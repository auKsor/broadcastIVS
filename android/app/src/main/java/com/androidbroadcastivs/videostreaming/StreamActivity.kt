package com.androidbroadcastivs.videostreaming

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.ivs.broadcast.*
import com.androidbroadcastivs.R
import com.facebook.react.bridge.ReactApplicationContext
import kotlinx.android.synthetic.main.activity_video_streaming.*


private const val TAG = "AmazonIVS"

class StreamActivity : PermissionActivity() {
    private lateinit var url: String
    private lateinit var key: String

    private val context = this

    private var imagePreviewView: ImagePreviewView? = null
    private var cameraDevice: Device.Descriptor? = null
    private var microphoneDevice: Device.Descriptor? = null
    private var attachedCameraSize: Int = 0
    private var attachedMicrophoneSize: Int = 0
    private var isMuted: Boolean = false

    val screenCaptureEnabled get() = false
    private val configuration
        get() = if (screenCaptureEnabled)
            Presets.Configuration.GAMING_PORTRAIT
        else Presets.Configuration.STANDARD_PORTRAIT

    var session: BroadcastSession? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_streaming)
        //get the passed video info
        url = intent.getStringExtra("url").toString()
        key = intent.getStringExtra("key").toString()

    }


    override fun onStart() {
        super.onStart()
        askForPermissions { success ->
            if(success) {
                if(!url.isEmpty() && !key.isEmpty()) {
                    createSession {
                        startSession(url, key)
                        Toast.makeText(context, "is Running", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.release()
    }


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

    fun createSession(onReady: () -> Unit = {}) {
        session?.release()
        BroadcastSession(context, broadcastListener, configuration, Presets.Devices.FRONT_CAMERA(context)).apply {
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
                Toast.makeText(context, "Broadcast session is streaming", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
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
            previewView.addView(this)
            imagePreviewView = this
        }
    }
}
