"""
Xbox Virtual Controller - Main Application
GUI with system tray support for the controller server
"""

import tkinter as tk
from tkinter import ttk, messagebox
from PIL import Image, ImageTk
import threading
import asyncio
import logging
import sys
import os

# Conditionally import pystray (Windows system tray)
try:
    import pystray
    from pystray import MenuItem as item
    TRAY_AVAILABLE = True
except ImportError:
    TRAY_AVAILABLE = False

from server import ControllerServer
from qr_generator import QRCodeManager, get_local_ip

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class XboxControllerServerApp:
    """Main application with GUI and system tray support"""
    
    def __init__(self):
        self.port = 8765
        self.server = ControllerServer(port=self.port)
        self.qr_manager = QRCodeManager(port=self.port)
        
        self.root: tk.Tk = None
        self.server_thread: threading.Thread = None
        self.loop: asyncio.AbstractEventLoop = None
        self.tray_icon = None
        
        # Player status labels
        self.player_labels = {}
        self.status_label: tk.Label = None
        self.qr_label: tk.Label = None
        self.ip_label: tk.Label = None
        
        # Setup callbacks
        self.server.on_status_change = self._on_server_status_change
        self.server.on_player_update = self._on_player_update
    
    def create_gui(self):
        """Create the main GUI window"""
        self.root = tk.Tk()
        self.root.title("Xbox Virtual Controller Server")
        self.root.geometry("425x725")
        self.root.resizable(False, False)
        self.root.configure(bg='#1a1a2e')
        
        # Handle window close
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        
        # Style configuration
        style = ttk.Style()
        style.theme_use('clam')
        style.configure('TFrame', background='#1a1a2e')
        style.configure('TLabel', background='#1a1a2e', foreground='#eee')
        style.configure('Title.TLabel', font=('Segoe UI', 18, 'bold'), foreground='#00ff88')
        style.configure('Status.TLabel', font=('Segoe UI', 12))
        style.configure('Player.TLabel', font=('Segoe UI', 11), padding=5)
        style.configure('Connected.TLabel', foreground='#00ff88')
        style.configure('Disconnected.TLabel', foreground='#666')
        
        # Main container
        main_frame = ttk.Frame(self.root, padding=20)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Title
        title_label = ttk.Label(main_frame, text="🎮 Xbox Controller Server", 
                               style='Title.TLabel')
        title_label.pack(pady=(0, 20))
        
        # Status
        status_frame = ttk.Frame(main_frame)
        status_frame.pack(fill=tk.X, pady=10)
        
        ttk.Label(status_frame, text="Status:", style='Status.TLabel').pack(side=tk.LEFT)
        self.status_label = ttk.Label(status_frame, text="Starting...", 
                                      style='Status.TLabel', foreground='#ffaa00')
        self.status_label.pack(side=tk.LEFT, padx=10)
        
        # IP Address
        ip_frame = ttk.Frame(main_frame)
        ip_frame.pack(fill=tk.X, pady=5)
        
        ttk.Label(ip_frame, text="Server:", style='Status.TLabel').pack(side=tk.LEFT)
        ip = get_local_ip()
        self.ip_label = ttk.Label(ip_frame, text=f"ws://{ip}:{self.port}", 
                                  style='Status.TLabel', foreground='#00aaff')
        self.ip_label.pack(side=tk.LEFT, padx=10)
        
        # QR Code section
        qr_frame = ttk.Frame(main_frame)
        qr_frame.pack(pady=20)
        
        ttk.Label(qr_frame, text="Scan to Connect:", style='Status.TLabel').pack()
        
        # QR Code image
        self.qr_label = ttk.Label(qr_frame)
        self.qr_label.pack(pady=10)
        self._update_qr_code()
        
        # Players section
        players_frame = ttk.Frame(main_frame)
        players_frame.pack(fill=tk.X, pady=20)
        
        ttk.Label(players_frame, text="Connected Players:", 
                 style='Status.TLabel').pack(anchor=tk.W)
        
        # Player slots (1-8)
        for i in range(1, 9):
            player_frame = ttk.Frame(players_frame)
            player_frame.pack(fill=tk.X, pady=3)
            
            self.player_labels[i] = ttk.Label(
                player_frame, 
                text=f"  ○ Player {i}: Not Connected",
                style='Disconnected.TLabel'
            )
            self.player_labels[i].pack(anchor=tk.W)
        
        # Buttons
        button_frame = ttk.Frame(main_frame)
        button_frame.pack(fill=tk.X, pady=20)
        
        # Minimize to tray button
        if TRAY_AVAILABLE:
            minimize_btn = tk.Button(
                button_frame, 
                text="Minimize to Tray",
                command=self._minimize_to_tray,
                bg='#16213e', fg='white',
                font=('Segoe UI', 10),
                relief=tk.FLAT,
                padx=15, pady=8
            )
            minimize_btn.pack(side=tk.LEFT, padx=5)
        
        # Refresh QR button
        refresh_btn = tk.Button(
            button_frame,
            text="Refresh QR",
            command=self._update_qr_code,
            bg='#16213e', fg='white',
            font=('Segoe UI', 10),
            relief=tk.FLAT,
            padx=15, pady=8
        )
        refresh_btn.pack(side=tk.LEFT, padx=5)
        
        # Stop button
        stop_btn = tk.Button(
            button_frame,
            text="Stop Server",
            command=self._stop_and_exit,
            bg='#e94560', fg='white',
            font=('Segoe UI', 10),
            relief=tk.FLAT,
            padx=15, pady=8
        )
        stop_btn.pack(side=tk.RIGHT, padx=5)
    
    def _update_qr_code(self):
        """Update the QR code display"""
        try:
            qr_image, ip = self.qr_manager.get_qr_image(force_refresh=True)
            # Convert to PhotoImage
            photo = ImageTk.PhotoImage(qr_image.resize((200, 200)))
            self.qr_label.configure(image=photo)
            self.qr_label.image = photo  # Keep reference
            
            # Update IP label
            if self.ip_label:
                self.ip_label.configure(text=f"ws://{ip}:{self.port}")
        except Exception as e:
            logger.error(f"Failed to update QR code: {e}")
    
    def _on_server_status_change(self, status: str):
        """Handle server status change"""
        if self.root:
            self.root.after(0, lambda: self._update_status(status))
    
    def _update_status(self, status: str):
        """Update status label (must be called from main thread)"""
        if status == "running":
            self.status_label.configure(text="Running ✓", foreground='#00ff88')
        elif status == "stopped":
            self.status_label.configure(text="Stopped", foreground='#888')
        elif status == "error":
            self.status_label.configure(text="Error!", foreground='#e94560')
        else:
            self.status_label.configure(text=status, foreground='#ffaa00')
    
    def _on_player_update(self, player_id: int, connected: bool):
        """Handle player connection/disconnection"""
        if self.root:
            self.root.after(0, lambda: self._update_player_status(player_id, connected))
    
    def _update_player_status(self, player_id: int, connected: bool):
        """Update player status label (must be called from main thread)"""
        if player_id in self.player_labels:
            if connected:
                self.player_labels[player_id].configure(
                    text=f"  ● Player {player_id}: Connected",
                    foreground='#00ff88'
                )
            else:
                self.player_labels[player_id].configure(
                    text=f"  ○ Player {player_id}: Not Connected",
                    foreground='#666'
                )
    
    def _start_server_thread(self):
        """Start the server in a background thread"""
        def run_server():
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            try:
                self.loop.run_until_complete(self.server.start())
            except Exception as e:
                logger.error(f"Server thread error: {e}")
        
        self.server_thread = threading.Thread(target=run_server, daemon=True)
        self.server_thread.start()
    
    def _stop_server(self):
        """Stop the server"""
        if self.loop and self.server.is_running:
            asyncio.run_coroutine_threadsafe(self.server.stop(), self.loop)
    
    def _minimize_to_tray(self):
        """Minimize application to system tray"""
        if not TRAY_AVAILABLE:
            return
        
        self.root.withdraw()
        
        # Create tray icon
        def create_image():
            # Create a simple gamepad icon
            size = 64
            img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
            from PIL import ImageDraw
            draw = ImageDraw.Draw(img)
            # Draw a simple controller shape
            draw.rounded_rectangle([8, 16, 56, 48], radius=8, fill='#00ff88')
            draw.ellipse([12, 24, 28, 40], fill='#1a1a2e')  # Left stick
            draw.ellipse([36, 24, 52, 40], fill='#1a1a2e')  # Right stick
            return img
        
        menu = (
            item('Show', self._show_from_tray),
            item('Exit', self._stop_and_exit)
        )
        
        self.tray_icon = pystray.Icon(
            "xbox_controller",
            create_image(),
            "Xbox Controller Server",
            menu
        )
        
        # Run tray icon in background thread
        threading.Thread(target=self.tray_icon.run, daemon=True).start()
    
    def _show_from_tray(self):
        """Show window from tray"""
        if self.tray_icon:
            self.tray_icon.stop()
            self.tray_icon = None
        self.root.after(0, self.root.deiconify)
    
    def _on_close(self):
        """Handle window close button"""
        if TRAY_AVAILABLE:
            result = messagebox.askyesnocancel(
                "Minimize to Tray?",
                "Do you want to minimize to system tray?\n\n"
                "Yes = Minimize to tray\n"
                "No = Exit completely\n"
                "Cancel = Stay open"
            )
            if result is True:
                self._minimize_to_tray()
            elif result is False:
                self._stop_and_exit()
            # Cancel does nothing
        else:
            self._stop_and_exit()
    
    def _stop_and_exit(self):
        """Stop server and exit application"""
        self._stop_server()
        if self.tray_icon:
            self.tray_icon.stop()
        if self.root:
            self.root.quit()
            self.root.destroy()
    
    def run(self):
        """Run the application"""
        self.create_gui()
        self._start_server_thread()
        self.root.mainloop()


def main():
    """Main entry point"""
    try:
        app = XboxControllerServerApp()
        app.run()
    except KeyboardInterrupt:
        logger.info("Interrupted by user")
    except Exception as e:
        logger.error(f"Application error: {e}")
        raise


if __name__ == "__main__":
    main()
