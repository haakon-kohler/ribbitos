# Shared helpers for tools/*.sh. Source, don't run. Adapted from robotOS tools/lib.sh (MIT, see NOTICE).

say()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mxx\033[0m %s\n' "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "$1 not found"; }

# Ask before anything destructive. Skipped with YES=1.
confirm() {
	[ "${YES:-0}" = 1 ] && return 0
	read -r -p "$1 [y/N] " a
	[ "$a" = y ] || [ "$a" = Y ] || die "Cancelled."
}

# Waits until `fastboot devices` lists something (bootloader or fastbootd).
wait_fastboot() {
	for _ in $(seq 90); do
		fastboot devices 2>/dev/null | grep -q . && return 0
		sleep 1
	done
	die "No fastboot device after 90 s."
}

# True when connected to userspace fastbootd rather than the bootloader's fastboot.
in_fastbootd() { fastboot getvar is-userspace 2>&1 | grep -q 'is-userspace: yes'; }

# Copies a vbmeta image with verification off (AVB header flags, big-endian u32 at byte 120, set to
# HASHTREE_DISABLED | VERIFICATION_DISABLED) and prints the copy's path. Same edit as
# `fastboot --disable-verity --disable-verification`, which some fastboot builds fail on.
vbmeta_disabled() {
	local dst
	dst="$(mktemp -d)/$(basename "$1" .img)-noverify.img"
	[ "$(head -c 4 "$1")" = AVB0 ] || die "$1 doesn't start with an AVB header"
	cp "$1" "$dst"
	printf '\000\000\000\003' | dd of="$dst" bs=1 seek=120 count=4 conv=notrunc 2>/dev/null
	echo "$dst"
}

# Flashes the stock vbmeta images to both slots with verification off: the R1 only boots a
# non-rabbit system this way.
flash_vbmeta_disabled() {
	local v s patched
	for v in vbmeta vbmeta_system vbmeta_vendor; do
		patched=$(vbmeta_disabled "$1/$v.img")
		for s in a b; do fastboot flash "${v}_$s" "$patched"; done
	done
}
