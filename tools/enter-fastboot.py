#!/usr/bin/env python3
# Send the R1's MediaTek preloader the FASTBOOT boot command, as rabbit's web flash tool does.
# R1 powered off and unplugged: start this, then plug it in. The preloader listens for ~1.5 s.
import time

import serial
from serial.tools import list_ports


def preloader_port():
    for p in list_ports.comports():
        if p.vid == 0x0E8D and p.pid == 0x2000:
            return p.device
    return None


print("waiting for the R1 preloader (0e8d:2000), plug it in now", flush=True)
while not (dev := preloader_port()):
    time.sleep(0.02)

# The tty can show up a moment before udev grants us access to it.
deadline = time.monotonic() + 1
while True:
    try:
        port = serial.Serial(dev, 115200, timeout=0.3)
        break
    except serial.SerialException:
        if time.monotonic() > deadline:
            raise
        time.sleep(0.02)

with port:
    greeting = port.read(5)
    port.write(b"FASTBOOT")
    port.flush()
print(f"{dev}: preloader said {greeting!r}, sent FASTBOOT; it should come back as 0e8d:201c (fastboot)")
