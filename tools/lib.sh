# shellcheck shell=bash
# SPDX-License-Identifier: MIT
# Shared helpers for tools/*.sh. Source, don't run. Adapted from robotOS tools/lib.sh (MIT, see NOTICE).

say() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die() {
	printf '\033[1;31mxx\033[0m %s\n' "$*" >&2
	exit 1
}

need() { command -v "$1" >/dev/null 2>&1 || die "$1 not found"; }

# Ask before anything destructive. Skipped with YES=1.
confirm() {
	[[ "${YES:-0}" == 1 ]] && return 0
	local answer
	read -r -p "$1 [y/N] " answer
	[[ "${answer}" == [yY] ]] || die "Cancelled."
}

# Waits up to 90 s until `fastboot devices` lists something (bootloader or fastbootd).
wait_fastboot() {
	local found
	for _ in $(seq 90); do
		found=$(fastboot devices 2>/dev/null || true)
		[[ -n "${found}" ]] && return 0
		sleep 1
	done
	die "No fastboot device after 90 s."
}

# Prints "fastbootd" (userspace, can flash logical partitions) or "bootloader".
fastboot_mode() {
	local answer
	answer=$(fastboot getvar is-userspace 2>&1 || true)
	if [[ "${answer}" == *"is-userspace: yes"* ]]; then echo fastbootd; else echo bootloader; fi
}

# Prints a fastboot variable's value, e.g. `fastboot_var unlocked` -> yes.
fastboot_var() {
	local answer
	answer=$(fastboot getvar "$1" 2>&1 || true)
	sed -n "/^$1:/{s/^$1: *//p;q}" <<<"${answer}"
}

# Copies a vbmeta image with verification off (AVB header flags, big-endian u32 at byte 120, set to
# HASHTREE_DISABLED | VERIFICATION_DISABLED) and prints the copy's path. Same edit as
# `fastboot --disable-verity --disable-verification`, which some fastboot builds fail on.
vbmeta_disabled() {
	local dir name magic dst
	dir=$(mktemp -d)
	name=$(basename "$1" .img)
	magic=$(head -c 4 "$1")
	[[ "${magic}" == AVB0 ]] || die "$1 doesn't start with an AVB header"
	dst="${dir}/${name}-noverify.img"
	cp "$1" "${dst}"
	printf '\000\000\000\003' | dd of="${dst}" bs=1 seek=120 count=4 conv=notrunc 2>/dev/null
	echo "${dst}"
}

# Flashes the stock vbmeta images to both slots with verification off: the R1 only boots a
# non-rabbit system this way.
flash_vbmeta_disabled() {
	local v s patched
	for v in vbmeta vbmeta_system vbmeta_vendor; do
		patched=$(vbmeta_disabled "$1/${v}.img")
		for s in a b; do fastboot flash "${v}_${s}" "${patched}"; done
	done
}
