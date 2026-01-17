# 🎮 Xbox Virtual Controller

Turn your Android phone into a virtual Xbox controller for your PC! This project enables wireless gamepad, mouse, and keyboard control over your local network via WebSocket.

![Python](https://img.shields.io/badge/Python-3.14+-blue.svg)
![Android](https://img.shields.io/badge/Android-Kotlin-green.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

## ✨ Features

- **Virtual Xbox Controller** - Full gamepad emulation with all buttons, triggers, and analog sticks
- **Multi-Player Support** - Connect up to 8 controllers simultaneously
- **Mouse & Keyboard Mode** - Use your phone as a trackpad and keyboard
- **QR Code Connection** - Scan to connect instantly
- **System Tray** - Minimize to tray for background operation
- **Low Latency** - WebSocket-based communication for responsive controls

## 📁 Project Structure

```
xbox-controller/
├── android-controller/     # Android app (Kotlin/Jetpack Compose)
│   └── app/               
└── pc-server/              # Windows server (Python)
    ├── main.py             # GUI application with system tray
    ├── server.py           # WebSocket server
    ├── controller.py       # Virtual gamepad (vgamepad)
    ├── mouse_keyboard.py   # Mouse & keyboard control
    ├── qr_generator.py     # QR code generation
    └── session_manager.py  # Player session handling
```

## 🚀 Getting Started

### Prerequisites

- **PC**: Windows 10/11, Python 3.14+
- **Phone**: Android device on the same WiFi network
- **Driver**: [ViGEmBus Driver](https://github.com/ViGEm/ViGEmBus/releases) (required for virtual controller)

### PC Server Setup

1. **Install ViGEmBus Driver**
   Download and install from [ViGEmBus Releases](https://github.com/ViGEm/ViGEmBus/releases)

2. **Install Chocolatey then add uv and make**
   Open PowerShell as Administrator and run:
   ```powershell
   Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
   choco install uv make
   ```

2. **Install Dependencies**
   
   ```bash
   cd pc-server
   uv sync
   ```

3. **Run the Server**
   
   ```bash
   make run
   ```

   A window will appear with a QR code to scan.

### Android App Setup

1. **Build the App**
   
   ```bash
   cd android-controller
   ./gradlew assembleDebug
   ```

2. **Install on Phone**
   
   Install the APK from `app/build/outputs/apk/debug/`

3. **Connect**
   
   - Open the app on your phone
   - Scan the QR code displayed on your PC
   - Start playing!

## 🎯 Usage

### Controller Mode
Use all standard Xbox controller inputs:
- Analog sticks (left/right)
- D-Pad
- A, B, X, Y buttons
- Bumpers (LB/RB)
- Triggers (LT/RT)
- Start, Back, Guide buttons

### Mouse Mode
- Swipe to move cursor
- Tap for left click
- Two-finger tap for right click
- Pinch to scroll

### Keyboard Mode
- Full QWERTY keyboard
- Special keys and shortcuts

## 🔧 Configuration

The server runs on port **8765** by default. Both devices must be on the same local network.

## 📦 Dependencies

### PC Server
| Package | Purpose |
|---------|---------|
| vgamepad | Virtual Xbox controller emulation |
| websockets | Real-time communication |
| qrcode | QR code generation |
| Pillow | Image processing |
| pystray | System tray support |
| netifaces | Network interface detection |
| pynput | Mouse/keyboard control |

### Android App
- Kotlin
- Jetpack Compose
- WebSocket client

## 🛠️ Development

### Building Standalone Executable

```bash
cd pc-server
make build
```

## 📝 License

This project is licensed under the MIT License.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

Made with ❤️ for gamers who want to use their phone as a controller
