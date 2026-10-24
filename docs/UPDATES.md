# Updates

ribbitOS lite updates itself. Nobody has to plug the R1 in or tap anything.

## How it works

Every 6 hours, on Wi-Fi, the R1 checks its update feed (`update/UpdateJob.kt`). Android runs that
check only once the R1 has been left alone with its screen off for a while (JobScheduler's "idle"),
so an update never lands in the middle of a chat.

The feed is a folder of four files, by default the latest GitHub release of this repository
(`Feed.DEFAULT`):

| File | What it is |
| --- | --- |
| `update.json` | the build's `versionCode`, `versionName`, APK file name and SHA-256 |
| `update.json.sig` | the release key's signature over `update.json` (ECDSA P-256, SHA-256) |
| `ribbitos-lite-<tag>.apk` | the build itself |
| `SHA256SUMS` | checksums, for people |

If `update.json` is signed by the release key and names a higher `versionCode` than the installed
one, the R1 downloads the APK, checks its SHA-256, and installs it with Android's package installer.
ribbitOS is a platform app (keys/README.md), so Android lets it install without asking, then
restarts it on the home screen.

## Why the signature matters

Android only installs an update signed with the same key as the installed app. Here that key is
AOSP's *public* test platform key ([keys/README.md](../keys/README.md)), so the check proves
nothing: anyone can sign an APK with it. The release key is what proves an update is genuine. Its
public half ships in the app (`app/src/main/res/raw/update_key.pem`); the private half never enters
the repository.

So an update has to get past every one of these:

- `update.json` must carry a valid signature by the release key (`Release.parse`).
- The APK must match the SHA-256 in that signed file (`Feed.download`).
- Android refuses an APK signed with any key but the installed app's, and any `versionCode` that
  isn't higher.
- Sizes are bounded (`Limits.MAX_UPDATE_MANIFEST_BYTES`, `Limits.MAX_UPDATE_APK_BYTES`).

## Publishing a release

You need the release key's private half at `~/.config/ribbitos/update-key.pem` (or wherever
`$RIBBITOS_UPDATE_KEY` points). Keep a backup somewhere safe: without it, installed R1s can't be
updated anymore and need a manual reinstall of a build carrying a new key.

1. Raise `versionCode` and set `versionName` in `app/build.gradle.kts`.
2. `./gradlew check assembleRelease`, then test the release build on an R1
   ([RULES.md](RULES.md) rule 5).
3. `tools/release.py v<versionName>` writes and checks `dist/v<versionName>/`.
4. `tools/release.py v<versionName> --publish notes.md` publishes it as the latest GitHub release.
   R1s pick it up at their next idle check.

Pre-releases aren't "latest", so they never reach R1s on their own.

## Testing an update on an R1

Point the R1 at a feed on your computer, over USB:

```sh
cd dist/v0.02 && python3 -m http.server 8765 --bind 127.0.0.1 &
adb reverse tcp:8765 tcp:8765
adb shell am broadcast -a dev.ribbitos.lite.CONFIG -n dev.ribbitos.lite/.ConfigReceiver \
  --es update_feed http://127.0.0.1:8765/
adb shell cmd jobscheduler run -f dev.ribbitos.lite 1    # check now, without waiting for idle
adb logcat -s ribbitos                                    # "Update check: INSTALLING"
```

Send `--es update_feed "''"` to go back to the default feed. Another feed can't sneak anything in:
its `update.json` still needs the release key's signature.

## In a fork

Make your own release key (`openssl ecparam -name prime256v1 -genkey -noout -out update-key.pem`),
put its public half in `app/src/main/res/raw/update_key.pem`
(`openssl ec -in update-key.pem -pubout`), and point `Feed.DEFAULT` at your releases. R1s running
someone else's build won't take yours, and the other way round.

If the R1's OS isn't signed with AOSP's public key, ribbitOS isn't a platform app, can't install
silently, and says so in the log. Update it by hand with `adb install -r`.
