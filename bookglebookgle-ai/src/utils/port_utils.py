"""
Port management utilities for BGBG AI Server
"""

import socket
import subprocess
import sys
from typing import List, Optional
from loguru import logger


def is_port_in_use(port: int, host: str = "localhost") -> bool:
    """
    Check if a port is currently in use.
    
    Args:
        port: Port number to check
        host: Host to check (default: localhost)
    
    Returns:
        True if port is in use, False otherwise
    """
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(1)
            result = sock.connect_ex((host, port))
            return result == 0
    except Exception:
        return False


def find_free_port(start_port: int = 50505, max_attempts: int = 100) -> Optional[int]:
    """
    Find a free port starting from start_port.
    
    Args:
        start_port: Starting port number
        max_attempts: Maximum number of ports to check
    
    Returns:
        Free port number or None if not found
    """
    for port in range(start_port, start_port + max_attempts):
        if not is_port_in_use(port):
            return port
    return None


def get_process_using_port(port: int) -> Optional[dict]:
    """
    Get information about the process using a specific port.
    
    Args:
        port: Port number to check
    
    Returns:
        Dictionary with process info or None if not found
    """
    try:
        if sys.platform == "win32":
            # Windows
            result = subprocess.run(
                ["netstat", "-ano"], 
                capture_output=True, 
                text=True, 
                check=True
            )
            
            for line in result.stdout.split('\n'):
                if f":{port}" in line and "LISTENING" in line:
                    parts = line.split()
                    if len(parts) >= 5:
                        pid = parts[-1]
                        try:
                            # Get process name
                            tasklist_result = subprocess.run(
                                ["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV"],
                                capture_output=True,
                                text=True,
                                check=True
                            )
                            lines = tasklist_result.stdout.strip().split('\n')
                            if len(lines) >= 2:
                                # Parse CSV output
                                process_line = lines[1].replace('"', '').split(',')
                                if len(process_line) >= 2:
                                    return {
                                        "pid": int(pid),
                                        "name": process_line[0],
                                        "port": port
                                    }
                        except (subprocess.CalledProcessError, ValueError):
                            return {"pid": int(pid), "name": "unknown", "port": port}
        else:
            # Linux/Mac
            result = subprocess.run(
                ["lsof", "-i", f":{port}"],
                capture_output=True,
                text=True,
                check=True
            )
            
            lines = result.stdout.strip().split('\n')
            if len(lines) >= 2:
                parts = lines[1].split()
                if len(parts) >= 2:
                    return {
                        "pid": int(parts[1]),
                        "name": parts[0],
                        "port": port
                    }
    
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass
    
    return None


def kill_process_on_port(port: int) -> bool:
    """
    Kill the process using a specific port.
    
    Args:
        port: Port number
    
    Returns:
        True if process was killed, False otherwise
    """
    process_info = get_process_using_port(port)
    if not process_info:
        logger.info(f"No process found using port {port}")
        return False
    
    pid = process_info["pid"]
    name = process_info["name"]
    
    try:
        if sys.platform == "win32":
            # Windows
            subprocess.run(["taskkill", "/F", "/PID", str(pid)], check=True)
        else:
            # Linux/Mac
            subprocess.run(["kill", "-9", str(pid)], check=True)
        
        logger.info(f"✅ Killed process {name} (PID: {pid}) using port {port}")
        return True
    
    except subprocess.CalledProcessError as e:
        logger.error(f"❌ Failed to kill process {name} (PID: {pid}): {e}")
        return False


def ensure_port_free(port: int, kill_if_needed: bool = True) -> bool:
    """
    Ensure a port is free, optionally killing the process using it.
    
    Args:
        port: Port number to check
        kill_if_needed: Whether to kill process if port is in use
    
    Returns:
        True if port is free, False otherwise
    """
    if not is_port_in_use(port):
        logger.info(f"✅ Port {port} is already free")
        return True
    
    if not kill_if_needed:
        logger.warning(f"⚠️  Port {port} is in use and kill_if_needed is False")
        return False
    
    logger.warning(f"🔄 Port {port} is in use, attempting to free it...")
    return kill_process_on_port(port)


def check_ports_status(ports: List[int]) -> dict:
    """
    Check the status of multiple ports.
    
    Args:
        ports: List of port numbers to check
    
    Returns:
        Dictionary mapping port numbers to their status
    """
    status = {}
    
    for port in ports:
        if is_port_in_use(port):
            process_info = get_process_using_port(port)
            status[port] = {
                "in_use": True,
                "process": process_info
            }
        else:
            status[port] = {
                "in_use": False,
                "process": None
            }
    
    return status


def print_ports_report(ports: List[int]) -> None:
    """
    Print a detailed report of port usage.
    
    Args:
        ports: List of port numbers to check
    """
    logger.info("🔍 Port Usage Report")
    logger.info("=" * 50)
    
    status = check_ports_status(ports)
    
    for port, info in status.items():
        if info["in_use"]:
            process = info["process"]
            if process:
                logger.info(f"🔴 Port {port}: IN USE by {process['name']} (PID: {process['pid']})")
            else:
                logger.info(f"🔴 Port {port}: IN USE by unknown process")
        else:
            logger.info(f"🟢 Port {port}: AVAILABLE")
    
    logger.info("=" * 50)


if __name__ == "__main__":
    # Example usage
    common_ports = [50505, 8126, 6379]  # 내부 Redis 포트로 변경
    print_ports_report(common_ports)