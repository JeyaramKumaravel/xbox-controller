"""
Xbox Virtual Controller - WebSocket Server
Handles WebSocket connections from Android controllers
"""

import asyncio
import websockets
import json
import logging
from typing import Optional, Callable, Any

from controller import ControllerManager
from session_manager import SessionManager

logger = logging.getLogger(__name__)


class ControllerServer:
    """WebSocket server for receiving controller input from mobile devices"""
    
    def __init__(self, host: str = "0.0.0.0", port: int = 8765):
        self.host = host
        self.port = port
        self.controller_manager = ControllerManager()
        self.session_manager = SessionManager(
            on_player_connected=self._on_player_connected,
            on_player_disconnected=self._on_player_disconnected
        )
        self._server: Optional[Any] = None
        self._running = False
        
        # Callbacks for GUI updates
        self.on_status_change: Optional[Callable[[str], None]] = None
        self.on_player_update: Optional[Callable[[int, bool], None]] = None
    
    def _on_player_connected(self, player_id: int):
        """Called when a player connects"""
        self.controller_manager.create_controller(player_id)
        if self.on_player_update:
            self.on_player_update(player_id, True)
    
    def _on_player_disconnected(self, player_id: int):
        """Called when a player disconnects"""
        self.controller_manager.remove_controller(player_id)
        if self.on_player_update:
            self.on_player_update(player_id, False)
    
    async def handle_client(self, websocket):
        """Handle a single client connection"""
        client_ip = websocket.remote_address[0] if websocket.remote_address else "unknown"
        logger.info(f"New connection from {client_ip}")
        
        # Assign player ID
        player_id = await self.session_manager.add_player(websocket)
        if player_id is None:
            # Server is full
            await websocket.send(json.dumps({
                "type": "error",
                "message": "Server is full (max 4 players)"
            }))
            await websocket.close()
            return
        
        # Send welcome message with player ID
        await websocket.send(json.dumps({
            "type": "connected",
            "playerId": player_id,
            "message": f"Connected as Player {player_id}"
        }))
        
        try:
            async for message in websocket:
                await self._process_message(player_id, message)
        except websockets.ConnectionClosed:
            logger.info(f"Player {player_id} connection closed")
        except Exception as e:
            logger.error(f"Error handling player {player_id}: {e}")
        finally:
            await self.session_manager.remove_player(websocket)
    
    async def _process_message(self, player_id: int, message: str):
        """Process an incoming message from a client"""
        try:
            data = json.loads(message)
            msg_type = data.get("type", "")
            
            if msg_type == "input":
                # Controller input
                controller = self.controller_manager.get_controller(player_id)
                if controller:
                    buttons = data.get("buttons", {})
                    triggers = data.get("triggers", {})
                    axes = data.get("axes", {})
                    controller.update_state(buttons, triggers, axes)
                    self.session_manager.update_activity(player_id)
            
            elif msg_type == "mouse":
                # Mouse input (trackpad mode)
                from mouse_keyboard import get_mouse_keyboard_controller
                mk = get_mouse_keyboard_controller()
                
                action = data.get("action", "")
                if action == "move":
                    dx = data.get("dx", 0)
                    dy = data.get("dy", 0)
                    mk.move_mouse(dx, dy)
                elif action == "click":
                    button = data.get("button", "left")
                    mk.click(button)
                elif action == "press":
                    button = data.get("button", "left")
                    mk.press_mouse(button)
                elif action == "release":
                    button = data.get("button", "left")
                    mk.release_mouse(button)
                elif action == "scroll":
                    dx = data.get("dx", 0)
                    dy = data.get("dy", 0)
                    mk.scroll(dx, dy)
                
                self.session_manager.update_activity(player_id)
            
            elif msg_type == "keyboard":
                # Keyboard input
                from mouse_keyboard import get_mouse_keyboard_controller
                mk = get_mouse_keyboard_controller()
                
                action = data.get("action", "")
                if action == "type":
                    text = data.get("text", "")
                    mk.type_text(text)
                elif action == "key":
                    key = data.get("key", "")
                    mk.press_key(key)
                elif action == "combo":
                    keys = data.get("keys", [])
                    if keys:
                        mk.key_combination(keys)
                
                self.session_manager.update_activity(player_id)
            
            elif msg_type == "ping":
                # Keepalive ping
                session = self.session_manager.get_session(player_id)
                if session:
                    await session.websocket.send(json.dumps({"type": "pong"}))
            
            elif msg_type == "disconnect":
                # Client requested disconnect
                session = self.session_manager.get_session(player_id)
                if session:
                    await session.websocket.close()
                    
        except json.JSONDecodeError:
            logger.warning(f"Invalid JSON from player {player_id}")
        except Exception as e:
            logger.error(f"Error processing message from player {player_id}: {e}")
    
    async def start(self):
        """Start the WebSocket server"""
        self._running = True
        
        try:
            self._server = await websockets.serve(
                self.handle_client,
                self.host,
                self.port,
                ping_interval=20,
                ping_timeout=10
            )
            
            logger.info(f"Server started on {self.host}:{self.port}")
            if self.on_status_change:
                self.on_status_change("running")
            
            await self._server.wait_closed()
            
        except Exception as e:
            logger.error(f"Server error: {e}")
            if self.on_status_change:
                self.on_status_change("error")
            raise
    
    async def stop(self):
        """Stop the WebSocket server"""
        self._running = False
        
        if self._server:
            self._server.close()
            await self._server.wait_closed()
        
        # Disconnect all controllers
        self.controller_manager.disconnect_all()
        self.session_manager.clear_all()
        
        logger.info("Server stopped")
        if self.on_status_change:
            self.on_status_change("stopped")
    
    @property
    def is_running(self) -> bool:
        return self._running
    
    def get_player_count(self) -> int:
        return self.session_manager.get_player_count()
