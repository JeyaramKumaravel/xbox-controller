"""
Xbox Virtual Controller - Controller Management
Uses vgamepad to create virtual Xbox 360 controllers via ViGEmBus
"""

import vgamepad as vg
from dataclasses import dataclass
from typing import Dict, Optional
import logging

logger = logging.getLogger(__name__)


@dataclass
class ControllerState:
    """Represents the current state of a controller"""
    buttons: Dict[str, bool]
    triggers: Dict[str, float]
    axes: Dict[str, float]
    
    @classmethod
    def default(cls) -> 'ControllerState':
        return cls(
            buttons={
                'a': False, 'b': False, 'x': False, 'y': False,
                'lb': False, 'rb': False,
                'start': False, 'back': False, 'guide': False,
                'l3': False, 'r3': False,
                'dpad_up': False, 'dpad_down': False,
                'dpad_left': False, 'dpad_right': False
            },
            triggers={'lt': 0.0, 'rt': 0.0},
            axes={'left_x': 0.0, 'left_y': 0.0, 'right_x': 0.0, 'right_y': 0.0}
        )


class VirtualController:
    """Manages a single virtual Xbox 360 controller"""
    
    # Button mapping from our names to vgamepad constants
    BUTTON_MAP = {
        'a': vg.XUSB_BUTTON.XUSB_GAMEPAD_A,
        'b': vg.XUSB_BUTTON.XUSB_GAMEPAD_B,
        'x': vg.XUSB_BUTTON.XUSB_GAMEPAD_X,
        'y': vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,
        'lb': vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
        'rb': vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
        'start': vg.XUSB_BUTTON.XUSB_GAMEPAD_START,
        'back': vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,
        'guide': vg.XUSB_BUTTON.XUSB_GAMEPAD_GUIDE,
        'l3': vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
        'r3': vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
        'dpad_up': vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
        'dpad_down': vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
        'dpad_left': vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
        'dpad_right': vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,
    }
    
    def __init__(self, player_id: int):
        self.player_id = player_id
        self.gamepad: Optional[vg.VX360Gamepad] = None
        self.state = ControllerState.default()
        self._connected = False
        
    def connect(self) -> bool:
        """Create and connect the virtual controller"""
        try:
            self.gamepad = vg.VX360Gamepad()
            self._connected = True
            logger.info(f"Virtual controller {self.player_id} connected")
            return True
        except Exception as e:
            logger.error(f"Failed to create virtual controller {self.player_id}: {e}")
            return False
    
    def disconnect(self):
        """Disconnect and destroy the virtual controller"""
        if self.gamepad:
            try:
                # Reset all inputs before disconnecting
                self.gamepad.reset()
                self.gamepad.update()
            except:
                pass
            self.gamepad = None
        self._connected = False
        logger.info(f"Virtual controller {self.player_id} disconnected")
    
    @property
    def is_connected(self) -> bool:
        return self._connected and self.gamepad is not None
    
    def update_state(self, buttons: Dict[str, bool], triggers: Dict[str, float], 
                     axes: Dict[str, float]):
        """Update the controller state from input data"""
        if not self.is_connected:
            return
            
        # Update buttons
        for button_name, pressed in buttons.items():
            if button_name in self.BUTTON_MAP:
                if pressed:
                    self.gamepad.press_button(self.BUTTON_MAP[button_name])
                else:
                    self.gamepad.release_button(self.BUTTON_MAP[button_name])
        
        # Update triggers (0.0 to 1.0 range)
        lt_value = max(0.0, min(1.0, triggers.get('lt', 0.0)))
        rt_value = max(0.0, min(1.0, triggers.get('rt', 0.0)))
        self.gamepad.left_trigger_float(lt_value)
        self.gamepad.right_trigger_float(rt_value)
        
        # Update joysticks (-1.0 to 1.0 range)
        left_x = max(-1.0, min(1.0, axes.get('left_x', 0.0)))
        left_y = max(-1.0, min(1.0, axes.get('left_y', 0.0)))
        right_x = max(-1.0, min(1.0, axes.get('right_x', 0.0)))
        right_y = max(-1.0, min(1.0, axes.get('right_y', 0.0)))
        
        self.gamepad.left_joystick_float(left_x, left_y)
        self.gamepad.right_joystick_float(right_x, right_y)
        
        # Send update to the virtual controller
        self.gamepad.update()
        
        # Store state
        self.state.buttons = buttons.copy()
        self.state.triggers = triggers.copy()
        self.state.axes = axes.copy()
    
    def vibrate(self, left_motor: int = 0, right_motor: int = 0):
        """
        Set vibration on the controller (for feedback to client)
        Values should be 0-255
        Note: This requires polling from the gamepad which vgamepad doesn't directly support
        """
        # Vibration feedback would require additional implementation
        # For now, this is a placeholder for future extension
        pass


class ControllerManager:
    """Manages multiple virtual controllers for multiplayer support"""
    
    MAX_CONTROLLERS = 4
    
    def __init__(self):
        self.controllers: Dict[int, VirtualController] = {}
        
    def create_controller(self, player_id: int) -> Optional[VirtualController]:
        """Create a new virtual controller for a player"""
        if player_id < 1 or player_id > self.MAX_CONTROLLERS:
            logger.error(f"Invalid player ID: {player_id}. Must be 1-{self.MAX_CONTROLLERS}")
            return None
            
        if player_id in self.controllers:
            logger.warning(f"Controller {player_id} already exists")
            return self.controllers[player_id]
        
        controller = VirtualController(player_id)
        if controller.connect():
            self.controllers[player_id] = controller
            return controller
        return None
    
    def get_controller(self, player_id: int) -> Optional[VirtualController]:
        """Get an existing controller by player ID"""
        return self.controllers.get(player_id)
    
    def remove_controller(self, player_id: int):
        """Remove and disconnect a controller"""
        if player_id in self.controllers:
            self.controllers[player_id].disconnect()
            del self.controllers[player_id]
            logger.info(f"Removed controller for player {player_id}")
    
    def get_available_player_id(self) -> Optional[int]:
        """Get the next available player ID"""
        for i in range(1, self.MAX_CONTROLLERS + 1):
            if i not in self.controllers:
                return i
        return None
    
    def get_connected_count(self) -> int:
        """Get the number of connected controllers"""
        return len(self.controllers)
    
    def disconnect_all(self):
        """Disconnect all controllers"""
        for controller in self.controllers.values():
            controller.disconnect()
        self.controllers.clear()
        logger.info("All controllers disconnected")
