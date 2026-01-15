"""
Mouse and Keyboard Controller
Handles remote mouse movements, clicks, and keyboard input
"""

from pynput.mouse import Controller as MouseController, Button
from pynput.keyboard import Controller as KeyboardController, Key
import logging

logger = logging.getLogger(__name__)


class MouseKeyboardController:
    """Controls the PC mouse and keyboard from remote input"""
    
    def __init__(self):
        self.mouse = MouseController()
        self.keyboard = KeyboardController()
        self.mouse_sensitivity = 2.0  # Multiplier for mouse movement
        
        # Track button states for proper press/release
        self.left_button_pressed = False
        self.right_button_pressed = False
        
        logger.info("MouseKeyboardController initialized")
    
    def move_mouse(self, delta_x: float, delta_y: float):
        """Move mouse by relative delta values"""
        # Apply sensitivity and move
        dx = int(delta_x * self.mouse_sensitivity)
        dy = int(delta_y * self.mouse_sensitivity)
        
        if dx != 0 or dy != 0:
            self.mouse.move(dx, dy)
    
    def click(self, button: str = "left"):
        """Perform a mouse click"""
        btn = Button.left if button == "left" else Button.right
        self.mouse.click(btn)
    
    def press_mouse(self, button: str = "left"):
        """Press and hold mouse button"""
        btn = Button.left if button == "left" else Button.right
        if button == "left" and not self.left_button_pressed:
            self.mouse.press(btn)
            self.left_button_pressed = True
        elif button == "right" and not self.right_button_pressed:
            self.mouse.press(btn)
            self.right_button_pressed = True
    
    def release_mouse(self, button: str = "left"):
        """Release mouse button"""
        btn = Button.left if button == "left" else Button.right
        if button == "left" and self.left_button_pressed:
            self.mouse.release(btn)
            self.left_button_pressed = False
        elif button == "right" and self.right_button_pressed:
            self.mouse.release(btn)
            self.right_button_pressed = False
    
    def scroll(self, dx: int = 0, dy: int = 0):
        """Scroll the mouse wheel"""
        self.mouse.scroll(dx, dy)
    
    def type_text(self, text: str):
        """Type a string of text"""
        if text:
            self.keyboard.type(text)
    
    def press_key(self, key: str):
        """Press a specific key"""
        key_map = {
            "enter": Key.enter,
            "space": Key.space,
            "backspace": Key.backspace,
            "tab": Key.tab,
            "escape": Key.esc,
            "up": Key.up,
            "down": Key.down,
            "left": Key.left,
            "right": Key.right,
            "ctrl": Key.ctrl,
            "alt": Key.alt,
            "shift": Key.shift,
            "delete": Key.delete,
            "home": Key.home,
            "end": Key.end,
            "pageup": Key.page_up,
            "pagedown": Key.page_down,
            "f1": Key.f1,
            "f2": Key.f2,
            "f3": Key.f3,
            "f4": Key.f4,
            "f5": Key.f5,
            "f6": Key.f6,
            "f7": Key.f7,
            "f8": Key.f8,
            "f9": Key.f9,
            "f10": Key.f10,
            "f11": Key.f11,
            "f12": Key.f12,
        }
        
        if key.lower() in key_map:
            self.keyboard.press(key_map[key.lower()])
            self.keyboard.release(key_map[key.lower()])
        elif len(key) == 1:
            # Single character
            self.keyboard.press(key)
            self.keyboard.release(key)
    
    def key_combination(self, keys: list):
        """Press a key combination like Ctrl+C"""
        key_map = {
            "ctrl": Key.ctrl,
            "alt": Key.alt,
            "shift": Key.shift,
            "win": Key.cmd,
        }
        
        # Press all modifier keys
        pressed = []
        for k in keys[:-1]:  # All but last
            if k.lower() in key_map:
                self.keyboard.press(key_map[k.lower()])
                pressed.append(key_map[k.lower()])
        
        # Press the final key
        final_key = keys[-1]
        if final_key.lower() in key_map:
            self.keyboard.press(key_map[final_key.lower()])
            self.keyboard.release(key_map[final_key.lower()])
        elif len(final_key) == 1:
            self.keyboard.press(final_key)
            self.keyboard.release(final_key)
        
        # Release modifier keys
        for k in reversed(pressed):
            self.keyboard.release(k)
    
    def set_sensitivity(self, sensitivity: float):
        """Set mouse movement sensitivity"""
        self.mouse_sensitivity = max(0.1, min(10.0, sensitivity))


# Singleton instance
_mouse_keyboard_controller = None

def get_mouse_keyboard_controller() -> MouseKeyboardController:
    """Get the singleton MouseKeyboardController instance"""
    global _mouse_keyboard_controller
    if _mouse_keyboard_controller is None:
        _mouse_keyboard_controller = MouseKeyboardController()
    return _mouse_keyboard_controller
