#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Sets up an R1 running LineageOS (tools/flash-gsi.sh) as ribbitOS lite, from the computer, over
# adb. This is the standard install step, and safe to run again. Every setting it changes is
# listed below; mods can add their own steps after it.
#
# Usage: tools/provision.sh <ribbitos-lite.apk> [<settings file>]
# The settings file (keep it out of git) may set RIBBIT_PROVIDER (demo, openclaw, openai, claude),
# RIBBIT_BASE_URL, RIBBIT_TOKEN and RIBBIT_MODEL; see tools/r1.example.env.
set -euo pipefail
here=$(dirname "$0")
# shellcheck source=tools/lib.sh
source "${here}/lib.sh"
[[ $# -ge 1 && $# -le 2 ]] || die "usage: $0 <ribbitos-lite.apk> [<settings file>]"
need adb
apk=$1
[[ -f "${apk}" ]] || die "APK not found: ${apk}"
app=dev.ribbitos.lite

say "Installing ribbitOS lite as the home app"
adb install -r "${apk}" >/dev/null
adb shell cmd package set-home-activity "${app}/.HomeActivity" >/dev/null
adb shell pm grant "${app}" android.permission.RECORD_AUDIO

say "Display sized for the 2.9-inch screen"
adb shell wm density 190

say "Gesture navigation without the handle: nothing at the bottom of the screen"
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
adb shell cmd overlay enable org.lineageos.overlay.customization.navbar.nohint

say "LineageOS setup marked done, so its wizard never comes back"
adb shell settings put secure user_setup_complete 1
adb shell settings put global device_provisioned 1

say "Android's lock screen, simplified: ribbitOS's frog picture, the clock, no notifications"
# The picture is res/drawable/lock_screen.xml, set by the app (LockWallpaper.kt). A swipe unlocks,
# unless the settings file sets RIBBIT_PIN.
adb shell locksettings set-disabled false >/dev/null
adb shell settings put secure lock_screen_show_notifications 0

say "Only ribbitOS: switching off the apps in tools/debloat.txt"
while read -r pkg; do
	[[ -z "${pkg}" || "${pkg}" == \#* ]] && continue
	# -n: adb shell would otherwise read this loop's input and swallow the rest of the list.
	adb shell -n pm disable-user --user 0 "${pkg}" >/dev/null 2>&1 || warn "${pkg} not installed"
done <"${here}/debloat.txt"

if [[ $# -eq 2 ]]; then
	say "Provider settings from $2"
	# shellcheck disable=SC1090 # The settings file is the user's own, named on the command line.
	source "$2"
	args=(--es provider "${RIBBIT_PROVIDER:?set RIBBIT_PROVIDER in $2}")
	[[ -n "${RIBBIT_BASE_URL:-}" ]] && args+=(--es base_url "${RIBBIT_BASE_URL}")
	[[ -n "${RIBBIT_TOKEN:-}" ]] && args+=(--es token "${RIBBIT_TOKEN}")
	[[ -n "${RIBBIT_MODEL:-}" ]] && args+=(--es model "${RIBBIT_MODEL}")
	adb shell am broadcast -a "${app}.CONFIG" -n "${app}/.ConfigReceiver" "${args[@]}" >/dev/null
	if [[ -n "${RIBBIT_PIN:-}" ]]; then
		say "Lock screen PIN"
		adb shell locksettings set-pin "${RIBBIT_PIN}" >/dev/null ||
			warn "A PIN is already set; change it in Android settings (Security)"
	fi
fi

adb shell am start -n "${app}/.HomeActivity" >/dev/null
say "Done. ribbitOS lite is the home screen."
