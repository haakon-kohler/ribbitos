# ribbitOS

A free, open-source (MIT) replacement for rabbitOS on the Rabbit R1.

> **Status: early work in progress.** The first app, *ribbitOS lite*, is a text chat for the R1
> that talks to a self-hosted [OpenClaw](https://docs.openclaw.ai) agent, OpenAI, or Claude, with a
> built-in demo that needs no server. Voice, camera, and a lock screen come next.

ribbitOS runs on LineageOS 21 (a GSI) on top of rabbit's own kernel and vendor partitions, which
the R1's side button needs. Everything in this repository is MIT-licensed source and scripts; it
contains no firmware. MediaTek's vendor firmware stays proprietary, as on any phone with this chip.

## What's here

| Path | What |
|---|---|
| `app/` | ribbitOS lite: the home screen app (Kotlin, no AndroidX, OkHttp, plus Anthropic's Java SDK for Claude) |
| `tools/` | Backup, fastboot entry, stock restore and LineageOS flashing for the R1 |
| `docs/FASTBOOT.md` | Getting the R1 into fastboot (it has no volume buttons) |
| `docs/POWER_OF_TEN.md` | The coding rules and how the build enforces them |
| `docs/RULES.md` | Every development rule, and the file that enforces it |
| `config/detekt/` | Static-analysis config: every rule on, deviations listed with reasons |

## Extending it

The app talks to one interface, `Backend` (`app/src/main/java/dev/ribbitos/lite/Backend.kt`):
send a message, stream the reply, cancel. Four ship with it: `DemoBackend` (canned replies, the
smallest example), `OpenClawBackend`, `OpenAiBackend` (any server that speaks OpenAI's Chat
Completions API) and `ClaudeBackend` (Anthropic's official Java SDK). To add one, see
[docs/RULES.md](docs/RULES.md#adding-a-backend); the screens don't change. Tests use the same
seam to inject fakes.

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
5. Install the app with `adb install`. It starts in demo mode. Pick a provider in its settings, or
   set one over adb so you don't type a key on a 2.9-inch screen:
   ```sh
   adb shell am broadcast -a dev.ribbitos.lite.CONFIG -n dev.ribbitos.lite/.ConfigReceiver \
     --es provider claude --es token <your API key>        # or openai, openclaw (+ --es base_url)
   ```

For OpenClaw, enable `gateway.http.endpoints.chatCompletions.enabled` on the gateway. Its token is
an operator credential: keep the gateway on your LAN or tailnet, never on the internet.

## Credits

ribbitOS adapts code from [robotOS](https://github.com/windoze95/robotOS) (MIT) and builds on
r1_escape, TurboTheTurtle's R1 notes, Andy Yan's GSIs and rabbit's developer program. Details and
license texts are in [NOTICE](NOTICE). Not affiliated with Rabbit Inc. or OpenClaw.
