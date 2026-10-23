# ribbitOS

A free, open-source (MIT) replacement for rabbitOS on the Rabbit R1.

> **Status: early work in progress.** The first app, *ribbitOS lite*, is a text-only chat client
> for a self-hosted [OpenClaw](https://docs.openclaw.ai) agent. Voice comes later.

ribbitOS runs on LineageOS 21 (a GSI) on top of rabbit's own kernel and vendor partitions, which
the R1's side button needs. Everything in this repository is MIT-licensed source and scripts; it
contains no firmware. MediaTek's vendor firmware stays proprietary, as on any phone with this chip.

## What's here

| Path | What |
|---|---|
| `app/` | ribbitOS lite: the home screen app (Kotlin, no AndroidX, one dependency: OkHttp) |
| `tools/` | Backup, fastboot entry, stock restore and LineageOS flashing for the R1 |
| `docs/FASTBOOT.md` | Getting the R1 into fastboot (it has no volume buttons) |
| `docs/POWER_OF_TEN.md` | The coding rules and how the build enforces them |
| `config/detekt/` | Static-analysis config: every rule on, deviations listed with reasons |

## Extending it

The app talks to one interface, `Backend` (`app/src/main/java/dev/ribbitos/lite/Backend.kt`):
send a message, stream the reply, cancel. `OpenClawBackend` is the reference implementation. To
make ribbitOS talk to something else, implement `Backend` and point `Wiring.backendFactory` at it;
the screens don't change. Tests use the same seam to inject fakes.

## Building

JDK 17 and the Android SDK (platform 35, build-tools 35.0.0):

```sh
./gradlew check          # compile (warnings are errors), lint, detekt, tests, coverage gate
./gradlew assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
```

## Installing on an R1

Unlocking voids the R1's warranty, and a bad flash on a MediaTek device can brick it. Rabbit's
official route comes first: in OS3, **settings → r1** → developer mode, then **device
modification → unlock**.

1. Back up the R1 with `tools/backup.sh` (needs [mtkclient](https://github.com/bkerler/mtkclient)).
2. Enter fastboot: [docs/FASTBOOT.md](docs/FASTBOOT.md). Then `fastboot flashing unlock`.
3. Optional clean baseline: `tools/flash-stock.sh <unzipped rabbitOS v0.8.293>`.
4. Flash LineageOS 21 (Andy Yan's `arm64_bvN` GSI): `tools/flash-gsi.sh <gsi.img> <unzipped rabbitOS>`.
5. Install the app with `adb install`, then set the gateway on the device or over adb (see
   `ConfigReceiver.kt`).

On the OpenClaw side, enable `gateway.http.endpoints.chatCompletions.enabled`. The gateway token
is an operator credential: keep the gateway on your LAN or tailnet, never on the internet.

## Credits

ribbitOS adapts code from [robotOS](https://github.com/windoze95/robotOS) (MIT) and builds on
r1_escape, TurboTheTurtle's R1 notes, Andy Yan's GSIs and rabbit's developer program. Details and
license texts are in [NOTICE](NOTICE). Not affiliated with Rabbit Inc. or OpenClaw.
