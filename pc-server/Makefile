# Xbox Controller Server - Build Makefile

.PHONY: build clean run install dev

# Build the executable
build:
	uv run pyinstaller --onefile --windowed \
		--name "XboxControllerServer" \
		--icon "icon.ico" \
		--add-binary ".venv\Lib\site-packages\vgamepad\win\vigem\client\x64\ViGEmClient.dll;vgamepad\win\vigem\client\x64" \
		--add-binary ".venv\Lib\site-packages\vgamepad\win\vigem\client\x86\ViGEmClient.dll;vgamepad\win\vigem\client\x86" \
		--hidden-import vgamepad \
		--hidden-import pystray \
		--hidden-import pynput \
		main.py

# Clean build artifacts
clean:
	rmdir /s /q build 2>nul || echo "build folder not found"
	rmdir /s /q dist 2>nul || echo "dist folder not found"
	del /q *.spec 2>nul || echo "spec file not found"

# Run the application (dev mode)
run:
	uv run python main.py

# Install dependencies
install:
	uv sync

# Install dev dependencies
dev:
	uv sync --dev
