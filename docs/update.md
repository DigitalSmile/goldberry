# Goldberry dependency update plan — 2026-10-10

**Repo:** https://github.com/DigitalSmile/goldberry
**Branch / commit pins were read from:** `master` @ `d0388dd43c30596a93560e49c822c77c56f33e81` (2026-10-08)
**Source of truth:** `gradle/libs.versions.toml`, plus `gradle/wrapper/gradle-wrapper.properties`,
`.github/workflows/showcase.yml` (GraalVM) and `assets/src/main/java/dev/goldberry/assets/prepare/Asset.java`
(the asset manifest — version **and** SHA-256, per ADR-0033).

**Summary:** 39 pinned dependencies checked; 18 are behind. One is a security fix (Logback, CVE-2026-104721).
HarfBuzz 14.6.0 changes no public C API at all, and SDL3 3.4.18 changes no struct or enum that Goldberry
actually binds — both verified against the headers, so the two native bumps are lower-risk than usual.
Lucide is 2 years and ~85 releases behind and needs a re-vendored licence file.

> **No jextract step anywhere in this plan.** Bindings are hand-written (ADR-0006 superseded by ADR-0010);
> what guards header drift is the run-time layout probe (`natives/.../layout/LayoutProbe.java`,
> `Layouts.java`), per ADR-0029. Where a header moved, the instruction below is to re-check the probe table,
> not to regenerate anything.

---

## Updates

| Dependency | TOML key / file | Current → target | Verdict | Release notes |
|---|---|---|---|---|
| Logback | `logback` | 1.6.3 → **1.6.5** | UPDATE NOW | [1.6.5](https://github.com/qos-ch/logback/releases/tag/v_1.6.5), [1.6.4](https://github.com/qos-ch/logback/releases/tag/v_1.6.4) |
| HarfBuzz | `harfbuzz` | 14.5.0 → **14.6.0** | SHOULD UPDATE | [NEWS](https://raw.githubusercontent.com/harfbuzz/harfbuzz/main/NEWS) |
| SDL3 | `sdl3` | `release-3.4.16` → **`release-3.4.18`** | SHOULD UPDATE | [tag](https://github.com/libsdl-org/SDL/releases/tag/release-3.4.18) |
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.properties` | 9.7.1 → **9.8.1** | SHOULD UPDATE | [9.8.0](https://docs.gradle.org/9.8.0/release-notes.html) |
| ArchUnit | `archunit` | 1.5.0 → **1.5.1** | SHOULD UPDATE | [v1.5.1](https://github.com/TNG/ArchUnit/releases/tag/v1.5.1) |
| Spotless (Gradle plugin) | `spotless` | 8.10.2 → **8.10.4** 🆕 | SHOULD UPDATE | [CHANGES](https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md) |
| NullAway | `nullaway` | 0.14.1 → **0.14.2** | SHOULD UPDATE | [CHANGELOG](https://github.com/uber/NullAway/blob/master/CHANGELOG.md) |
| PMD | `pmd` | 7.27.0 → **7.28.0** | SHOULD UPDATE | [7.28.0 notes](https://github.com/pmd/pmd/blob/pmd_releases/7.28.0/docs/pages/release_notes.md) |
| SLF4J | `slf4j` | 2.0.19 → **2.0.20** | OPTIONAL | [news](https://slf4j.org/news.html) |
| SpotBugs Gradle plugin | `spotbugsPlugin` | 6.5.11 → **6.5.12** | OPTIONAL | [6.5.12](https://github.com/spotbugs/spotbugs-gradle-plugin/releases/tag/6.5.12) |
| jqwik | `jqwik` | 1.9.3 → **1.10.1** | OPTIONAL | [release notes](https://jqwik.net/release-notes.html) |
| palantir-java-format | `palantirFormat` | 2.98.0 → **2.102.0** | OPTIONAL | [releases](https://github.com/palantir/palantir-java-format/releases) |
| GraalVM CE | `.github/workflows/showcase.yml` (`version:`) | 25.3 → **25.4** | OPTIONAL | [release calendar](https://www.graalvm.org/release-calendar/) |
| Lucide | `Asset.LUCIDE` in `Asset.java` | 0.469.0 → **1.54.0** 🆕 | OPTIONAL | [1.0.0](https://github.com/lucide-icons/lucide/releases/tag/1.0.0), [1.54.0](https://github.com/lucide-icons/lucide/releases/tag/1.54.0) |

🆕 = released in the last 48 hours.

---

## Batch 1 — Security (one commit / PR)

### Step 1.1 — Logback 1.6.3 → 1.6.5

**File:** `gradle/libs.versions.toml`

```diff
-logback = "1.6.3"
+logback = "1.6.5"
```

**Why now.** 1.6.5 fixes **CVE-2026-104721** (related to CVE-2026-19880). The 1.6.3 fix stripped slashes
from MDC values but was insufficient: values could still carry path-traversal sequences, variable-like
references, and characters special to file-name patterns and e-mail addresses. **The pinned 1.6.3 is the
version that fix was incomplete in.**

**Blast radius is small, and that is worth stating in the commit message.** Logback reaches only the
showcase — `libs.versions.toml` itself records that the toolkit binds no backend and that Logback is
`:example`'s choice. The CVE is reachable through `MDCBasedDiscriminator` / `SiftingAppender`; verify the
showcase uses neither (`grep -rn "SiftingAppender\|MDCBasedDiscriminator" example/`), in which case this is
hygiene rather than an exposure — but it is a one-line bump, so take it.

**Behaviour changes to be aware of (1.6.4 + 1.6.5):**

- `MDCBasedDiscriminator` now **rejects** bad MDC values instead of stripping characters (empty, over 64
  characters, containing `..`, or certain special characters), falling back to the `DefaultValue` property
  and logging rate-limited warnings.
- With compression enabled, `TimeBasedRollingPolicy` / `SizeAndTimeBasedRollingPolicy` now also delete old
  **uncompressed** files that `maxHistory` previously ignored.
- `Encoder` gains an `isStateful()` default method (returns `false`); stateless encoders unaffected.
- Variable substitution in the `scan` attribute of `<configuration>` works again (regressed in 1.5.27).
- `SimpleInvocationGate` (deprecated in 1.6.3) is now marked for removal — use `FixedIntervalInvocationGate`.
- `LogbackMDCAdapterSimple` was removed; `LogbackMDCAdapter` remains the default.
- Half-day rollover for date patterns with an AM/PM marker (`%d{yyyy-MM-dd-a}`).

**Code to touch:** none expected. Check `example/src/main/resources/logback*.xml` for
`SimpleInvocationGate` and for any `scan` attribute that relies on the old (broken) non-substituting
behaviour.

**Commit message:**
```
build(deps): bump Logback 1.6.3 → 1.6.5 for CVE-2026-104721
```

---

## Batch 2 — Java build and test tooling

Four independent steps. 2.1–2.4 are safe together in one commit; **2.5 (palantir-java-format) must be its
own commit** because it rewrites files.

### Step 2.1 — Gradle wrapper 9.7.1 → 9.8.1

**File:** `gradle/wrapper/gradle-wrapper.properties`

```diff
-distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
+distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.1-bin.zip
```

Do this with `./gradlew wrapper --gradle-version 9.8.1` rather than by hand, so the wrapper jar, script and
checksum move together. `validateDistributionUrl=true` is already set, so leave it.

**9.8.0 (2026-09-24) + 9.8.1 (2026-10-07):** Java 27 support for the daemon and toolchains; Maven mirror
reuse via `org.gradle.mirror.maven.settings` (off by default); clickable problem locations and wider
Problems API coverage; lazy `destinationDirectory` on `Copy`/`Sync` (incubating); `GenerateMavenPom` now
participates in up-to-date checks (**disabled when a `withXml` action is registered** — verify whether the
publishing setup registers one, since that would silently opt out of the new up-to-date checking); up to 45%
faster builds on Windows machines with slow system clocks. No breaking changes listed. Nothing about Java 25
specifically — the toolchain floor is unaffected.

### Step 2.2 — ArchUnit 1.5.0 → 1.5.1

```diff
-archunit = "1.5.0"
+archunit = "1.5.1"
```

Bug-fix release (2026-09-25). `java.lang.IO` is now included in `ACCESS_STANDARD_STREAMS`, and **caught
exception types now count as type dependencies**. The second one is the live risk: Goldberry's ArchUnit
rules assert the module graph and the native boundary, so a rule phrased over type dependencies may now see
edges it did not see before — a `catch (SomeNativeException e)` in a module that was not previously
considered to depend on the exception's package will now register. Expect possible new violations in the
module-graph rules and either widen the rule or fix the edge.

### Step 2.3 — NullAway 0.14.1 → 0.14.2

```diff
-nullaway = "0.14.1"
+nullaway = "0.14.2"
```

Released 2026-09-24. Adds `JSpecifyUnrecognizedAnnotationLocation` — **opt-in, so it does not fire unless
enabled.** Also: NullAway now more precisely detects whether the build JDK supports reading type-use
annotations from bytecode, which *"may lead NullAway to crash on certain build configurations that were
unsupported before but we were not detecting precisely"* — on JDK 25 with JSpecify mode this is the one to
watch. Further JSpecify-mode fixes around wildcard bounds and multi-dimensional arrays, plus less
conservative handling of raw array types; both can move findings in either direction.

If new findings appear, `-Pgoldberry.nullaway=warn` demotes them for a sweep (already a documented
property). Upstream also ships a suppression-remover script worth running after the bump to retire
suppressions the improvements made unnecessary.

### Step 2.4 — PMD 7.27.0 → 7.28.0

```diff
-pmd = "7.27.0"
+pmd = "7.28.0"
```

**Verified low-risk.** `goldberry.java-conventions.gradle` sets `ruleSets = []` and points
`ruleSetFiles` at `config/pmd/ruleset.xml`, a hand-picked list of 8 rules. 7.28.0's five new Java rules
(`OnDemandImport`, `LongLiteralEndingWithLowercaseL`, `TypeNameMismatch`, `CStyleArrayDeclaration`,
`InternalApiUsage`) therefore **do not fire**, and neither changed rule (`NonThreadSafeSingleton`,
`VariableCanBeInlined`) is in the ruleset. None of the 8 rules Goldberry does run
(`UnconditionalIfStatement`, `EmptyCatchBlock`, `JumbledIncrementer`, `MisplacedNullCheck`,
`BrokenNullCheck`, `OverrideBothEqualsAndHashcode`, `StringInstantiation`, `InefficientStringBuffering`)
is mentioned in the 7.28.0 notes. The API changes are all in the experimental Kotlin module.

Expect zero change in findings. `ignoreFailures = false`, so if that is wrong, `./gradlew pmdMain` says so
immediately.

### Step 2.5 — Spotless 8.10.2 → 8.10.4 (own commit)

```diff
-spotless = "8.10.2"
+spotless = "8.10.4"
```

🆕 8.10.4 landed 2026-10-08.

**What does *not* apply, checked against the config:** Goldberry's `spotless { java { ... } }` block uses
only `trimTrailingWhitespace()`, `endWithNewline()` and the custom
`KeepDocReferencedImports.palantir(catalog…'palantirFormat')` step. So:

- the `versionCatalog()` lint changes in 8.10.3 (which "may expose catalog errors that previously caused
  silent data loss") **do not apply** — that step is not used;
- the default `palantir-java-format` bump to 2.102.0 **does not apply** — the version is passed explicitly
  from the catalog, which is why `palantirFormat` is a separate step below;
- the removal of the legacy `com.diffplug.gradle.spotless` plugin marker **does not apply** — the
  conventions plugin already applies `com.diffplug.spotless`.

**What does apply:** two fixes to `shortenFullyQualifiedTypes` and `expandWildcardImports` — neither step is
configured here, so the practical effect should be nil. Treat any file Spotless rewrites as a surprise worth
reading rather than committing blind.

**Follow-up:** run `./gradlew spotlessApply` and inspect the diff. If it is empty, that confirms the
analysis above.

### Step 2.6 — SLF4J 2.0.19 → 2.0.20 (OPTIONAL)

```diff
-slf4j = "2.0.19"
+slf4j = "2.0.20"
```

Released 2026-09-22. `Marker` instances are slated to become immutable in a future release, so the
add/remove-children methods on `Marker` are now **deprecated**. SLF4J is Goldberry's only compile-time
logging dependency and `-Werror` is on for `:assets` (and `-Xlint:all` broadly), so a deprecation warning
could become an error wherever the toolkit touches those methods. Check first:

```sh
grep -rn "Marker" --include=*.java core/ common/ widgets/ html/ media/ natives/ emoji/ gpu/ toolkit/
```

If nothing calls `Marker.add`/`remove`, this is a free bump.

### Step 2.7 — SpotBugs Gradle plugin 6.5.11 → 6.5.12 (OPTIONAL)

```diff
-spotbugsPlugin = "6.5.11"
+spotbugsPlugin = "6.5.12"
```

Released 2026-09-26. The only change is that the plugin's own ArchUnit test dependency moved to 1.5.1 — no
behaviour change for consumers. Note `spotbugs` (the analyser, 4.10.4) is already current and versions
independently, as the catalog comment says. SpotBugs reports and does not gate, so this is cosmetic.

### Step 2.8 — jqwik 1.9.3 → 1.10.1 (OPTIONAL)

```diff
-jqwik = "1.9.3"
+jqwik = "1.10.1"
```

**Skip 1.10.0** — upstream's own release notes say it should no longer be used. 1.10.1 (2026-05-29) adds
UTF-8 whitespace handling to the `@NotBlank` configurator and upgrades to Kotlin 2.3.21 and **JUnit Platform
1.14.4**.

The thing to understand before taking this: jqwik 1.10.1 is *"probably the last jqwik release on JUnit
Platform 1.x"*, and future releases will move to JUnit Platform 6 and require Java 21+. Goldberry is on
`junit = "6.1.3"`, so Gradle's conflict resolution already lifts jqwik's platform dependency to 6.x — which
is how 1.9.3 works today. Nothing changes about that here; the real alignment arrives with the next jqwik.

**verify:** after the bump, confirm both engines still register —
`./gradlew :core:test --info 2>&1 | grep -i "jqwik\|jupiter"` should show both discovered. The catalog
comment about `junit-platform-launcher` mattering "because jqwik is a second JUnit engine rather than a
library" is exactly the thing to re-check.

Upstream also added a user-guide section discouraging use of jqwik with AI coding agents, on the grounds
that its stdout output may confuse them. No technical effect; mentioned so it is not a surprise in the
notes.

### Step 2.9 — palantir-java-format 2.98.0 → 2.102.0 (OPTIONAL, own commit, rewrites files)

```diff
-palantirFormat = "2.98.0"
+palantirFormat = "2.102.0"
```

**This one reformats the codebase.** 2.99.0 (2026-09-23) changed output: *"Don't break line when switch
expression used in assignment."* Goldberry's painters and CSS machinery use switch expressions heavily, so
expect a real diff. 2.100.0 (2026-09-28) allowed Java Format without Spotless; 2.101.0 (2026-10-01) had no
documented user-facing changes; 2.102.0 (2026-10-05) only disabled GPG signing in a test repository. No
breaking changes documented in any of the four.

**Required follow-up — do not skip this.** `goldberry.java-conventions.gradle` carries
`palantirCannotFormat`, a list of 19 files the formatter writes invalid Java for, and its own doc comment
says the list *"is re-derived when the formatter is upgraded rather than trusted for ever"*. The reason it
exists is that **2.97.0 corrupted JDK 23+ `///` markdown javadoc** by wrapping a long `///` line without
re-emitting the prefix, turning a doc comment into a statement. So:

1. Bump the version.
2. Empty `palantirCannotFormat` (or comment it out).
3. `./gradlew spotlessApply --continue`.
4. `./gradlew compileJava` on every module. Re-derive the exclusion list from exactly which files now fail
   to compile — the same method the comment describes.
5. Restore `palantirCannotFormat` to that derived set, with the count in the surrounding prose updated if it
   changed (the comment currently says "all but nineteen of them").
6. `./gradlew spotlessCheck` must pass, which is what says the list is right.

If the list grows, the `///`-wrapping bug is back and this step should be abandoned rather than worked
around; 2.98.0 is a fine place to stay.

**Commit messages for this batch:**
```
build(deps): bump Gradle wrapper 9.7.1 → 9.8.1
build(deps): bump ArchUnit 1.5.0 → 1.5.1, NullAway 0.14.1 → 0.14.2, PMD 7.27.0 → 7.28.0
build(deps): bump Spotless 8.10.2 → 8.10.4
build(deps): bump SLF4J 2.0.19 → 2.0.20, SpotBugs plugin 6.5.11 → 6.5.12
build(deps): bump jqwik 1.9.3 → 1.10.1
style(deps): bump palantir-java-format 2.98.0 → 2.102.0 and reformat
```

---

## Batch 3 — Native libraries

Both are superbuild pins. `natives/src/main/cmake/CMakeLists.txt` reads `libs.versions.toml` directly and
the CI workflows pass no refs, so **edit the catalog and nothing else**, then rebuild from a clean `_deps`:

```sh
./gradlew :natives:cleanNativeDeps
./gradlew :natives:checkPinnedRefs    # fails if a second copy of any ref reappeared
./gradlew :natives:cmakeBuild
```

### Step 3.1 — HarfBuzz 14.5.0 → 14.6.0

```diff
-harfbuzz = "14.5.0"
+harfbuzz = "14.6.0"
```

**14.6.0 — 2026-10-05. 14.5.1 — 2026-10-01.** Both are included in this move.

**The public C API does not change.** `git diff 14.5.0..14.6.0 -- 'src/hb*.h'` touches **only
`src/hb-version.h`** (2 insertions, 2 deletions). No new, removed or changed function, struct or enum in any
public header. So: no hand-written binding changes, no FFM layout changes, no layout-probe table changes, no
native-image metadata changes.

**What is actually fixed, that matters here:**

- **14.6.0: "Fix shaping failures caused by out-of-range glyph IDs, a regression from 11.4.0."** This is the
  headline for Goldberry — a shaping-path correctness fix on the main text stack.
- 14.5.1: signed-integer overflows fixed when **scaling glyph extents** and when applying **synthetic slant
  and emboldening** — all three are paths the toolkit's text stack uses, and the fix is security-adjacent
  hardening on malformed input.
- 14.5.1: heap buffer overflow with malicious `COLR` fonts in the **experimental GPU library**, and a memory
  leak in the **experimental WebAssembly shaper** — neither is built here, so neither is an exposure; listed
  for completeness.
- 14.5.1: deadlock in `hb_font_destroy()` after `hb_ft_font_set_funcs()` — FreeType path, not used.
- 14.6.0: `DMAP` table support; `FeatureVariations` 1.1 lookup variations; `VARC` moved to the revised
  variation-store format (**upstream states the new format is incompatible with the previous one**) and
  various `VARC` fixes. `VARC` affects only fonts carrying that table — Inter, JetBrains Mono and
  Noto-COLRv1 do not — so no effect on the bundled faces.
- Various fixes for malformed fonts, and subsetting improvements (subsetting is not used here).

**Required follow-up:** shaping output can change — that is the point of the out-of-range-glyph-ID fix. Run
the golden tests before blessing anything, and read the diffs rather than blessing on sight:

```sh
./gradlew test                                   # golden comparisons across core/, widgets/, html/, example/
./gradlew blessGoldens -Pgoldberry.golden.update=true   # only after reading the failures
```

Goldens live in `core/`, `widgets/`, `html/`, `example/`, `gpu/` and `media/`
`src/test/resources/golden`. One set serves all platforms with an antialiasing tolerance, so a genuine
shaping change shows up as a real difference rather than platform noise.

**Licence:** unchanged (MIT "Old MIT"). No `licenses/harfbuzz.txt` or `NOTICE` edit needed, but
`./gradlew checkLicenses` confirms.

### Step 3.2 — SDL3 `release-3.4.16` → `release-3.4.18`

```diff
-sdl3 = "release-3.4.16"
+sdl3 = "release-3.4.18"
```

**3.4.18 — 2026-10-02** (3.4.16 was 2026-09-02). Odd micro versions are development-only in SDL, so 3.4.18
is the next release and the current stable; 3.5.0 exists on `main` but **is not released** — there is no
`release-3.5.x` tag.

**Header changes, and what each means for Goldberry — all verified against the bindings:**

- **5 pen event structs gained a field.** `SDL_PenProximityEvent`, `SDL_PenMotionEvent`,
  `SDL_PenTouchEvent`, `SDL_PenButtonEvent` and `SDL_PenAxisEvent` each gained
  `SDL_PenDeviceType device_type` ("added in 3.4.18"). **No action: Goldberry binds no pen event struct**
  (nothing under `natives/` names any of the five). `SDL_PenDeviceType` and `SDL_GetPenDeviceType()`
  already existed at 3.4.16, so this is a field addition, not a new type.
- **`SDL_Event` size is unchanged.** The union is still explicitly padded to `Uint8 padding[128]` in both
  tags, with the upstream `SDL_COMPILE_TIME_ASSERT` still in place. `Layouts.SDL_EVENT` models it as sixteen
  longs = 128 bytes, so the layout probe stays correct. This is the one thing that *could* have broken from
  the pen-struct growth, and it did not.
- **`SDL_GAMEPAD_TYPE_STEAM` enumerator added.** No action: `GAMEPAD_TYPE` appears nowhere in the Java
  sources.
- **`SDL_SCANCODE_FRONT = 165` and `SDLK_FRONT` added** (Sun keyboards). `Key.java` maps SDL scancodes;
  a new scancode it does not know simply goes unmapped. **verify:** confirm `Key.java`'s mapping handles an
  unknown scancode by falling through rather than throwing, and decide whether `FRONT` is worth mapping
  (probably not).
- `SDL_gpu.h`: documentation only — the multisample-texture wording changed where
  `GPU: Allow multisample textures to be read` landed. **verify** against `:gpu`'s sampler/texture binding
  whether that relaxation is worth using; nothing breaks if it is ignored.
- `SDL_init.h`: `SDL_PROP_APP_METADATA_NAME_STRING` documentation changed — SDL now picks the binary's name
  as a default instead of always "SDL Application". Cosmetic, but it changes what a Wayland/X11 volume
  applet shows if the app sets no metadata name.
- `SDL_assert.h`: MinGW `__debugbreak` guard. `SDL_main_impl.h`: wiki-category comment removed.

**The fixes worth having (73 commits), grouped by what Goldberry depends on:**

- **Linux / Wayland:** don't use a stale scaling factor (DPI correctness); don't defer clipboard source
  submission when no serial is available; send eraser info in pen events.
- **Linux / X11:** clear internal selection data when updating the clipboard; ignore `BadWindow` when
  handling a `SelectionRequest`; handle `XTranslateCoordinates` failing during a reparenting operation;
  register IM destruction/instantiation callbacks; rescan relative valuators and update all device info on
  `XI_DeviceChanged`; don't suppress slave mouse buttons when lacking keyboard focus.
- **macOS:** fixed a Metal ARC leak when freeing GPU window data; clear a bogus pointer when getting a new
  drawable fails; command buffer now holds a reference to its fence; read the fence in
  `SubmitAndAcquireFence` while `submitLock` is held; Steam Controller no longer appears twice on macOS 27.
- **Windows:** fixed a crash if a broken `EZFRD64.DLL` is installed; D3D12 fence read under `submitLock`;
  drain `ID3D12InfoQueue` for log messages when `ID3D12InfoQueue1` is unsupported; fixed multi-layer texture
  fallback upload alignment.
- **Vulkan:** fixed a validation error in `SDL_RenderReadPixels()`; fixed a `commandPool` leak on window
  resize; opt-in device extensions.
- **kmsdrm:** try a supported surface format instead of demanding `ARGB8888`.
- **CMake:** marked compatible with CMake 4.4; `lld-link` argument-error detection.

The clipboard, DPI and Metal/Vulkan-leak fixes are the reason to take this: all three are in paths the
backend SPI uses on every platform.

**Required follow-up:**
- Re-run the layout probe against the rebuilt library — it runs at start-up on every target, so
  `./gradlew :natives:test -Dgoldberry.native.required=true` is the check.
- No `nativeImageMetadata` change expected (no new symbol is bound).
- Re-run the goldens: nothing here should change rendering, but the kmsdrm format change and the D3D12
  alignment fix touch presentation paths.
- **Licence:** unchanged (Zlib). `./gradlew checkLicenses`.

**Commit messages:**
```
build(deps): bump HarfBuzz 14.5.0 → 14.6.0
build(deps): bump SDL3 release-3.4.16 → release-3.4.18
```

---

## Batch 4 — Toolchain

### Step 4.1 — GraalVM CE 25.3 → 25.4 (OPTIONAL)

**File:** `.github/workflows/showcase.yml`

```diff
       - uses: graalvm/setup-graalvm@v1
         with:
-          version: '25.3'
+          version: '25.4'
           java-version: '25'
           distribution: graalvm-community
```

GraalVM's current feature release is **25.4.4.1.1, 2026-09-22**; the pinned 25.3 line is 25.3.4.1
(2026-08-25). The next feature release, 25.5.5.1, is scheduled for 2026-10-27 — if this bump is not urgent,
waiting ~2 weeks and taking 25.5 in one move is reasonable.

Keep `java-version: '25'` and keep the pin by *GraalVM* version rather than Java version, exactly as the
existing comment in the workflow explains.

**verify:** that `setup-graalvm` resolves `'25.4'` for `distribution: graalvm-community`. The workflow's own
comment notes the community builds are tagged `graal-25.x` since 25.1, and the tag naming in `oracle/graal`
is inconsistent enough (`vm-25.3.4.1`, `vm-25.4.4.1.1`) that this deserves a CI run rather than trust. If
`'25.4'` does not resolve, try `'25.4.4.1.1'`.

**Required follow-up:** this is a native-image toolchain move, so the showcase image must be rebuilt and run
on **all three platforms** — that is the whole point of the `showcase.yml` matrix. Watch for:
- `:example:nativeImage` still succeeding with `libgoldberry` linked in;
- reachability metadata still complete (`./gradlew :example:nativeImageMetadata`);
- the `cannot find -lz` failure mode the native-image docs call out, if the runner images moved too.

There is no security driver here and no API change in Goldberry's own code, which is why this is OPTIONAL
rather than SHOULD UPDATE.

**Commit message:**
```
build(deps): bump GraalVM CE 25.3 → 25.4 in showcase workflow
```

---

## Batch 5 — Assets (riskiest; own PR)

### Step 5.1 — Lucide 0.469.0 → 1.54.0 (OPTIONAL)

**File:** `assets/src/main/java/dev/goldberry/assets/prepare/Asset.java`, the `LUCIDE` constant
(around line 236). Per ADR-0033 the manifest pins version **and** SHA-256, so three literals move together.

```diff
     public static final Asset LUCIDE = new Asset(
             …
-            "0.469.0",
-            "https://github.com/lucide-icons/lucide/releases/download/0.469.0/lucide-icons-0.469.0.zip",
+            "1.54.0",
+            "https://github.com/lucide-icons/lucide/releases/download/1.54.0/lucide-icons-1.54.0.zip",
             …
-            "a1f58d08afa0f7c12a9e6eb92814b74c6fb763eeb92bf62ead5b38bb7770de7f",
+            "807a25ed9b525bd15268cf5d10f97996262a9c6c06962f3fec50078087422a57",
             …
-            "https://raw.githubusercontent.com/lucide-icons/lucide/0.469.0/LICENSE",
+            "https://raw.githubusercontent.com/lucide-icons/lucide/1.54.0/LICENSE",
```

**The SHA-256 above was computed from the actual downloaded archive** (1 407 484 bytes), not copied from
upstream. The old value matches the `a1f58d08…` literal already in `Asset.java`, which confirms the right
field.

**The pin is 2026-10-08's release against 2024-12-20's — ~85 releases and a major version.**

**Verified facts about the archive, so the risk is known rather than guessed:**

| | 0.469.0 (pinned) | 1.54.0 |
|---|---|---|
| SVG icons | 1 544 | **1 870** |
| Archive layout | `icons/<name>.svg` + `icons/<name>.json` | same |
| `viewBox` | all `0 0 24 24` | **all `0 0 24 24`** |
| `stroke-width` | 2 | **2 on all 1 870** |
| `stroke-linecap` | round | **round on all 1 870** |
| `transform=` | none | **none** |
| `<g>` groups | none | **none** |
| Shape primitives | 7 | **same 7** (`path`, `circle`, `rect`, `line`, `ellipse`, `polyline`, `polygon`) |
| `fill="currentColor"` | 7 icons | 10 icons |

So **ADR-0033's uniformity invariant still holds** and `IconCompiler`'s "an unconvertible icon fails the
build" rule should not trip. Note the pinned archive *already* contains 7 filled icons (`tag`, `tags`,
`vault`, `palette`, `guitar`, …) and the build is green, so the 3 newly-filled ones
(`chart-scatter`, `galaxy`, `images`, `key-round`, `tag-plus`, `tag-x`) are not a new class of input — the
ADR's "no fills" prose is looser than the converter's actual behaviour.

**Breaking change: Lucide 1.0 (2026-03-23) removed all brand icons and renamed others — 74 names are gone.**
**Checked: none of them is referenced anywhere in Goldberry.** The 22 icon names the repo actually uses
(`Icon.bundled("…")`, `BundledAssets.icon("…")`, `icon="…"` in KDL) are `check`, `circle`, `circle-alert`,
`file`, `folder`, `footprints`, `layout-dashboard`, `list`, `map`, `palette`, `plus`, `save`, `square`,
`tag`, `trash`, `type` — all present in 1.54.0 — plus the deliberate test negatives
`definitely-not-an-icon` and `no-such-icon`. (`git-commit`, `grid` and `home` appear in prose only and are
absent from the pinned archive too.)

The full removed set, for a re-grep after any rebase:

```
  album, align-center, align-justify, align-left, align-right, angry, annoyed, badge-help, book-marked, building-2
  chrome, circle-help, codepen, codesandbox, dribbble, facebook, figma, file-audio, file-audio-2, file-badge-2
  file-check-2, file-code-2, file-json, file-json-2, file-key-2, file-lock-2, file-minus-2, file-plus-2, file-question, file-search-2
  file-type-2, file-video, file-video-2, file-volume-2, file-warning, file-x-2, filter, filter-x, fingerprint, flip-horizontal
  flip-horizontal-2, flip-vertical, flip-vertical-2, framer, frown, github, gitlab, grab, history, indent-decrease
  indent-increase, instagram, laugh, letter-text, linkedin, mail-question, meh, message-circle-question, pocket, podcast
  rail-symbol, shield-question, slack, smile, smile-plus, text, text-select, trash-2, trello, twitch
  twitter, waves, wrap-text, youtube
```

**Required follow-up — the licence changed, and `checkLicenses` will catch it.**
`licenses/lucide.txt` is vendored verbatim from the revision actually shipped (ADR-0015). The ISC licence is
intact, but the file is **not** identical:

- the copyright line changed from
  *"Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT). All
  other copyright (c) for Lucide are held by Lucide Contributors 2022."*
  to *"Copyright (c) 2026 Lucide Icons and Contributors"*;
- a new appended section lists ~115 Feather-derived icons by name and reproduces the **full MIT licence
  text** for them, under *"Copyright (c) 2013-present Cole Bemis"*.

So:

1. `./gradlew vendorLicences` to rewrite `licenses/lucide.txt` from the new tag (it is a deliberate manual
   task, not part of `build`).
2. Confirm `licenses/lucide.txt` now carries both the ISC block and the appended MIT block.
3. **verify** whether `THIRD-PARTY-NOTICES.md`'s Lucide row and `NOTICE`'s `Lucide  ISC license` line should
   now say "ISC, with MIT for Feather-derived icons". `NoticeDisclosureTest` in `build-logic` only checks
   that the component is *named*, so it will not fail — but the disclosure would be understating the
   licence, which is the kind of thing ADR-0015 exists to prevent.
4. `./gradlew checkLicenses` and `./gradlew checkLicenses -Pgoldberry.releaseCheck=true`.

**Other follow-ups:**
- `./gradlew prepareAssets` (or `:core:prepareAssets` via the `assetTool.bundle('inter,jetbrains-mono,lucide', null)`
  wiring) to re-download and recompile the icon table. The cache keys on the checksum, so the new archive is
  fetched automatically.
- The showcase's Icons sheet groups icons from Lucide's per-icon category JSON via `PrepareCatalogs`; 326
  more icons will change the sheet's contents and layout. **Re-bless `example/src/test/resources/golden`**
  for `IconsScreenTest` / `IconSheet`, and expect `:example`'s reflow test
  (ADR-0309, "a sheet of icons reflows, and pays for it") to need new references.
- Stale doc comments: `1544` is hard-coded in prose in `SvgShapes.java`, `SvgShapesTest.java`,
  `SvgPathData.java` (*"481 of the 1544"* — that ratio changes too), `CatalogCompiler.java`,
  `IconCompiler.java`, `PrepareAssets.java` and `Asset.java`. None is an assertion, so nothing fails, but
  this codebase's comments are precise and should be updated to 1 870 in the same commit.
- `core/src/test/java/dev/goldberry/icon/IconFindTest.java` and `IconPaintTest.java` use names that still
  exist; re-run them anyway.

**Why OPTIONAL rather than SHOULD UPDATE:** nothing is broken today, no security content, and the benefit is
326 additional icons. But the pin is two years stale and the work is now fully scoped, so it is a good
candidate for the next quiet PR.

**Commit message:**
```
build(deps): bump Lucide 0.469.0 → 1.54.0 and re-vendor its licence
```

---

## Verification

Run per batch, not once at the end.

```sh
# Java-only, fastest signal — no native toolchain needed
./gradlew build -Pgoldberry.skipNative=true

# Everything, including libgoldberry for this machine
./gradlew build

# Static analysis gates individually, when a batch-2 step is suspected
./gradlew spotlessCheck
./gradlew spotlessApply          # then read the diff
./gradlew pmdMain
./gradlew compileJava            # the Error Prone / NullAway gate

# Native superbuild from scratch, after any batch-3 pin moves
./gradlew :natives:checkToolchain
./gradlew :natives:cleanNativeDeps
./gradlew :natives:checkPinnedRefs
./gradlew :natives:cmakeBuild

# Prove the rebuilt library loads and the layout probe passes on this target
./gradlew :natives:test -Dgoldberry.native.required=true

# Golden / visual tests (core, widgets, html, example, gpu, media)
./gradlew test
./gradlew blessGoldens -Pgoldberry.golden.update=true   # only after reading each failure

# Licences, after batch 3 or 5
./gradlew checkLicenses
./gradlew checkLicenses -Pgoldberry.releaseCheck=true

# Assets, after batch 5
./gradlew prepareAssets
./gradlew vendorLicences

# Native image, after batch 4 (and worth one run after batch 3)
./gradlew :example:nativeImage -Pgraalvm.home=/path/to/graalvm
./example/build/native/goldberry-showcase-linux-x64
./gradlew :example:nativeImageMetadata

# The showcase, headless
./gradlew run -Pgoldberry.example.frames=3 -Pgoldberry.backend.videoDriver=dummy
```

**Check by hand:**

- **Golden diffs, one by one.** HarfBuzz 14.6.0 fixes a shaping regression, so some change is expected and
  legitimate — but read every diff before `blessGoldens`. One blessed-by-accident regression is worse than
  a red build.
- **The layout probe's own output** after the SDL bump — it is what catches a drifted struct or enumerator
  (ADR-0029), and it should pass unchanged given the analysis above. If it fails, the pen-struct or
  `SDL_Event` reasoning in step 3.2 was wrong and should be re-derived from the headers.
- **Clipboard and DPI by hand on Wayland and X11.** Three of SDL 3.4.18's fixes are in exactly those paths
  and no automated test covers a real compositor. Do **not** test on GNOME Shell 46 — the build docs warn it
  segfaults in `wl_client_destroy` when a client disconnects and costs the session.
- **`palantirCannotFormat` after step 2.9** — re-derived, not edited by hand.
- **`spotlessApply`'s diff after step 2.5** — should be empty; if it is not, read why.
- **The showcase's Icons sheet** after step 5.1, visually: 1 870 tiles reflowing is the thing most likely to
  look wrong.
- **macOS:** `-XstartOnFirstThread` is passed by `./gradlew run`; an ad-hoc run needs it or `SDL_Init` fails
  with a misleading "No available video device".

---

## Rollback

Every batch is a single-file-or-two change and reverts cleanly with `git revert <commit>`. Per batch:

- **Batch 1 (Logback).** Revert the catalog line. Nothing is generated from it.
- **Batch 2 (tooling).** Revert the catalog lines / `gradle-wrapper.properties`. For the wrapper, re-run
  `./gradlew wrapper --gradle-version 9.7.1` rather than hand-editing, so the jar and script go back too.
  For step 2.9, revert both the version line **and** the reformat commit — in that order — and restore the
  original `palantirCannotFormat` list.
- **Batch 3 (natives).** Revert the catalog lines, then **`./gradlew :natives:cleanNativeDeps` and rebuild**
  — the checkouts in `natives/.deps/<target>` live outside `build/` and survive `clean`, so a revert without
  that step leaves the old sources in place and the build will disagree with the pin. Re-bless or revert any
  golden images blessed in the same PR.
- **Batch 4 (GraalVM).** Revert the one `version:` line in `showcase.yml`. Nothing local changes.
- **Batch 5 (Lucide).** Revert `Asset.java` (version, URL, SHA-256, licence URL), `licenses/lucide.txt`,
  any `NOTICE` / `THIRD-PARTY-NOTICES.md` wording, the re-blessed `example` goldens and the comment updates.
  Then `./gradlew prepareAssets` to recompile the icon table from the old archive. The checksum decides the
  cache hit, so the old archive is re-fetched or re-used automatically.

---

## Skipped

| Dependency | TOML key | Pinned | Newest upstream | Why skipped |
|---|---|---|---|---|
| AsmJit | `asmjit` | `6a6cca88…` (2026-09-05) | `0d7f0105…` (2026-09-23), 5 commits ahead | **Deliberate pairing with Blend2D.** Blend2D's `master` is still *exactly* at the pinned `58ca9460…`, and the commits since the AsmJit pin include an **ABI bump to v1.23 for arm64e** — which postdates Blend2D v0.21.3, so nothing upstream says the pair works. The catalog's own rule is that both move together. **But flag it:** one of those 5 commits is *"Fixed using x30 (lr) during function calls"* — an AArch64 JIT codegen fix, and Goldberry ships `linux-aarch64` and `macos-aarch64` with Blend2D's JIT, so the pinned AsmJit carries that bug. Worth revisiting the moment Blend2D cuts a release adapted to AsmJit v1.23, or testing the pair deliberately. |
| FFmpeg | `ffmpeg` / `ffmpegCommit` | `n8.1.3` | `n8.1.3` is current for the 8.1 line; **`n9.0.2`** (2026-09-18) is a newer major | `n8.1.3` is the newest tag on `release/8.1`, and there is no `release/8.2`. FFmpeg 9.0 bumps **every** library major: avformat 62→63, avcodec 62→63, avutil 60→61, swresample 6→7, swscale 9→10 (verified from both branches' `version_major.h`). `FfmpegVersions` checks exactly the 8.x set, and `FfmpegStructs`' layouts would all need re-reading. That is a migration project, not a digest item. |
| WebView2 SDK | `webview2` | `1.0.1150.38` | ~`1.0.4129.50` | Pinned on purpose to the SDK version **webview 0.12.0 pins and tests against**, which is why it is a 2022 date. Headers only, nothing linked or shipped. Moving it means moving `webview` first — and `webview` is current at 0.12.0. |
| Unicode emoji data | `UNICODE_EMOJI` in `Asset.java` | `17.0` | **Emoji 18.0** (final data files published) | Deliberately coupled to the Unicode version the pinned Noto release draws, and **Noto Color Emoji 2.051 is still the newest Noto release**. Moving the data ahead of the font would put `emoji-test.txt` groups in front of glyphs the face does not have. Revisit when Noto ships a Unicode 18 build. |
| JDK | `java` | `25` | JDK 27 is GA; JDK 25 is on 25.0.4.x | Not behind: the pin is an **LTS floor**, and the catalog records why (FFM final, Vector API for the blur path). CI's `setup-java` with `java-version: '25'` already tracks the newest 25.x patch. No action. |

### Already current (24 of 39)

`junit` 6.1.3 · `jacoco` 0.8.15 · `mavenPublishPlugin` 0.37.0 · `errorprone` 5.1.1 · `errorproneCore` 2.50.0 ·
`jspecify` 1.0.1 · `spotbugs` 4.10.4 · `pitest` 1.30.0 · `pitestJunit5` 1.2.3 · `jmhPlugin` 0.7.3 ·
`jmh` 1.37 · `blend2d` (`master` == pin) · `yoga` v3.2.1 · `md4c` v0.6.0 · `libwebp` v1.6.0 ·
`webview` 0.12.0 · `dav1d` 1.5.4 (+ `dav1dCommit`) · `ffmpegCommit` (mirrors `n8.1.3`) · Inter 4.1 ·
JetBrains Mono 2.304 · Noto Color Emoji 2.051

---

## Suggested PR order

1. `build(deps): bump Logback 1.6.3 → 1.6.5 for CVE-2026-104721` — merge first, independent of everything.
2. Batch 2 tooling, as the separate commits listed above. Hold 2.9 (palantir) back until the rest is green.
3. `build(deps): bump HarfBuzz 14.5.0 → 14.6.0` — zero API risk, real shaping fix; goldens may move.
4. `build(deps): bump SDL3 release-3.4.16 → release-3.4.18` — needs hand testing on Wayland and X11.
5. `build(deps): bump GraalVM CE 25.3 → 25.4` — or wait for 25.5 on 2026-10-27 and take one step.
6. `build(deps): bump Lucide 0.469.0 → 1.54.0 and re-vendor its licence` — its own PR; licence and goldens.
---

## Status — 2026-10-10

Everything in the plan is applied. Verified on
linux-x64 (Temurin 25.0.4, NVIDIA under GNOME Wayland): `./gradlew build checkLicenses
-Pgoldberry.native.required=true` against a `libgoldberry` rebuilt from clean deps, with the
GPU lanes under `-Pgoldberry.gpu.videoDriver=x11` — **10,531 tests and 148 GPU-lane tests, 0
failures**.

| Step | State | What happened |
|---|---|---|
| 1.1 Logback 1.6.5 | done | The showcase uses neither `SiftingAppender` nor `MDCBasedDiscriminator`, so hygiene rather than exposure. The guide's two `runtimeOnly` snippets still say 1.6.3; the change is parked in `docs/snapshot/guide-logback-version.md` |
| 2.1 Gradle 9.8.1 | done | `./gradlew wrapper` moved the properties only; the jar and scripts did not change. `toolkit/build.gradle` registers a `withXml`, so that one POM opts out of the new up-to-date check |
| 2.2–2.4 ArchUnit, NullAway, PMD | done | No new findings from any of the three |
| 2.5 Spotless 8.10.4 | done | `spotlessCheck` passed before the palantir step: nothing rewritten |
| 2.6 SLF4J 2.0.20 | done | Nothing calls `Marker.add`/`remove`; one test implements `Logger` with a `Marker` parameter, unaffected |
| 2.7 SpotBugs plugin 6.5.12 | done | Also its version in the catalog's comment |
| 2.8 jqwik 1.10.1 | done | Both engines register; `SeriesPropertyTest`'s nine properties ran |
| 2.9 palantir 2.102.0 | done, **better than planned** | The `///` bug is fixed: a long doc line is wrapped with its prefix (checked with a scratch file). Emptying the list broke nothing, so `palantirCannotFormat` is down from 21 entries to `module-info.java`, which stays out because the formatter splits `opens … to` and moves blank lines in a descriptor two tests read from disk. 68 files reformatted, no `///` line touched |
| 3.1 HarfBuzz 14.6.0 | done | No golden moved because of it: every changed picture below is an icon, and text is pixel-identical |
| 3.2 SDL 3.4.18 | done, one check left | `:natives:test` (layout probe included) green. **Not done:** clipboard and DPI by hand on Wayland and X11 |
| 4.1 GraalVM 25.4 | done | 25.4 is `25.4.4.1.1`. `GraalVmRelease.CI_LINE` in build-logic was a second copy of the pin, moved with its tests. A local `:example:nativeImage` on 25.4.4.1.1 built in 2m 43s and painted 300 resized frames through Vulkan. `setup-graalvm` resolving `'25.4'` is still for CI to show |
| 5.1 Lucide 1.54.0 | done | Checksum, 1870 SVGs and the 24×24/stroke-2/no-`<g>` invariants confirmed. No name the repo resolves was removed (`git-commit`, `grid`, `home` are application-registered names, absent from 0.469.0 too). Licence vendored by `vendorLicences`; `NOTICE`, `THIRD-PARTY-NOTICES.md` and the table header say MIT for the Feather-derived icons. 60 goldens re-blessed (59 from `test`, `gallery-gpu-drawn` from `gpuTest`), every one an icon Lucide redrew (`palette`, `play`) or the Icons sheet itself. "481 of the 1544" recomputed as 634 of 1870 by the same rule, which gives 481 on 0.469.0 |

Beyond the plan:

- **The Icons screen's search placeholder said "Search 1544 icons" as a literal.** It reads the
  table's count now, and the icon cache is sized from the table (`HashMap.newHashMap`) rather than
  a constant 2048 that 1870 entries would have outgrown.
- `IconsScreenTest` and `FrameBudgetBenchmark` assert against `BundledAssets.iconNames().size()`
  instead of 1544, so the next Lucide bump is not a test edit.

Decided:

- **The 18 book pictures are updated in this commit** (`book/src/images/screen-*.webp`, the
  `palette` icon in the showcase's top bar). They are `ScreenPicturesTest`'s goldens, and the
  user allowed them as an exception to not editing `book/` before a release.
- **Committed to `master`** as one commit for the five batches. The plan's per-batch split was
  not kept: the palantir reformat and the Lucide edits touch the same files.

Open:

- SDL 3.4.18's clipboard and DPI fixes by hand on Wayland and X11, as step 3.2 says.
- `setup-graalvm` resolving `'25.4'`, on the first `showcase.yml` run.
