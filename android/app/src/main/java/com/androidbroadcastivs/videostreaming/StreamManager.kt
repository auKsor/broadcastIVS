package com.androidbroadcastivs.videostreaming

import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.amazonaws.ivs.broadcast.*

private const val TAG = "IVSAmazon"
class StreamManager(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private var session: BroadcastSession? = null
    override fun getName(): String {
        return "BroadcastStream"
    }

    @ReactMethod
    fun startStream(url: String, key: String) {
        val intent = Intent(reactApplicationContext, StreamActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("url", url)
        intent.putExtra("key", key)
        reactApplicationContext.startActivity(intent)
    }

    @ReactMethod
    fun createSessionAndStart(url: String, key: String) {
        createSession {
            startSession(url, key)
        }
    }


    fun createSession(onReady: () -> Unit = {}) {
        BroadcastSession(reactApplicationContext, broadcastListener, Presets.Configuration.STANDARD_PORTRAIT,
            Presets.Devices.FRONT_CAMERA(reactApplicationContext)).apply {
            session = this
            if(isReady) {
                onReady()
                Toast.makeText(reactApplicationContext, "Session is created", Toast.LENGTH_LONG).show();
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

            }

        })
    }
}