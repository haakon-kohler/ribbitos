# Modding ribbitOS

ribbitOS lite is meant to be a base for mods. A mod is a **fork**: change what you want, build it,
and put it on an R1 with `tools/provision.sh`. There is no plug-in API and no app store, on purpose.

## Why a fork and not apps

The R1's hardware (microphone, camera, camera motor) is reachable by one app only: ribbitOS. Letting
other apps use it would need an exported device API, a permission scheme, and someone vetting which
apps get it, which is an app store in all but name. A fork keeps everything that touches the
hardware in one codebase that you build, read and sign yourself.

Planned for a later release: the AI you talk to (OpenClaw, say) asks ribbitOS for device actions
("take a photo", "turn the camera") as tools. Mods on the server side then won't need a fork, and
ribbitOS stays the only thing on the R1 that touches the hardware.

## The device, piece by piece

Every device option is a small module. The ones that need hardware sit behind an interface in
`Wiring` (`app/src/main/java/dev/ribbitos/lite/Backend.kt`): change the default there to swap one in
your fork, and assign a fake in tests, the way `HomeActivityTest` does.

| Part | Where | To change it |
| --- | --- | --- |
| Who answers | `Backend`, `net/*Backend.kt` | `Wiring.backendFactory`; [RULES.md](RULES.md#adding-a-backend) |
| Microphone | `voice/MicRecorder.kt` (`Recorder`) | `Wiring.recorderFactory` |
| Speech to text | `voice/WhisperTranscriber.kt` (`Transcriber`) | `Wiring.transcriberFactory`; [VOICE.md](VOICE.md) |
| Camera motor | `camera/Motor.kt` (`SysfsMotor`) | `Wiring.motorFactory` |
| Camera | `camera/Lens.kt` (Camera2), `camera/CameraActivity.kt` | edit them |
| Photo size and orientation | `camera/Photo.kt` | its constants |
| Background work | `Wiring.executor` | swap it |
| Bounds (lengths, timeouts) | `Limits.kt` | its constants |
| Lock screen picture | `res/drawable/lock_screen.xml`, `LockWallpaper.kt` | edit the drawable, bump `LockWallpaper.VERSION` |
| Settings and keys | `Settings.kt`, `ConfigReceiver.kt` | push them over adb (below) |
| Text size | `TextSize.kt` | its sizes; picked in settings or over adb |
| Updates | `update/` (`Feed.DEFAULT`, `res/raw/update_key.pem`) | your feed and release key; [UPDATES.md](UPDATES.md) |
| What Android shows and runs, its colours, the keyboard | `tools/provision.sh`, `tools/debloat.txt` | edit, re-run |

**Buttons.** The scroll wheel sends DPAD up/down: the chat scrolls with it
(`HomeActivity.dispatchKeyEvent`), and in the camera one notch flips the camera round
(`CameraActivity.dispatchKeyEvent`).
The side button is Android's power key: a press locks, a hold opens the power menu. The R1 has no
other keys.

**Settings over adb.** Nobody should type an API key on a 2.9-inch screen:

```sh
adb shell am broadcast -a dev.ribbitos.lite.CONFIG -n dev.ribbitos.lite/.ConfigReceiver \
  --es provider openai --es token "$KEY" --es model gpt-6.1-sol
```

`tools/provision.sh` does the same from an env file (`tools/r1.example.env`).

## The security model

- **Only ribbitOS touches the hardware.** Its camera and settings screens aren't exported, and it
  offers no service, provider or intent that lets another app record, take a photo, turn the
  camera or install anything.
- **Settings come from you.** `ConfigReceiver` accepts broadcasts only from senders holding `DUMP`:
  adb's shell and root, not apps.
- **Permissions are Android's own.** `RECORD_AUDIO` and `CAMERA` are runtime permissions;
  `provision.sh` grants them, and you can revoke them in Android's settings.
- **The camera motor needs the platform key.** The motor's sysfs file is labelled `sysfs_motor`,
  and rabbit's vendor SELinux policy lets only platform and system apps write it. ribbitOS is signed
  with AOSP's public test platform key, which the LineageOS GSI is also signed with
  ([keys/README.md](../keys/README.md)). That key is public, so on this GSI any APK signed with it
  is a platform app too: install only APKs you built or trust.
- **Updates must be signed by the release key.** The platform key is public, so it can't vouch
  for an update; a signed `update.json` naming the APK's SHA-256 does ([UPDATES.md](UPDATES.md)).
- **Keys stay on the device**, in the app's private storage. Never put one in the build
  ([RULES.md](RULES.md) rule 7).

## Keeping a mod healthy

Run `./gradlew check assembleRelease` and keep it green: [RULES.md](RULES.md) is the bar ribbitOS
holds itself to, and the build enforces most of it. ribbitOS is MIT; keep [NOTICE](../NOTICE) with
your fork.
