# ribbitOS 🐸

**A free, open-source replacement for rabbitOS on the Rabbit R1.**

Your R1, talking to the AI you choose: your own [OpenClaw](https://docs.openclaw.ai) agent, OpenAI,
or Claude. No rabbit account, no cloud in between, MIT-licensed.

<p>
  <img src="docs/images/chat.png" width="240" alt="ribbitOS chat screen on the R1">
  <img src="docs/images/settings.png" width="240" alt="Choosing who answers: Demo, OpenClaw, OpenAI or Claude">
  <img src="docs/images/power.png" width="240" alt="Holding the button brings up power off and restart">
</p>

> **v0.01-alpha.** Text chat works end to end on a real R1. Voice typing, sending a photo from the
> camera, and a lock screen are next, for v0.01. Not accepting contributions yet.

## Features

- **Pick who answers:** OpenClaw (self-hosted agent), OpenAI (or any server that speaks its Chat
  Completions API), Claude, or a built-in demo that needs no server at all.
- **Made for a 2.9-inch screen:** one conversation, replies stream in as they're written, a stop
  button, the scroll wheel scrolls, full screen with nothing else in the way.
- **Your keys stay on your R1.** Set them on the device, or push them from a computer over USB so
  nobody types an API key on a tiny keyboard.
- **Easy to extend:** one small `Backend` interface; adding a provider doesn't touch the screens.
- **Built strictly:** NASA "Power of Ten" rules, every lint check on with zero warnings, 150+ tests,
  all enforced in CI ([docs/RULES.md](docs/RULES.md)).

## Install

You need an R1, a computer with `adb`/`fastboot`, and about an hour. Unlocking voids the R1's
warranty, and a bad flash can brick it.

1. In rabbit's OS3: **settings → r1 →** developer mode, then **device modification → unlock**.
2. Back up the R1: `tools/backup.sh` (needs [mtkclient](https://github.com/bkerler/mtkclient)).
3. Get into fastboot ([docs/FASTBOOT.md](docs/FASTBOOT.md)), then `fastboot flashing unlock`.
4. Flash LineageOS 21 ([Andy Yan's](https://sourceforge.net/projects/andyyan-gsi/files/lineage-21-pre-qpr2-td/)
   `arm64_bvN` GSI): `tools/flash-gsi.sh <gsi.img> <unzipped stock rabbitOS v0.8.293>`.
5. Install ribbitOS from the [latest release](../../releases), make it the home app, and pick who
   answers:
   ```sh
   adb install ribbitos-lite.apk
   adb shell cmd package set-home-activity dev.ribbitos.lite/.HomeActivity
   adb shell am broadcast -a dev.ribbitos.lite.CONFIG -n dev.ribbitos.lite/.ConfigReceiver \
     --es provider claude --es token <your API key>     # or openai, or openclaw + --es base_url
   ```

## Build

JDK 17 and the Android SDK (platform 35):

```sh
./gradlew check assembleRelease
```

## Credits

Built on [robotOS](https://github.com/windoze95/robotOS) (MIT), r1_escape, TurboTheTurtle's R1 notes,
Andy Yan's LineageOS GSIs, and rabbit's developer program. Details in [NOTICE](NOTICE).
Not affiliated with Rabbit Inc., OpenAI, Anthropic or OpenClaw.

MIT License.
