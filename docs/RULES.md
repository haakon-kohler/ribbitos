# Development rules

The rules ribbitOS is built by, written down here and enforced by the build rather than remembered.
Outside contributions open with a later release. Every rule a machine can check runs in CI (`.github/workflows/ci.yml`) on every push.

## Before you push

```sh
./gradlew check assembleRelease          # the app: everything below marked "check"
shellcheck -x -P tools tools/*.sh        # the tools (.shellcheckrc)
ruff check tools/ && ruff format --check tools/   # the tools (ruff.toml)
```

## The rules

1. **Zero warnings.** Kotlin compiler warnings are errors; Android lint runs every check with
   warnings as errors; detekt runs every rule (formatting included) with zero issues allowed, both
   with and without type resolution; shellcheck and ruff run every rule. *(check, CI)*

2. **Every exception sits next to the setting it changes, with its reason.** Nothing is disabled
   silently. The exceptions live in exactly these files; read them before adding one:
   - `config/detekt/detekt.yml`: detekt thresholds and the one swallowed-exception allowance
   - `app/build.gradle.kts` (`lint {}`, `packaging {}`): lint checks that depend on today's date,
     the synthetic-accessor check, duplicate license files in dependencies
   - `app/lint.xml`: two dependency jars exempt from `InvalidPackage`
   - `app/proguard-rules.pro`: R8 keep rules for the Anthropic SDK
   - `ruff.toml`, `.shellcheckrc`: script linting

3. **NASA Power of Ten, adapted to Kotlin.** No recursion; every loop and every growing collection
   has a fixed upper bound from `Limits.kt`; short functions; inputs checked with `require`; no `!!`
   or `lateinit` in app code. The mapping, and which tool enforces each rule, is in
   [docs/POWER_OF_TEN.md](POWER_OF_TEN.md).

4. **Tests come with the change.** Network code is tested against fake servers (OkHttp's
   `MockWebServer`) speaking the real wire format, screens with Robolectric, and every `Limits`
   bound by a test that hits it. Coverage must stay at or above 90 % of lines and 80 % of branches
   (Kover). *(check)*

5. **Release builds are tested on an R1.** R8 shrinks release builds, and it can break code that
   other libraries reach by reflection (Jackson inside the Anthropic SDK did) in ways no JVM test
   sees. Before a release, install `app/build/outputs/apk/release/app-release.apk` on an R1 and send
   one message through each provider. An invalid key is enough: the provider's own "rejected the
   key" error proves the request was built, sent and its answer parsed.

6. **Pinned versions.** Dependencies use exact versions. GitHub Actions are pinned to full commit
   SHAs of releases that were at least two weeks old when pinned. Linters in CI use the exact versions
   in `ci.yml`, so a new upstream rule can't turn a green commit red unannounced.

7. **Nothing private in the repository.** No firmware, no device backups (they contain the IMEI,
   serial and hardware IDs), no API keys or tokens, no IP addresses or personal identifiers. Keys
   reach a device through its settings screen or over adb (`ConfigReceiver.kt`), never through the
   build.

8. **Licensing.** Everything here is MIT. Code adapted from elsewhere gets an entry in
   [NOTICE](../NOTICE) with its source and license text, and a one-line pointer in the file itself.

## Adding a backend

1. Implement `Backend` (`app/src/main/java/dev/ribbitos/lite/Backend.kt`). `DemoBackend` is the
   smallest complete example; `OpenAiBackend` shows a stateless API that needs the history
   (`History.forTurn`), `OpenClawBackend` a server that keeps it.
2. Add a `Provider` (with its default URL and model) in `Settings.kt`, and map it in
   `Wiring.defaultBackend`.
3. Add its button, labels and help text to the settings screen (`activity_settings.xml`,
   `SettingsActivity.TEXT`).
4. Test it against a fake server, including errors, cancel and the `Limits` bounds.
