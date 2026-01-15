"""
Xbox Virtual Controller - QR Code Generator
Generates QR codes for easy mobile connection
"""

import qrcode
from qrcode.image.pil import PilImage
from PIL import Image
import io
import json
import socket
import logging
from typing import Optional, Tuple

logger = logging.getLogger(__name__)


def get_local_ip() -> str:
    """Get the local IP address of this machine"""
    try:
        # Create a socket to determine the local IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        # Fallback to localhost
        return "127.0.0.1"


def get_all_local_ips() -> list:
    """Get all local IP addresses"""
    ips = []
    try:
        import netifaces
        for interface in netifaces.interfaces():
            addrs = netifaces.ifaddresses(interface)
            if netifaces.AF_INET in addrs:
                for addr in addrs[netifaces.AF_INET]:
                    ip = addr.get('addr')
                    if ip and not ip.startswith('127.'):
                        ips.append(ip)
    except ImportError:
        # Fallback if netifaces not available
        ips.append(get_local_ip())
    except Exception as e:
        logger.error(f"Error getting local IPs: {e}")
        ips.append(get_local_ip())
    
    return ips if ips else [get_local_ip()]


def generate_connection_data(ip: str, port: int) -> str:
    """Generate JSON connection data for QR code"""
    data = {
        "type": "xbox_controller",
        "version": 1,
        "host": ip,
        "port": port,
        "protocol": "ws"
    }
    return json.dumps(data)


def generate_qr_code(ip: str, port: int, size: int = 300) -> Image.Image:
    """
    Generate a QR code image for the connection
    
    Args:
        ip: Server IP address
        port: Server port
        size: Size of the QR code image in pixels
        
    Returns:
        PIL Image object containing the QR code
    """
    connection_data = generate_connection_data(ip, port)
    
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=10,
        border=4,
    )
    qr.add_data(connection_data)
    qr.make(fit=True)
    
    # Create image with custom colors
    img = qr.make_image(fill_color="black", back_color="white")
    
    # Resize to desired size
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    
    return img


def generate_qr_bytes(ip: str, port: int, size: int = 300, 
                      format: str = 'PNG') -> bytes:
    """
    Generate QR code as bytes
    
    Args:
        ip: Server IP address
        port: Server port
        size: Size of the QR code image
        format: Image format (PNG, JPEG, etc.)
        
    Returns:
        Bytes of the image
    """
    img = generate_qr_code(ip, port, size)
    buffer = io.BytesIO()
    img.save(buffer, format=format)
    return buffer.getvalue()


def save_qr_code(ip: str, port: int, filepath: str, size: int = 300):
    """Save QR code to a file"""
    img = generate_qr_code(ip, port, size)
    img.save(filepath)
    logger.info(f"QR code saved to {filepath}")


class QRCodeManager:
    """Manages QR code generation and caching"""
    
    def __init__(self, port: int = 8765):
        self.port = port
        self._cached_ip: Optional[str] = None
        self._cached_image: Optional[Image.Image] = None
    
    def get_qr_image(self, force_refresh: bool = False) -> Tuple[Image.Image, str]:
        """
        Get the QR code image
        
        Returns:
            Tuple of (PIL Image, IP address used)
        """
        current_ip = get_local_ip()
        
        if force_refresh or self._cached_ip != current_ip or self._cached_image is None:
            self._cached_ip = current_ip
            self._cached_image = generate_qr_code(current_ip, self.port)
            logger.info(f"Generated new QR code for {current_ip}:{self.port}")
        
        return self._cached_image, self._cached_ip
    
    def get_connection_info(self) -> dict:
        """Get connection information"""
        ip = get_local_ip()
        return {
            "ip": ip,
            "port": self.port,
            "url": f"ws://{ip}:{self.port}",
            "all_ips": get_all_local_ips()
        }
