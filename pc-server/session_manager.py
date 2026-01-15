"""
Xbox Virtual Controller - Session Manager
Handles player sessions and maps WebSocket connections to controllers
"""

import logging
from typing import Dict, Optional, Callable, Any
from dataclasses import dataclass, field
from datetime import datetime
import asyncio

logger = logging.getLogger(__name__)


@dataclass
class PlayerSession:
    """Represents a connected player session"""
    player_id: int
    websocket: Any  # WebSocket connection
    connected_at: datetime = field(default_factory=datetime.now)
    last_input_at: datetime = field(default_factory=datetime.now)
    
    def update_activity(self):
        self.last_input_at = datetime.now()


class SessionManager:
    """Manages player sessions and maps them to controllers"""
    
    MAX_PLAYERS = 4
    
    def __init__(self, on_player_connected: Optional[Callable] = None,
                 on_player_disconnected: Optional[Callable] = None):
        self.sessions: Dict[int, PlayerSession] = {}
        self.websocket_to_player: Dict[Any, int] = {}
        self.on_player_connected = on_player_connected
        self.on_player_disconnected = on_player_disconnected
        self._lock = asyncio.Lock()
    
    async def add_player(self, websocket: Any) -> Optional[int]:
        """
        Register a new player and assign a player ID
        Returns the assigned player ID or None if server is full
        """
        async with self._lock:
            # Check if already connected
            if websocket in self.websocket_to_player:
                return self.websocket_to_player[websocket]
            
            # Find available player ID
            player_id = self._get_available_id()
            if player_id is None:
                logger.warning("Server full - cannot accept new player")
                return None
            
            # Create session
            session = PlayerSession(player_id=player_id, websocket=websocket)
            self.sessions[player_id] = session
            self.websocket_to_player[websocket] = player_id
            
            logger.info(f"Player {player_id} connected")
            
            if self.on_player_connected:
                try:
                    self.on_player_connected(player_id)
                except Exception as e:
                    logger.error(f"Error in on_player_connected callback: {e}")
            
            return player_id
    
    async def remove_player(self, websocket: Any) -> Optional[int]:
        """
        Remove a player session
        Returns the player ID that was removed
        """
        async with self._lock:
            player_id = self.websocket_to_player.get(websocket)
            if player_id is None:
                return None
            
            del self.websocket_to_player[websocket]
            del self.sessions[player_id]
            
            logger.info(f"Player {player_id} disconnected")
            
            if self.on_player_disconnected:
                try:
                    self.on_player_disconnected(player_id)
                except Exception as e:
                    logger.error(f"Error in on_player_disconnected callback: {e}")
            
            return player_id
    
    def get_player_id(self, websocket: Any) -> Optional[int]:
        """Get player ID for a WebSocket connection"""
        return self.websocket_to_player.get(websocket)
    
    def get_session(self, player_id: int) -> Optional[PlayerSession]:
        """Get session by player ID"""
        return self.sessions.get(player_id)
    
    def update_activity(self, player_id: int):
        """Update last activity time for a player"""
        session = self.sessions.get(player_id)
        if session:
            session.update_activity()
    
    def _get_available_id(self) -> Optional[int]:
        """Get the next available player ID (1-4)"""
        for i in range(1, self.MAX_PLAYERS + 1):
            if i not in self.sessions:
                return i
        return None
    
    def get_player_count(self) -> int:
        """Get current number of connected players"""
        return len(self.sessions)
    
    def get_all_player_ids(self) -> list:
        """Get list of all connected player IDs"""
        return list(self.sessions.keys())
    
    def is_full(self) -> bool:
        """Check if server is at max capacity"""
        return len(self.sessions) >= self.MAX_PLAYERS
    
    async def broadcast(self, message: str, exclude_player: Optional[int] = None):
        """Send a message to all connected players"""
        for player_id, session in self.sessions.items():
            if exclude_player and player_id == exclude_player:
                continue
            try:
                await session.websocket.send(message)
            except Exception as e:
                logger.error(f"Failed to send to player {player_id}: {e}")
    
    def clear_all(self):
        """Clear all sessions"""
        self.sessions.clear()
        self.websocket_to_player.clear()
        logger.info("All sessions cleared")
