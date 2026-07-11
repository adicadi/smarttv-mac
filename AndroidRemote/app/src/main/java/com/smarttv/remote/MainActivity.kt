package com.smarttv.remote

import android.app.Activity
import android.os.Bundle
import com.smarttv.remote.connection.RemoteSocketClient
import com.smarttv.remote.discovery.BonjourDiscovery
import com.smarttv.remote.model.RemoteCommand
import com.smarttv.remote.ui.RemotePadScreen

class MainActivity : Activity() {

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
                    pad.setStatus("Pairing…")
                    socketClient.submitPin(pin)
                }
            }
        )
        setContentView(pad.createView())
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
            pad.setStatus("Connecting to $serviceName…")
            socketClient.connect(host, port, socketListener)
        }

        override fun onLost() {
            pad.setStatus("SmartTV disappeared — searching…")
            pad.setControlsEnabled(false)
        }

        override fun onNothingFound() {
            pad.setStatus("No SmartTV found on this network.\nIs the Mac app running on the same Wi-Fi?")
        }
    }

    private val socketListener: RemoteSocketClient.Listener = object : RemoteSocketClient.Listener {
        override fun onConnected() {
            pad.setStatus("Connected — checking pairing…")
        }

        override fun onPairingRequired() {
            pad.setStatus("Enter the PIN shown on the TV")
            pad.showPinEntry(true)
            pad.setControlsEnabled(false)
        }

        override fun onPaired() {
            pad.setStatus("Paired ✓")
            pad.showPinEntry(false)
            pad.setControlsEnabled(true)
        }

        override fun onPairingRejected() {
            pad.setStatus("Wrong PIN — try again")
            pad.showPinEntry(true)
        }

        override fun onState(screen: String, serviceId: String?) {
            val focused: String = if (serviceId != null) " — focused: $serviceId" else ""
            val status: String = when (screen) {
                "grid" -> "TV: home$focused"
                "playing" -> "TV: playing ${serviceId ?: ""}"
                else -> "TV: $screen"
            }
            pad.setStatus(status)
        }

        override fun onDisconnected(reason: String) {
            pad.setStatus("Disconnected ($reason) — searching…")
            pad.setControlsEnabled(false)
            // Rediscover; the Mac may have restarted on a new port.
            discovery.start(discoveryListener)
        }
    }
}
