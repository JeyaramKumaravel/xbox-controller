# Xbox Virtual Controller

A virtual Xbox controller system that turns your Android phone into a wireless game controller for PC.

## Features
- 📱 **Full Xbox Controller Layout** - All buttons, triggers, joysticks, and D-pad
- 📶 **Wi-Fi Connection** - Connect via QR code scan
- 🎮 **Multi-Player Support** - Up to 4 simultaneous controllers
- 📳 **Haptic Feedback** - Vibration on button presses
- 🖱️ **Touchpad Mode** - Use as a mouse/trackpad
- ⚙️ **Customizable** - Adjust button sizes, deadzone, haptic intensity

## Project Structure

```
xbox/
├── pc-server/          # Python server for Windows
│   ├── main.py         # GUI application with system tray
│   ├── server.py       # WebSocket server
│   ├── controller.py   # Virtual Xbox controller (vgamepad)
│   ├── session_manager.py  # Multi-player sessions
│   ├── qr_generator.py # QR code generation
│   └── requirements.txt
│
└── android-controller/ # Kotlin Android app
    └── app/
        └── src/main/
            ├── java/com/xboxcontroller/
            │   ├── MainActivity.kt      # Connection screen
            │   ├── ControllerActivity.kt # Main controller
            │   ├── ScanActivity.kt      # QR scanner
            │   ├── TouchpadActivity.kt  # Touchpad mode
            │   ├── controller/          # Custom UI views
            │   ├── network/             # WebSocket client
            │   ├── data/                # Data models
            │   └── settings/            # Settings screen
            └── res/                     # Layouts & resources
```

## Setup Instructions

### PC Server (Windows)

1. **Install ViGEmBus Driver** (required for virtual controller):
   - Download from: https://github.com/nefarius/ViGEmBus/releases
   - Run the installer

2. **Install Python dependencies**:
   ```bash
   cd pc-server
   pip install -r requirements.txt
   ```

3. **Run the server**:
   ```bash
   python main.py
   ```

4. A window will appear with a QR code and server status

### Android App

1. **Build the app**:
   - Open `android-controller` in Android Studio
   - Build and install on your Android device

2. **Connect**:
   - Ensure your phone and PC are on the same Wi-Fi network
   - Tap "Scan QR Code" and point at the PC screen
   - Or manually enter the server IP address

## Usage

- **Switch Modes**: Use the mode buttons in the center to switch between controller and touchpad modes
- **Settings**: Customize button sizes, haptic feedback, and joystick deadzone
- **Multi-Player**: Connect up to 4 Android devices for local multiplayer

## Requirements

### PC
- Windows 10/11
- Python 3.8+
- ViGEmBus driver

### Android
- Android 7.0+ (API 24)
- Camera (for QR scanning)
