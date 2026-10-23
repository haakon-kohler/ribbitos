# Power of Ten, adapted

ribbitOS follows Gerard Holzmann's ["Power of Ten" rules](https://spinroot.com/gerard/pdf/P10.pdf)
for safety-critical code, adapted from C to Kotlin on Android. Each rule is enforced by a tool
where one exists, and by review where none does. `./gradlew check` runs every tool below and
fails on any finding.

| # | Rule | Here | Enforced by |
|---|------|------|-------------|
| 1 | Simple control flow, no recursion | No recursion; guard clauses over nesting; cyclomatic complexity ≤ 10, nesting depth ≤ 3 | detekt `CyclomaticComplexMethod`, `NestedBlockDepth`, `ReturnCount`; review for recursion |
| 2 | Every loop has a fixed upper bound | Every loop is bounded by a constant in `Limits` (stream lines, reply size, loaded messages) | review; tests drive each bound (`streamIsCutAtTheLineCap`, `replyIsCutAtTheCapAndReadingStops`, `loadingIsCappedToo`) |
| 3 | No dynamic allocation after initialization | The JVM allocates freely, so the adaptation is bounded growth: nothing grows past a `Limits` constant | review; tests (`keepsOnlyTheNewestMessages`, `inputIsCappedAtTheLimit`) |
| 4 | Functions fit on a page | ≤ 40 lines (Kotlin is denser than C's 60) | detekt `LongMethod` |
| 5 | Assertions | `require` at every boundary that takes outside input; `check` for invariants; failures are tested | review; tests for each `require` |
| 6 | Smallest possible scope | `private` by default, `val` over `var`, no `lateinit` in app code | detekt `VarCouldBeVal`, `LateinitUsage`; lint `SyntheticAccessor` disabled because it pushes the other way |
| 7 | Check return values and parameters | No ignored results; parameters validated; no swallowed exceptions except the documented "not JSON" case | detekt `IgnoredReturnValue`, `SwallowedException`, `TooGenericExceptionCaught` |
| 8 | Limited preprocessor | No reflection, annotation processing or code generation; `BuildConfig` is off | build config; review |
| 9 | Restricted pointer use | No `!!`, no unchecked platform types, no casts that can fail | detekt `UnsafeCallOnNullableType`, `UnsafeCast`, `HasPlatformType` |
| 10 | All warnings on, zero warnings | Kotlin `allWarningsAsErrors`; Android lint `checkAllWarnings` + `warningsAsErrors`; detekt `allRules` with `maxIssues: 0`, including ktlint formatting; shellcheck with every optional check and ruff with every rule for `tools/` | the build; CI |

Every deviation is written next to the setting that makes it, with its reason; the full list of
those files is in [RULES.md](RULES.md#the-rules). CI (`.github/workflows/ci.yml`)
runs all of it on every push.

Coverage is gated too (Kover, `koverVerifyDebug`): 90 % of lines and 80 % of branches.
