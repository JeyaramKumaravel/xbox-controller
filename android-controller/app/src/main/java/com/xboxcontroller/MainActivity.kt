package com.xboxcontroller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xboxcontroller.data.SettingsRepository
import com.xboxcontroller.databinding.ActivityMainBinding
import com.xboxcontroller.network.WebSocketClient
import com.xboxcontroller.network.XboxControllerApp
import com.xboxcontroller.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main entry activity for connection setup
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    
    // Use the shared WebSocket client
    private val webSocketClient get() = XboxControllerApp.webSocketClient

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startQRScanner()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)

        setupUI()
        observeConnectionState()
        loadLastServer()
    }

    private fun setupUI() {
        binding.scanButton.setOnClickListener {
            checkCameraPermissionAndScan()
        }

        binding.connectButton.setOnClickListener {
            val host = binding.hostInput.text?.toString()?.trim() ?: ""
            if (host.isNotEmpty()) {
                connectToServer(host, 8765)
            } else {
                Toast.makeText(this, "Please enter server IP", Toast.LENGTH_SHORT).show()
            }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            webSocketClient.connectionState.collectLatest { state ->
                when (state) {
                    is WebSocketClient.ConnectionState.Disconnected -> {
                        binding.statusText.text = getString(R.string.disconnected)
                        binding.statusText.setTextColor(getColor(R.color.text_secondary))
                        binding.playerIdText.visibility = android.view.View.GONE
                        binding.scanButton.isEnabled = true
                        binding.connectButton.isEnabled = true
                    }
                    is WebSocketClient.ConnectionState.Connecting -> {
                        binding.statusText.text = getString(R.string.connecting)
                        binding.statusText.setTextColor(getColor(R.color.button_y))
                        binding.scanButton.isEnabled = false
                        binding.connectButton.isEnabled = false
                    }
                    is WebSocketClient.ConnectionState.Reconnecting -> {
                        binding.statusText.text = "Reconnecting..."
                        binding.statusText.setTextColor(getColor(R.color.button_y))
                        binding.scanButton.isEnabled = false
                        binding.connectButton.isEnabled = false
                    }
                    is WebSocketClient.ConnectionState.Connected -> {
                        binding.statusText.text = getString(R.string.connected)
                        binding.statusText.setTextColor(getColor(R.color.accent))
                        binding.playerIdText.text = "Player ${state.playerId}"
                        binding.playerIdText.visibility = android.view.View.VISIBLE

                        // Navigate to controller
                        startControllerActivity(state.playerId)
                    }
                    is WebSocketClient.ConnectionState.Error -> {
                        binding.statusText.text = state.message
                        binding.statusText.setTextColor(getColor(R.color.button_b))
                        binding.scanButton.isEnabled = true
                        binding.connectButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun loadLastServer() {
        lifecycleScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                if (settings.lastServerHost.isNotEmpty()) {
                    binding.hostInput.setText(settings.lastServerHost)
                }
            }
        }
    }

    private fun checkCameraPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startQRScanner()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startQRScanner() {
        val intent = Intent(this, ScanActivity::class.java)
        startActivityForResult(intent, REQUEST_QR_SCAN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_QR_SCAN && resultCode == RESULT_OK) {
            val host = data?.getStringExtra("host") ?: return
            val port = data.getIntExtra("port", 8765)
            binding.hostInput.setText(host)
            connectToServer(host, port)
        }
    }

    private fun connectToServer(host: String, port: Int) {
        lifecycleScope.launch {
            settingsRepository.saveLastServer(host, port)
        }
        // Use the shared connection manager
        XboxControllerApp.connect(host, port)
    }

    private fun startControllerActivity(playerId: Int) {
        val intent = Intent(this, ControllerActivity::class.java).apply {
            putExtra("player_id", playerId)
        }
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_QR_SCAN = 1001
    }
}
