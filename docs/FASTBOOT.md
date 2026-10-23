# Getting the R1 into fastboot

The R1 has no volume buttons, so the usual Android key combo doesn't exist. Instead, the
MediaTek preloader listens on USB for about 1.5 seconds at power-on, and `tools/enter-fastboot.py`
sends it the text `FASTBOOT` in that window. This works from any state: stock rabbitOS, its
setup screen, LineageOS, or a boot loop.

1. Unplug the R1. (Powering off while plugged in boots it into off-mode charging instead.)
2. Power it off: hold the side button. Take the power-off option if one appears; otherwise keep
   holding for about 10-12 s until the screen goes black (hardware power-off).
3. Wait about 5 s and check that the screen stays black. If it lights up again, repeat step 2.
4. On the computer, start the listener:
   `python3 tools/enter-fastboot.py` (needs `pyserial`, and the udev rule in
   `tools/70-rabbit-r1.rules` on Linux).
5. Plug the cable into the R1 in one quick, firm push, laptop end already connected. No buttons.
6. The screen shows **FASTBOOT** within about 2 s; `fastboot devices` lists it.

**Charging icon instead of FASTBOOT?** The USB connection bounced while the preloader was
listening (the kernel logs `device descriptor read/64, error -71`). Unplug, wait 5 s, and plug in
again; the listener keeps waiting. If it keeps happening, try another USB port.

From LineageOS with USB debugging on, `adb reboot bootloader` gets there without any of this.
