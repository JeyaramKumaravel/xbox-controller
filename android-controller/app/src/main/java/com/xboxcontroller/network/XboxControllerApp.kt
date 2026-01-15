package com.xboxcontroller.network

import android.app.Application

/**
 * Application class that holds the shared WebSocket connection
 * This ensures only ONE connection per device, regardless of which activity is active
 */
class XboxControllerApp : Application() {
    
    companion object {
        // Singleton WebSocket client shared across all activities
        val webSocketClient = WebSocketClient()
        
        // Connection info saved after QR scan or manual entry
        var serverHost: String = ""
        var serverPort: Int = 8765
        var isConnected: Boolean = false
        
        fun connect(host: String, port: Int) {
            if (isConnected && serverHost == host && serverPort == port) {
                // Already connected to this server
                return
            }
            
            // Disconnect existing connection if any
            if (isConnected) {
                webSocketClient.disconnect()
            }
            
            serverHost = host
            serverPort = port
            webSocketClient.connect(host, port)
            isConnected = true
        }
        
        fun disconnect() {
            webSocketClient.disconnect()
            isConnected = false
        }
    }
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        disconnect()
    }
}
