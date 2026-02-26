#!/usr/bin/env python3
"""
Mock GPS controller for Biscuit app.
Sends simulated GPS data over TCP to the app's PositionSensor.

Usage:
  1. Enable "模拟GPS" in app Settings
  2. Start recording in the app
  3. Run: adb forward tcp:7777 tcp:7777
  4. Run: python mock_gps.py

Controls:
  W/S     - Move North/South
  A/D     - Move West/East
  +/=     - Increase speed
  -       - Decrease speed
  0       - Stop (speed = 0)
  Q/ESC   - Quit
"""

# ==================== 参数设置 ====================
INITIAL_LAT = 39.9042                # 初始纬度
INITIAL_LNG = 116.4074              # 初始经度
INITIAL_SPEED_KMH = 100.0             # 初始速度 (km/h)
INITIAL_HEADING = 0.0               # 初始方向 (度)
SPEED_STEP = 1.0                    # 每次按键速度增减量 (km/h)
MAX_SPEED_KMH = 8000.0              # 最大速度上限 (km/h)
DIRECTION_AUTO_SPEED_KMH = 5.0      # 静止时按方向键自动设定的速度 (km/h)
UPDATE_INTERVAL = 1.0               # GPS 更新间隔 (秒)
SERVER_HOST = '127.0.0.1'           # 连接主机地址
SERVER_PORT = 7777                  # 连接端口
CONNECT_TIMEOUT = 5                 # 连接超时 (秒)
MAX_RECONNECT_ATTEMPTS = 10         # 最大重连次数
RECONNECT_DELAY = 1                 # 重连间隔 (秒)
# =================================================

import socket
import json
import sys
import time
import math

try:
    import msvcrt  # Windows
    def get_key():
        if msvcrt.kbhit():
            ch = msvcrt.getch()
            if ch in (b'\x00', b'\xe0'):
                msvcrt.getch()
                return None
            return ch.decode('utf-8', errors='ignore').lower()
        return None
except ImportError:
    import tty, termios, select
    _old_settings = termios.tcgetattr(sys.stdin)
    tty.setcbreak(sys.stdin.fileno())
    def get_key():
        if select.select([sys.stdin], [], [], 0)[0]:
            return sys.stdin.read(1).lower()
        return None

lat = INITIAL_LAT
lng = INITIAL_LNG
speed_kmh = INITIAL_SPEED_KMH
heading = INITIAL_HEADING

def move(lat, lng, speed_kmh, heading_deg, dt_sec):
    if speed_kmh <= 0:
        return lat, lng
    speed_ms = speed_kmh / 3.6
    dist_m = speed_ms * dt_sec
    R = 6371000.0
    heading_rad = math.radians(heading_deg)
    dlat = (dist_m * math.cos(heading_rad)) / R
    dlng = (dist_m * math.sin(heading_rad)) / (R * math.cos(math.radians(lat)))
    return lat + math.degrees(dlat), lng + math.degrees(dlng)

def connect():
    """Try to connect, return socket or None."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    sock.settimeout(CONNECT_TIMEOUT)
    try:
        sock.connect((SERVER_HOST, SERVER_PORT))
        sock.settimeout(None)
        return sock
    except Exception as e:
        try: sock.close()
        except: pass
        return None

def main():
    global lat, lng, speed_kmh, heading

    print("Mock GPS Controller for Biscuit")
    print("================================")
    print("Make sure you ran: adb forward tcp:7777 tcp:7777")
    print("Make sure Mock GPS is enabled in app settings and recording is started.\n")

    print(f"Connecting to device on {SERVER_HOST}:{SERVER_PORT}...")
    sock = connect()
    if not sock:
        print("Connection failed. Check that:")
        print("  1. 'Mock GPS' is enabled in app settings")
        print("  2. Recording is started in the app")
        print(f"  3. adb forward tcp:{SERVER_PORT} tcp:{SERVER_PORT} is running")
        sys.exit(1)

    print("Connected!\n")
    print(f"Position: {lat:.6f}, {lng:.6f}")
    print(f"Speed: {speed_kmh:.1f} km/h  Heading: {heading:.0f}\n")
    print("Controls: W=North S=South A=West D=East  +/-=Speed  0=Stop  Q=Quit\n")

    running = True
    last_time = time.time()
    reconnect_count = 0

    try:
        while running:
            key = get_key()
            if key:
                if key == 'w':
                    heading = 0.0
                    if speed_kmh == 0: speed_kmh = DIRECTION_AUTO_SPEED_KMH
                elif key == 's':
                    heading = 180.0
                    if speed_kmh == 0: speed_kmh = DIRECTION_AUTO_SPEED_KMH
                elif key == 'a':
                    heading = 270.0
                    if speed_kmh == 0: speed_kmh = DIRECTION_AUTO_SPEED_KMH
                elif key == 'd':
                    heading = 90.0
                    if speed_kmh == 0: speed_kmh = DIRECTION_AUTO_SPEED_KMH
                elif key in ('+', '='):
                    speed_kmh = min(speed_kmh + SPEED_STEP, MAX_SPEED_KMH)
                elif key == '-':
                    speed_kmh = max(speed_kmh - SPEED_STEP, 0.0)
                elif key == '0':
                    speed_kmh = 0.0
                elif key in ('q', '\x1b'):
                    break

            now = time.time()
            dt = now - last_time
            if dt >= UPDATE_INTERVAL:
                lat, lng = move(lat, lng, speed_kmh, heading, dt)
                last_time = now

                data = json.dumps({
                    "lat": round(lat, 7),
                    "lng": round(lng, 7),
                    "speed": round(speed_kmh, 1)
                }) + "\n"

                try:
                    sock.sendall(data.encode('utf-8'))
                    reconnect_count = 0
                except (BrokenPipeError, ConnectionAbortedError, ConnectionResetError, OSError) as e:
                    print(f"\n  Connection lost: {e}")
                    try: sock.close()
                    except: pass
                    # Auto-reconnect
                    reconnect_count += 1
                    if reconnect_count > MAX_RECONNECT_ATTEMPTS:
                        print("  Too many reconnect attempts, giving up.")
                        break
                    print(f"  Reconnecting (attempt {reconnect_count})...")
                    time.sleep(RECONNECT_DELAY)
                    sock = connect()
                    if sock:
                        print("  Reconnected!")
                    else:
                        print("  Reconnect failed, will retry...")
                        continue

                dirs = {0: "N", 90: "E", 180: "S", 270: "W"}
                dir_str = dirs.get(int(heading), f"{heading:.0f}")
                sys.stdout.write(f"\r  Lat={lat:.6f} Lng={lng:.6f}  Speed={speed_kmh:.1f}km/h  Dir={dir_str}    ")
                sys.stdout.flush()

            time.sleep(0.05)
    except KeyboardInterrupt:
        pass
    finally:
        try: sock.close()
        except: pass
        print("\nDone.")
        try: termios.tcsetattr(sys.stdin, termios.TCSADRAIN, _old_settings)
        except: pass

if __name__ == '__main__':
    main()
