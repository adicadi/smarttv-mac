package com.smarttv.remote.connection

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.smarttv.remote.model.RemoteCommand
import com.smarttv.remote.model.TvMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket connection to the Mac. Handles the auth/pairing handshake:
 * a stored device token is tried first (silent reconnect); if the TV rejects
 * it — or none exists — the UI is asked for the PIN shown on the TV screen.
 * All listener callbacks are delivered on the main thread.
 */
class RemoteSocketClient(context: Context) {

    interface Listener {
        fun onConnected()
        /** TV is showing a PIN; prompt the user and call [submitPin]. */
        fun onPairingRequired()
        fun onPaired()
        fun onPairingRejected()
        fun onState(screen: String, serviceId: String?)
        fun onDisconnected(reason: String)
    }

    private val prefs = context.getSharedPreferences("smarttv_remote", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var listener: Listener? = null
    @Volatile private var paired = false
    @Volatile private var connected = false

    val isPaired: Boolean get() = paired
    /** True while the WebSocket is open — used to ignore mDNS flapping. */
    val isConnected: Boolean get() = connected

    fun connect(host: String, port: Int, listener: Listener) {
        disconnect()
        this.listener = listener
        paired = false
        val request = Request.Builder().url("ws://$host:$port/").build()
        webSocket = client.newWebSocket(request, socketListener)
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
        paired = false
        connected = false
    }

    fun submitPin(pin: String) {
        send(RemoteCommand.Pair(pin))
    }

    fun send(command: RemoteCommand) {
        webSocket?.send(command.toJson())
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            post { listener?.onConnected() }
            val token = prefs.getString(KEY_TOKEN, null)
            if (token != null) {
                webSocket.send(RemoteCommand.Auth(token).toJson())
            } else {
                webSocket.send(RemoteCommand.Hello.toJson())
                post { listener?.onPairingRequired() }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val message = TvMessage.parse(text)) {
                is TvMessage.AuthResult -> {
                    if (message.accepted) {
                        paired = true
                        post { listener?.onPaired() }
                    } else {
                        // Stale token: fall back to PIN pairing.
                        prefs.edit().remove(KEY_TOKEN).apply()
                        webSocket.send(RemoteCommand.Hello.toJson())
                        post { listener?.onPairingRequired() }
                    }
                }
                is TvMessage.PairResult -> {
                    if (message.accepted) {
                        paired = true
                        message.deviceToken?.let {
                            prefs.edit().putString(KEY_TOKEN, it).apply()
                        }
                        post { listener?.onPaired() }
                    } else {
                        post { listener?.onPairingRejected() }
                    }
                }
                is TvMessage.State -> post { listener?.onState(message.screen, message.serviceId) }
                is TvMessage.Error -> Log.w(TAG, "TV error: ${message.message}")
                TvMessage.Unknown -> Log.w(TAG, "unknown message: $text")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            paired = false
            connected = false
            post { listener?.onDisconnected(t.message ?: "connection failed") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            paired = false
            connected = false
            post { listener?.onDisconnected(reason.ifEmpty { "closed" }) }
        }
    }

    private fun post(block: () -> Unit) = mainHandler.post(block)

    companion object {
        private const val TAG = "RemoteSocketClient"
        private const val KEY_TOKEN = "device_token"
    }
}
