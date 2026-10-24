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
adb install -r "${apk}" >/dev/null ||
    die "Install failed. A ribbitOS signed with another key (v0.01-alpha) must go first: adb uninstall ${app} (clears its settings)"
adb shell cmd package set-home-activity "${app}/.HomeActivity" >/dev/null
adb shell pm grant "${app}" android.permission.RECORD_AUDIO
adb shell pm grant "${app}" android.permission.CAMERA

say "Display sized for the 2.9-inch screen"
adb shell wm density 190

say "Gesture navigation without the handle: nothing at the bottom of the screen"
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
adb shell cmd overlay enable org.lineageos.overlay.customization.navbar.nohint

say "Android's colours from ribbitOS's amber: the keyboard, the lock screen's clock"
amber=FFB020 # @color/accent in app/src/main/res/values/colors.xml
theme='{"android.theme.customization.color_source":"preset","android.theme.customization.color_both":"1"'
theme+=',"android.theme.customization.theme_style":"TONAL_SPOT"'
theme+=",\"android.theme.customization.system_palette\":\"${amber}\",\"android.theme.customization.accent_color\":\"${amber}\"}"
adb shell "settings put secure theme_customization_overlay_packages '${theme}'"

say "No hide-keyboard button: tapping outside the text box puts the keyboard away"
# In gesture navigation Android 14 draws a back chevron under the keyboard. Two overlays remove it:
# the keyboard stops drawing its own button row, and the system bar's layout loses its back button.
# Fabricating overlays needs root, which userdebug builds (the GSI) give adb.
adb root >/dev/null 2>&1 || true
sleep 2
adb wait-for-device
uid=$(adb shell id -u)
if [[ "${uid}" == 0 ]]; then
	adb shell cmd overlay fabricate --target android --name RibbitImeNavBar \
		android:bool/config_imeDrawsImeNavBar 0x12 0
	adb shell "cmd overlay fabricate --target com.android.systemui --name RibbitNavLayout \
		com.android.systemui:string/config_navBarLayoutHandle 0x03 'space;home_handle;space'"
	adb shell cmd overlay enable --user 0 com.android.shell:RibbitImeNavBar
	adb shell cmd overlay enable --user 0 com.android.shell:RibbitNavLayout
	adb unroot >/dev/null
	sleep 2
	adb wait-for-device
else
	warn "adb has no root on this build; the keyboard keeps its hide button"
fi

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
	[[ -n "${RIBBIT_TEXT_SIZE:-}" ]] && args+=(--es text_size "${RIBBIT_TEXT_SIZE}")
	adb shell am broadcast -a "${app}.CONFIG" -n "${app}/.ConfigReceiver" "${args[@]}" >/dev/null
	if [[ -n "${RIBBIT_PIN:-}" ]]; then
		say "Lock screen PIN"
		adb shell locksettings set-pin "${RIBBIT_PIN}" >/dev/null ||
			warn "A PIN is already set; change it in Android settings (Security)"
	fi
fi

adb shell am start -n "${app}/.HomeActivity" >/dev/null
say "Done. ribbitOS lite is the home screen."
