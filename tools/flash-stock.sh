#!/usr/bin/env bash
# Flash stock rabbitOS the way rabbit's web tool does (rabbit-hmi-oss.github.io/flashing, js/stock-flash.js),
# minus its final `flashing lock`: an open OS comes next, and every lock/unlock cycle wipes the R1.
# Needs the R1 in LK fastboot (enter-fastboot.py) with the bootloader already unlocked.
set -euo pipefail
img="${1:?usage: flash-stock.sh <extracted firmware dir>}"

fastboot getvar unlocked 2>&1 | grep -q 'unlocked: yes' || { echo "bootloader is locked: fastboot flashing unlock first" >&2; exit 1; }

fastboot erase userdata
while read -r part file; do
	for slot in a b; do fastboot flash "${part}_$slot" "$img/$file"; done
done <<'EOF'
preloader preloader_k65v1_64_bsp.bin
vbmeta vbmeta.img
vbmeta_system vbmeta_system.img
vbmeta_vendor vbmeta_vendor.img
md1img md1img.img
spmfw spmfw.img
scp scp.img
sspm sspm.img
gz gz.img
lk lk.img
boot boot.img
dtbo dtbo.img
tee tee.img
EOF
fastboot flash logo "$img/logo.bin"
fastboot flash super "$img/super.img"
fastboot flash userdata "$img/userdata.img"
fastboot set_active a
fastboot reboot
