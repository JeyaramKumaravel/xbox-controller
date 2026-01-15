package com.xboxcontroller.network

import android.util.Log
import com.google.gson.Gson
import com.xboxcontroller.data.ControllerInput
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for connecting to the PC server
 */
class WebSocketClient {
    
    companion object {
        private const val TAG = "WebSocketClient"
        private const val PING_INTERVAL = 15000L // 15 seconds
    }
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    
    private val _playerId = MutableStateFlow<Int?>(null)
    val playerId: StateFlow<Int?> = _playerId
    
    private var scope: CoroutineScope? = null
    private val inputQueue = Channel<ControllerInput>(Channel.CONFLATED)
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val playerId: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    /**
     * Connect to the server
     */
    fun connect(host: String, port: Int) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            Log.w(TAG, "Already connected or connecting")
            return
        }
        
        _connectionState.value = ConnectionState.Connecting
        
        val url = "ws://$host:$port"
        Log.i(TAG, "Connecting to $url")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                startInputSender()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                cleanup()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $reason")
                _connectionState.value = ConnectionState.Disconnected
                cleanup()
            }
        })
    }
    
    /**
     * Disconnect from the server
     */
    fun disconnect() {
        webSocket?.send(gson.toJson(mapOf("type" to "disconnect")))
        webSocket?.close(1000, "User disconnected")
        cleanup()
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Send controller input to the server
     */
    fun sendInput(input: ControllerInput) {
        scope?.launch {
            inputQueue.send(input)
        }
    }
    
    /**
     * Send raw JSON message to the server (for mouse/keyboard)
     */
    fun sendRaw(json: String) {
        webSocket?.send(json)
    }
    
    private fun startInputSender() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope?.launch {
            for (input in inputQueue) {
                val message = buildInputMessage(input)
                webSocket?.send(message)
            }
        }
    }
    
    private fun buildInputMessage(input: ControllerInput): String {
        val message = mapOf(
            "type" to "input",
            "playerId" to (_playerId.value ?: 1),
            "buttons" to mapOf(
                "a" to input.buttonA,
                "b" to input.buttonB,
                "x" to input.buttonX,
                "y" to input.buttonY,
                "lb" to input.buttonLB,
                "rb" to input.buttonRB,
                "start" to input.buttonStart,
                "back" to input.buttonBack,
                "guide" to input.buttonGuide,
                "l3" to input.buttonL3,
                "r3" to input.buttonR3,
                "dpad_up" to input.dpadUp,
                "dpad_down" to input.dpadDown,
                "dpad_left" to input.dpadLeft,
                "dpad_right" to input.dpadRight
            ),
            "triggers" to mapOf(
                "lt" to input.triggerLT,
                "rt" to input.triggerRT
            ),
            "axes" to mapOf(
                "left_x" to input.leftStickX,
                "left_y" to input.leftStickY,
                "right_x" to input.rightStickX,
                "right_y" to input.rightStickY
            )
        )
        return gson.toJson(message)
    }
    
    private fun handleMessage(text: String) {
        try {
            val data = gson.fromJson(text, Map::class.java)
            when (data["type"]) {
                "connected" -> {
                    val id = (data["playerId"] as? Double)?.toInt() ?: 1
                    _playerId.value = id
                    _connectionState.value = ConnectionState.Connected(id)
                    Log.i(TAG, "Connected as Player $id")
                }
                "error" -> {
                    val message = data["message"] as? String ?: "Unknown error"
                    _connectionState.value = ConnectionState.Error(message)
                }
                "pong" -> {
                    // Keepalive response
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
    
    private fun cleanup() {
        scope?.cancel()
        scope = null
        _playerId.value = null
    }
}
