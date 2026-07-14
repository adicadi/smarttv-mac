package com.adicadi.smarttv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import com.adicadi.smarttv.connection.RemoteSocketClient
import com.adicadi.smarttv.discovery.BonjourDiscovery
import com.adicadi.smarttv.model.RemoteCommand
import com.adicadi.smarttv.ui.RemotePadScreen

class MainActivity : Activity() {

    companion object {
        private const val VOICE_REQUEST_CODE = 1001
    }

    private lateinit var discovery: BonjourDiscovery
    private lateinit var socketClient: RemoteSocketClient
    private lateinit var pad: RemotePadScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        discovery = BonjourDiscovery(this)
        socketClient = RemoteSocketClient(this)
        pad = RemotePadScreen(
            this,
            onCommand = { command -> socketClient.send(command) },
            onPinSubmit = { pin ->
                if (pin.isNotEmpty()) {
                    socketClient.submitPin(pin)
                }
            },
            onVoiceRequest = { startVoiceCapture() }
        )
        setContentView(pad.createView())
    }

    /** Speech-to-text via the system recognizer; result is sent as typed text. */
    private fun startVoiceCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to type on the TV")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No speech recognizer on this device", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val spoken = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrEmpty()) {
                socketClient.send(RemoteCommand.VoiceText(spoken))
                pad.setStateLine("Sent: “$spoken”")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pad.setStatus("Searching for SmartTV…")
        discovery.start(discoveryListener)
    }

    override fun onStop() {
        super.onStop()
        discovery.stop()
        socketClient.disconnect()
    }

    private val discoveryListener: BonjourDiscovery.Listener = object : BonjourDiscovery.Listener {
        override fun onFound(host: String, port: Int, serviceName: String) {
            // mDNS often re-announces; don't tear down a healthy connection.
            if (socketClient.isConnected) return
            pad.setStatus("Connecting to $serviceName…")
            socketClient.connect(host, port, socketListener)
        }

        override fun onLost() {
            // mDNS records flap regularly; only the socket state matters.
            if (socketClient.isConnected) return
            pad.setStatus("SmartTV disappeared — searching…")
            pad.setControlsEnabled(false)
        }

        override fun onNothingFound() {
            if (socketClient.isConnected) return
            pad.setStatus("No SmartTV found on this network.\nIs the Mac app running on the same Wi-Fi?")
        }
    }

    private val socketListener: RemoteSocketClient.Listener = object : RemoteSocketClient.Listener {
        override fun onConnected() {
            pad.setStatus("Connected — checking pairing…")
        }

        override fun onPairingRequired() {
            pad.showPinEntry(true)
            pad.setControlsEnabled(false)
        }

        override fun onPaired() {
            pad.showPaired()
            pad.setControlsEnabled(true)
        }

        override fun onPairingRejected() {
            pad.showPinError()
        }

        override fun onState(screen: String, serviceId: String?) {
            val focused: String = if (serviceId != null) " — focused: $serviceId" else ""
            val line: String = when (screen) {
                "grid" -> "TV: grid$focused"
                "playing" -> "TV: playing ${serviceId ?: ""}"
                else -> "TV: $screen"
            }
            pad.setStateLine(line)
        }

        override fun onDisconnected(reason: String) {
            pad.setStatus("Disconnected ($reason) — searching…")
            pad.setControlsEnabled(false)
            // Rediscover after a short backoff (the Mac may have restarted on
            // a new port); immediate retries cause visible flicker loops.
            window.decorView.postDelayed({
                if (!socketClient.isConnected) discovery.start(discoveryListener)
            }, 2000)
        }
    }
}
