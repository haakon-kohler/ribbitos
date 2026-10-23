#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Flash a LineageOS GSI onto the R1's active slot, keeping rabbit's kernel and vendor (the side
# button only works with rabbit's kernel). Wipes the R1. Adapted from robotOS tools/flash-gsi.sh.
# Needs the R1 in fastboot (tools/enter-fastboot.py) with the bootloader unlocked.
set -euo pipefail
# shellcheck source=tools/lib.sh
source "$(dirname "$0")/lib.sh"
[[ $# -eq 2 ]] || die "usage: $0 <unpacked GSI .img> <extracted stock rabbitOS folder>"
need fastboot
gsi=$1 stock=$2
[[ -f "${gsi}" ]] || die "GSI not found: ${gsi}"
[[ -f "${stock}/vbmeta.img" && -f "${stock}/userdata.img" ]] || die "No vbmeta.img/userdata.img in ${stock}"

wait_fastboot
mode=$(fastboot_mode)
if [[ "${mode}" == fastbootd ]]; then
	say "In fastbootd; going to the bootloader first for vbmeta"
	fastboot reboot bootloader
	sleep 5
	wait_fastboot
fi
unlocked=$(fastboot_var unlocked)
[[ "${unlocked}" == yes ]] || die "Bootloader is locked."
slot=$(fastboot_var current-slot)
[[ "${slot}" == [ab] ]] || die "Couldn't read the active slot."
name=$(basename "${gsi}")
confirm "Flash ${name} to system_${slot} and ERASE all data on the R1?"

say "vbmeta with verification off"
flash_vbmeta_disabled "${stock}"
say "Wiping data"
fastboot erase userdata
fastboot flash userdata "${stock}/userdata.img"

say "Rebooting to fastbootd"
fastboot reboot fastboot
sleep 5
wait_fastboot
mode=$(fastboot_mode)
[[ "${mode}" == fastbootd ]] || die "Didn't reach fastbootd."
fastboot snapshot-update cancel 2>/dev/null || true
say "Removing rabbit's product_${slot} (its launcher and services)"
fastboot delete-logical-partition "product_${slot}" || warn "product_${slot} not deleted (maybe already gone)"
say "Flashing the GSI to system_${slot}"
fastboot flash "system_${slot}" "${gsi}"
say "Booting LineageOS; the first boot takes a few minutes."
fastboot reboot
