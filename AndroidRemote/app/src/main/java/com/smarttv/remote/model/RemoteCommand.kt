package com.smarttv.remote.model

import org.json.JSONObject

/**
 * Outgoing protocol messages (see docs/protocol.md). Serialized with the
 * platform's org.json — no extra dependency.
 */
sealed class RemoteCommand {
    object Hello : RemoteCommand()
    data class Auth(val token: String) : RemoteCommand()
    data class Pair(val pin: String) : RemoteCommand()
    data class Navigate(val direction: String) : RemoteCommand()
    object Select : RemoteCommand()
    object Back : RemoteCommand()
    object Home : RemoteCommand()
    data class Volume(val direction: String) : RemoteCommand()
    data class PointerMove(val dx: Float, val dy: Float) : RemoteCommand()
    object PointerClick : RemoteCommand()
    data class Scroll(val dy: Float) : RemoteCommand()
    data class Text(val value: String) : RemoteCommand()
    /** Special key: "return", "backspace", "space", "escape". */
    data class Key(val value: String) : RemoteCommand()

    fun toJson(): String {
        val json = JSONObject()
        when (this) {
            is Hello -> json.put("type", "hello")
            is Auth -> json.put("type", "auth").put("token", token)
            is Pair -> json.put("type", "pair").put("pin", pin)
            is Navigate -> json.put("type", "command").put("action", "navigate").put("direction", direction)
            is Select -> json.put("type", "command").put("action", "select")
            is Back -> json.put("type", "command").put("action", "back")
            is Home -> json.put("type", "command").put("action", "home")
            is Volume -> json.put("type", "command").put("action", "volume").put("direction", direction)
            is PointerMove -> json.put("type", "command").put("action", "pointer_move")
                .put("dx", dx.toDouble()).put("dy", dy.toDouble())
            is PointerClick -> json.put("type", "command").put("action", "pointer_click")
            is Scroll -> json.put("type", "command").put("action", "scroll").put("dy", dy.toDouble())
            is Text -> json.put("type", "command").put("action", "text").put("value", value)
            is Key -> json.put("type", "command").put("action", "key").put("value", value)
        }
        return json.toString()
    }
}

/** Incoming messages from the TV. */
sealed class TvMessage {
    data class PairResult(val accepted: Boolean, val deviceToken: String?) : TvMessage()
    data class AuthResult(val accepted: Boolean) : TvMessage()
    data class State(val screen: String, val serviceId: String?) : TvMessage()
    data class Error(val message: String) : TvMessage()
    object Unknown : TvMessage()

    companion object {
        fun parse(text: String): TvMessage {
            return try {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "pair_result" -> PairResult(
                        accepted = json.optString("status") == "accepted",
                        deviceToken = json.optString("deviceToken").ifEmpty { null }
                    )
                    "auth_result" -> AuthResult(json.optString("status") == "accepted")
                    "state" -> State(
                        screen = json.optString("screen"),
                        serviceId = json.optString("serviceId")
                            .ifEmpty { json.optString("focusedServiceId") }
                            .ifEmpty { null }
                    )
                    "error" -> Error(json.optString("message"))
                    else -> Unknown
                }
            } catch (_: Exception) {
                Unknown
            }
        }
    }
}
