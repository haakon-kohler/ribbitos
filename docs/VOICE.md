# Voice typing

Hold the mic next to the message box, speak, let go: Whisper transcribes on the R1 itself (no
network, nothing leaves the device) and the text lands in the box to edit and send.

- Engine: [whisper.cpp](https://github.com/ggml-org/whisper.cpp) v1.9.5, the pinned submodule in
  `third_party/whisper.cpp`, built into `libribbit_whisper.so` (`app/src/main/cpp/`), without OpenMP
  (`libomp.so` isn't on the device).
- Model: `ggml-tiny.en-q8_0.bin` (44 MB, English), from
  [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp). The build downloads it and
  refuses it unless its SHA-256 matches (`app/build.gradle.kts`); it ships inside the APK.

## Why tiny.en

Measured on a Rabbit R1 (MT6765: 4 × Cortex-A53 at 2.2 GHz + 4 at 1.6 GHz, ARMv8.0 without the
dot-product or half-precision extensions, 4 GB RAM) with whisper.cpp v1.9.5's `whisper-cli` on its
11-second `samples/jfk.wav`, total time including model load:

| Model | Size | 4 threads | 8 threads | Transcript |
|---|---|---|---|---|
| tiny.en q8_0 | 44 MB | 6.5 s | **4.4 s** | correct, one comma short |
| base.en q5_1 | 60 MB | 14.4 s | 8.9 s | exact |
| base.en q8_0 | 82 MB | 13.6 s | 8.6 s | exact |

Whisper always encodes a 30-second window, so a short phrase costs about the same as this clip.
base.en is twice as slow for the same words; on this CPU that wait is too long for typing. In the
app the model stays loaded after the first use, so real waits are a little shorter than these.

To try another model, change `WHISPER_MODEL` and `WHISPER_SHA256` in `app/build.gradle.kts`.
