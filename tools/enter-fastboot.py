#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Send the R1's MediaTek preloader the FASTBOOT boot command, as rabbit's web flash tool does.

R1 powered off and unplugged: start this, then plug it in. The preloader listens for ~1.5 s
whenever the R1 boots, so a restart while plugged in works too. See docs/FASTBOOT.md.
"""

import sys
import time

import serial
from serial.tools import list_ports

MEDIATEK_VID = 0x0E8D
PRELOADER_PID = 0x2000
BAUD = 115200
POLL_S = 0.02
WAIT_LIMIT_S = 600.0  # Give up after 10 minutes without a preloader.
OPEN_RETRY_S = 1.0  # The tty can show up a moment before udev grants us access to it.
READ_TIMEOUT_S = 0.3
GREETING_BYTES = 5  # The preloader says "READY" first; the reply doesn't depend on it.


def preloader_port() -> str | None:
    """Return the preloader's serial device, or None while it isn't plugged in."""
    for p in list_ports.comports():
        if p.vid == MEDIATEK_VID and p.pid == PRELOADER_PID:
            return p.device
    return None


def wait_for_preloader() -> str | None:
    """Poll for the preloader for up to WAIT_LIMIT_S seconds."""
    deadline = time.monotonic() + WAIT_LIMIT_S
    while time.monotonic() < deadline:
        dev = preloader_port()
        if dev is not None:
            return dev
        time.sleep(POLL_S)
    return None


def try_open(dev: str) -> serial.Serial | None:
    """Open dev, or return None if it can't be opened (yet)."""
    try:
        return serial.Serial(dev, BAUD, timeout=READ_TIMEOUT_S)
    except serial.SerialException:
        return None


def open_port(dev: str) -> serial.Serial:
    """Open dev, retrying for up to OPEN_RETRY_S seconds; the last attempt's error explains a failure."""
    deadline = time.monotonic() + OPEN_RETRY_S
    while time.monotonic() < deadline:
        port = try_open(dev)
        if port is not None:
            return port
        time.sleep(POLL_S)
    return serial.Serial(dev, BAUD, timeout=READ_TIMEOUT_S)


def main() -> int:
    """Wait for the preloader and hand it the FASTBOOT command."""
    print("waiting for the R1 preloader (0e8d:2000), plug it in now", flush=True)
    dev = wait_for_preloader()
    if dev is None:
        print(f"no preloader within {WAIT_LIMIT_S:.0f} s; see docs/FASTBOOT.md", file=sys.stderr)
        return 1
    with open_port(dev) as port:
        greeting = port.read(GREETING_BYTES)
        port.write(b"FASTBOOT")
        port.flush()
    print(f"{dev}: preloader said {greeting!r}, sent FASTBOOT; it should come back as 0e8d:201c (fastboot)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
