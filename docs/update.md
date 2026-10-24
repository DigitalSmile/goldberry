# Goldberry dependency update plan — 2026-10-24

**Repo:** https://github.com/DigitalSmile/goldberry · branch `master` (pins read from
`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` and
`.github/workflows/release.yml` as served by `raw.githubusercontent.com` on 2026-10-24 05:07 UTC).

**Summary:** 41 dependencies checked, 17 behind, 13 need action. Nothing is a known CVE, but
HarfBuzz 15.0.0 carries two memory-safety fixes in code that parses untrusted font files and is
statically linked into `libgoldberry` — do that batch first. Error Prone 2.51.0 and NullAway
0.14.3 will both surface *new* build errors, so they are the ones that cost time.

> Every pin in this plan that the native superbuild reads lives in `gradle/libs.versions.toml` and
> nowhere else: `natives/src/main/cmake/CMakeLists.txt` and `media/src/main/cmake/CMakeLists.txt`
> both parse that file (`goldberry_pin(...)`), and `:natives:checkPinnedRefs` fails if a second copy
> of a ref reappears. Do not add refs to the CMake files or the workflows.

---

## Updates

| Dependency | TOML key / file | Current → target | Verdict | Release notes |
|---|---|---|---|---|
| HarfBuzz | `harfbuzz` in `gradle/libs.versions.toml` | `14.6.0` → `15.0.0` 🆕 | SHOULD UPDATE | [15.0.0](https://github.com/harfbuzz/harfbuzz/releases/tag/15.0.0) |
| Error Prone (analyser) | `errorproneCore` | `2.50.0` → `2.51.0` 🆕 | SHOULD UPDATE | [v2.51.0](https://github.com/google/error-prone/releases/tag/v2.51.0) |
| NullAway | `nullaway` | `0.14.2` → `0.14.3` 🆕 | SHOULD UPDATE | [CHANGELOG](https://github.com/uber/NullAway/blob/master/CHANGELOG.md) |
| SpotBugs (analyser) | `spotbugs` | `4.10.4` → `4.10.5` | SHOULD UPDATE | [CHANGELOG 4.10.5](https://github.com/spotbugs/spotbugs/blob/master/CHANGELOG.md) |
| SpotBugs Gradle plugin | `spotbugsPlugin` | `6.5.12` → `6.5.13` | SHOULD UPDATE | [6.5.13](https://github.com/spotbugs/spotbugs-gradle-plugin/releases/tag/6.5.13) |
| jqwik | `jqwik` | `1.10.1` → `1.10.2` | OPTIONAL | [1.10.2](https://github.com/jqwik-team/jqwik/releases/tag/1.10.2) |
| PIT (pitest) | `pitest` | `1.30.0` → `1.30.1` | OPTIONAL | none published — verify: no GitHub release for 1.30.1, only the Maven Central artifact |
| Gradle wrapper | `gradle/wrapper/gradle-wrapper.properties` | `9.8.1` → `9.8.2` | OPTIONAL | [9.8.2 notes](https://docs.gradle.org/9.8.2/release-notes.html) |
| Noto Color Emoji (COLRv1) | verify: vendored font file in the `:emoji` module — no version pin found in any read file | `v2.051` → `v2.057` | OPTIONAL | [noto-emoji Unicode 18.0](https://github.com/googlefonts/noto-emoji/releases/tag/v2026-09-24-unicode18_0) |
| Unicode emoji data (showcase) | verify: consumed by `PrepareCatalogs` (named in `THIRD-PARTY-NOTICES.md`); no pinned URL found in any read file | `17.0` → `18.0` | OPTIONAL | [Unicode 18.0](https://emojipedia.org/unicode-18.0) |
| `actions/checkout` | `.github/workflows/release.yml` | `v4` → `v7` | OPTIONAL | [releases](https://github.com/actions/checkout/releases) |
| `actions/setup-java` | `.github/workflows/release.yml` | `v4` → `v6` | OPTIONAL | [releases](https://github.com/actions/setup-java/releases) |
| `gradle/actions/setup-gradle` | `.github/workflows/release.yml` | `v4` → `v6` | OPTIONAL | [releases](https://github.com/gradle/actions/releases) |

🆕 = published in the last 48 hours.

---

## Batch 1 — HarfBuzz 14.6.0 → 15.0.0 (native, security-adjacent; one commit/PR)

This is the riskiest *necessary* batch, but it goes first because it is the only one touching code
that parses attacker-supplied data (font files) and ships inside every published binary.

1. In `gradle/libs.versions.toml`, under `[versions]`:
    - `harfbuzz = "14.6.0"` → `harfbuzz = "15.0.0"`
    - Update the surrounding comment only if it names the version.
2. Rebuild the native dependency tree from clean, because the superbuild caches the clone:
    - `./gradlew :natives:cleanNativeDeps`
    - `./gradlew :natives:cmakeBuild`
3. Required follow-ups, in order:
    - **Deprecated API.** `hb_font_set_face()` is deprecated in 15.0.0; it now resets the font
      functions to the defaults for the new face, which fixed a use-after-free. Upstream says to use
      `hb_font_create()` instead. Search the hand-written FFM bindings for a downcall handle named
      after `hb_font_set_face` (bindings are hand-written, not jextract-generated — `docs/ARCHITECTURE.md`
      §ADR-0010) and, if one exists, replace the call site with a fresh `hb_font_create()` per face.
      If no such handle exists, no code change is needed here.
    - **Layout probe.** Re-run the run-time struct/constant probe (`docs/ARCHITECTURE.md`, ADR-0029)
      on every target; it is the mechanism that catches a drifted enumerator or struct layout. Any
      probe failure after this bump is a real ABI change, not a flake — fix the declared layout, do
      not relax the probe.
    - **Shaping output may move.** 15.0.0 enables ISO Open Font Format 5th-edition support by
      default (24-bit glyph indices, cubic curves in `glyf`, `cmap` format 15, 24-bit GSUB/GPOS/GDEF),
      rewrites the number parser without Ragel, and updates the `kbts` backend. Text positions can
      change for some fonts. Re-run the golden/visual tests per platform *before* blessing anything,
      and only bless after eyeballing the diffs:
      `./gradlew test` then, if and only if the diffs are acceptable,
      `./gradlew blessGoldens -Pgoldberry.golden.update=true` on each of linux-x64, linux-aarch64,
      macos-aarch64, windows-x64.
    - **No ABI/soname change is announced** and the licence is unchanged ("Old MIT"), so
      `licenses/harfbuzz.txt` and `THIRD-PARTY-NOTICES.md` need no edit. Still run
      `./gradlew checkLicenses` to confirm.
    - **Native image:** no new reachability metadata is expected (the bump adds no Java surface).
      Verify with the native-image build in the Verification section rather than by inspection.
4. What else is in 15.0.0, for the review message: heap-buffer-overflow fix in
   `hb_font_set_variation()` after `hb_font_set_face()` switched to a face with more axes; the
   raster/vector/GPU libraries are no longer experimental (API+ABI guarantees now apply);
   WebAssembly shaper hardened against malicious fonts; CFF/CFF2 subsetting now fails instead of
   silently dropping a table in one malformed case; overriding atomic operators at compile time is
   no longer supported (only relevant if the superbuild passes such a flag — grep
   `natives/src/main/cmake/CMakeLists.txt` for an atomics override before assuming it does not).

**Commit message:** `build(deps): bump HarfBuzz 14.6.0 → 15.0.0`

---

## Batch 2 — Java static analysis (one commit/PR; expect new build errors)

These are build- and test-time only: nothing here reaches a published artifact. Do them together,
because all three will want code or suppression changes in the same pass.

1. In `gradle/libs.versions.toml`:
    - `errorproneCore = "2.50.0"` → `"2.51.0"`
    - `nullaway = "0.14.2"` → `"0.14.3"`
    - `spotbugs = "4.10.4"` → `"4.10.5"`
    - `spotbugsPlugin = "6.5.12"` → `"6.5.13"`  *(6.5.13 contains exactly one change: it bumps its
      bundled SpotBugs to 4.10.5 — keeping the two in step is the point of bumping both.)*
2. Known breaking changes and the concrete adaptation:
    - **Error Prone 2.51.0 — `CompareToZero` is promoted from warning to error** and now covers more
      cases. Any `compareTo(...) == 1` / `< 1` style comparison that was previously a warning now
      fails the build. Fix by comparing against `0` with the right relational operator.
    - **Error Prone 2.51.0 — `IfChainToSwitch` is now enabled by default as a warning.** Harmless
      unless the build treats warnings as errors; check the conventions plugin's Error Prone
      configuration before assuming it is quiet.
    - **Error Prone 2.51.0 — `UndefinedEquals` absorbed `ArrayAsKeyOfSetOrMap` and
      `CollectionUndefinedEquality`.** If either old name appears in a `-Xep:` flag or a
      `@SuppressWarnings`, it still resolves (both are kept as alternate names), but prefer renaming
      to `UndefinedEquals`.
    - **NullAway 0.14.3 — two fixes (#2003, #2005) may produce new warnings even outside JSpecify
      mode**: unboxing a `@Nullable` operand inside a primitive conditional expression, and
      explicitly typed lambda parameters checked against unmarked interface methods. Expect real
      findings in the widget/binding code. For a staged sweep, run once with
      `./gradlew build -Pgoldberry.nullaway=warn` to collect the list, fix, then drop the property.
      Do **not** land the property.
    - **SpotBugs 4.10.5 — JSpecify type-use annotation handling in `CheckRelaxingNullnessAnnotation`
      was fixed**, and a new `ReflectiveAccessTracker` tracks fields reached via `VarHandle`,
      `MethodHandle` and atomic field updaters. Both can produce new findings in FFM/binding code
      that uses `VarHandle`. Fix or suppress with a justification; do not blanket-exclude.
    - SpotBugs 4.10.5 also hardens its own XML parsing against external-entity resolution (#4365)
      and stops reporting missing `java.applet` on Java 26+ — the latter removes noise on newer JDKs.
3. Escape hatch while triaging only, never in a landed commit: `-Pgoldberry.lenient` turns Error
   Prone off and `-Pgoldberry.nullaway=warn` demotes NullAway.

**Commit message:** `build(deps): bump Error Prone 2.50.0 → 2.51.0, NullAway 0.14.2 → 0.14.3, SpotBugs 4.10.4 → 4.10.5`

---

## Batch 3 — test and build tooling (one commit/PR; low risk)

1. In `gradle/libs.versions.toml`:
    - `jqwik = "1.10.1"` → `"1.10.2"` — the only change is that `@Provide` is now meta-annotated
      `@Keep` so IDEs stop reporting provider methods as unused. No behaviour change, no migration.
    - `pitest = "1.30.0"` → `"1.30.1"` — verify: 1.30.1 is on Maven Central (2026-10-14) but has no
      published release notes; treat it as a patch and let the mutation run speak for itself. PIT is
      driven through its own command line here, so there is no plugin version to move with it.
      `pitestJunit5 = "1.2.3"` is already current.
2. In `gradle/wrapper/gradle-wrapper.properties`:
    - `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.1-bin.zip`
      → `...gradle-9.8.2-bin.zip`
    - 9.8.2 fixes one issue: a false "graph wasn't stable enough" warning when aligning versions with
      a virtual platform. The three high-severity deserialization/SSL advisories were fixed in 9.8.1,
      which this repo already has — so this is a convenience bump, not a security one.
    - Do not hand-edit the wrapper jar or checksum; run `./gradlew wrapper --gradle-version 9.8.2`
      and commit what it changes.
3. In `.github/workflows/release.yml`, bump the action refs: `actions/checkout@v4` → `@v7` (two
   occurrences), `actions/setup-java@v4` → `@v5` or `@v6`, `gradle/actions/setup-gradle@v4` → `@v6`.
    - verify: only `release.yml` was readable from `raw.githubusercontent.com` in this run.
      `grep -rn "uses:" .github/workflows/` in a checkout and bump every workflow, not just this one —
      mixed action majors across workflows are a common source of one-leg-only CI failures.
    - Leave `java-version: '25'` alone (see Skipped).

**Commit message:** `build(deps): bump jqwik 1.10.1 → 1.10.2, PIT 1.30.0 → 1.30.1, Gradle 9.8.1 → 9.8.2, CI actions`

---

## Batch 4 — assets (one commit/PR; do last, and only after locating the pins)

Neither of these has a pin in any file this run could read. **Before changing anything, find where
the version actually lives** — likely in the `:emoji` module's build script and in the
`PrepareCatalogs` build-logic task named in `THIRD-PARTY-NOTICES.md`. If the font and the icon/emoji
archives are checked-in binaries with no version string anywhere, say so in the PR rather than
inventing a pin.

1. **Noto Color Emoji, COLRv1 build: v2.051 → v2.057.** Upstream release
   `v2026-09-24-unicode18_0` (2026-09-24) ships 2D fonts at v2.057 (the 3D set is v3.054 and is not
   used here). The file must stay **unmodified** — the toolkit draws Noto's paint graphs itself and
   must not subset, re-theme or rename it, because a modified font cannot carry the reserved name
   "Noto" under the OFL.
    - Follow-up: re-bless the emoji golden images (new and redrawn glyphs will move pixels).
    - Follow-up: the licence is still OFL 1.1, so `licenses/noto-emoji.txt` and `NOTICE` need no new
      text — but re-copy `licenses/noto-emoji.txt` verbatim from the revision actually vendored, as
      `THIRD-PARTY-NOTICES.md` § "Adding a dependency" requires, then run `./gradlew checkLicenses`.
    - Follow-up: if the notice text names the font version ("release v2.051"), update that string in
      `THIRD-PARTY-NOTICES.md` and in `docs/ARCHITECTURE.md` where it is repeated.
2. **Unicode emoji data 17.0 → 18.0** for the showcase's Emoji sheet grouping (`emoji-test.txt`,
   compiled by `PrepareCatalogs`). Pair it with the font bump: a font that knows Unicode 18.0 and a
   grouping table that stops at 17.0 will leave the new glyphs ungrouped in the showcase.
   `licenses/unicode.txt` stays (Unicode License v3) but the URL in `THIRD-PARTY-NOTICES.md` names
   the 17.0 path and must move to the 18.0 one.
3. Not changed here: Lucide, Inter and JetBrains Mono — see "Couldn't verify".

**Commit message:** `chore(assets): bump Noto Color Emoji v2.051 → v2.057 and Unicode emoji data 17.0 → 18.0`

---

## Verification

Run per batch, not once at the end.

```sh
# Java side (all batches)
./gradlew build                      # compiles + tests every module, builds libgoldberry if stale
./gradlew :build-logic:test          # the included build has tests of its own
./gradlew checkLicenses              # agreement between THIRD-PARTY-NOTICES.md and licenses/

# Native superbuild (batches 1 and 4)
./gradlew :natives:checkToolchain    # prints the cmake/ninja/compiler it will use
./gradlew :natives:cleanNativeDeps   # after any pin move, so the clone is re-fetched
./gradlew :natives:cmakeBuild
./gradlew :natives:checkPinnedRefs   # fails if a ref got duplicated outside the catalog
./gradlew :natives:test -Dgoldberry.native.required=true   # turns "no library, skip" into failure

# Rendering / shaping goldens (batches 1 and 4) — per platform, inspect before blessing
./gradlew test
./gradlew blessGoldens -Pgoldberry.golden.update=true

# Showcase and native image (all batches)
./gradlew run -Pgoldberry.example.frames=3 -Pgoldberry.backend.videoDriver=dummy
./gradlew :example:nativeImage -Pgraalvm.home=/path/to/graalvm
./example/build/native/goldberry-showcase-linux-x64
```

Check manually:

- **Batch 1:** the layout probe passed on all four published targets, not just the host. A green
  local Linux build proves nothing about `long` on Win64. Diff the golden images for text-heavy
  screens (forms, Markdown, HTML views) and look for baseline or advance-width shifts rather than
  whole-glyph changes.
- **Batch 2:** the new Error Prone and NullAway findings were *fixed*, not suppressed in bulk. Grep
  the diff for added `@SuppressWarnings` and `@SuppressFBWarnings` and justify each one.
- **Batch 3:** every workflow file uses the same action majors; the mutation score did not drop.
- **Batch 4:** the showcase Emoji sheet groups the Unicode 18.0 additions, and
  `Font.bundled(EMOJI, …)` still resolves through the `EmojiFont` service with `goldberry-emoji` on
  the path — and still fails with its explanatory message when it is absent.

---

## Rollback

Each batch is one commit touching text files only, so `git revert <sha>` is the whole rollback, plus:

- **Batch 1 and Batch 4 (native/assets):** after reverting, `./gradlew :natives:cleanNativeDeps`
  before rebuilding, or the superbuild will link the newer checkout against the older pin. If
  goldens were blessed in the same commit, the revert restores them; if they were blessed in a
  follow-up commit, revert that one too.
- **Batch 2:** reverting the pins restores the old analyser behaviour, but any code fixes made to
  satisfy the new checks are harmless to keep — keep them and revert only the version lines.
- **Batch 3:** for the wrapper, re-run `./gradlew wrapper --gradle-version 9.8.1` rather than
  hand-reverting the properties file.

---

## Skipped

| Dependency | Pinned | Latest verified | Why skipped |
|---|---|---|---|
| AsmJit | `6a6cca88d6dccef4c5c1d258b9e12d8699dff842` | master HEAD `0464e1c8f1f43c861a0c73d4e59437fff255cdd9` (2026-10-24) | The pin is deliberately paired with Blend2D v0.21.3, and **Blend2D's master is still exactly at its pinned SHA** (`58ca9460…`), so there is no new Blend2D release to re-pair against. Moving AsmJit alone is the one change the catalog comment explicitly warns against. Revisit when Blend2D tags past v0.21.3. |
| Blend2D | `58ca9460b4138af6e793184682e9324e0a8053b2` | same — master HEAD (verified via `git ls-remote`) | Already at upstream HEAD. Not behind. |
| WebView2 SDK | `1.0.1150.38` | `1.0.4314.35` (2026-10-23) 🆕 | Headers only, Windows only, and pinned to the version `webview` 0.12.0 pins and tests against. Bumping it ahead of `webview` is how `WebView2.h` include mismatches start. Move it when `webview` moves. |
| webview/webview | `0.12.0` | `0.12.0` (2024-09-11) — latest tag, `[Unreleased]` section open | Not behind. |
| FFmpeg | `n8.1.3` + commit `1041abdc…` (verified: that commit is the `n8.1.3` tag) | `n8.1.3` is the newest 8.1 point release (2026-09-21); **`n9.0.2` (2026-09-18) is a newer line** | 8.1 is still a maintained branch and 8.1.3 is current for it. Moving to 9.0 bumps the library majors that `FfmpegVersions` checks (avformat 62, avcodec 62, avutil 60, swresample 6, swscale 9) and means re-reading every layout `FfmpegStructs` declares, plus a new `ffmpeg-sources` classifier for LGPL §4. Worth a dedicated change, not a dependency bump. Note 9.0 removes CELT decoding and ogg/celt parsing and deprecated NVENC options. |
| JDK floor | `java = "25"` | JDK 27 is GA; `openjdk/jdk` master is at `jdk-28+21` | 25 is the LTS floor the toolkit documents ("Java 25 LTS is the floor: FFM is final, and the blur path needs the Vector API") and CI builds on temurin 25. Raising the floor is a compatibility decision for users, not a maintenance bump. |
| dav1d | `1.5.4` + commit `54706fc6…` (verified against the `1.5.4` tag) | `1.5.4` | Not behind. |
| Yoga | `v3.2.1` | `v3.2.1` | Not behind. |
| SDL3 | `release-3.4.18` | `release-3.4.18` | Not behind. Upstream's `3.4.19` is the post-release development bump ("Updated to version 3.4.19 for development", 2026-10-03); libsdl.org still lists 3.4.18 as the latest stable, and only even micro versions are released. |
| md4c | `v0.6.0` | `v0.6.0` | Not behind. Upstream has an unreleased "Next Version" section (autolink relaxations, hot-loop optimization) but no tag. |
| libwebp | `v1.6.0` | `v1.6.0` (2025-06-30) | Not behind. |
| JUnit, ArchUnit, JaCoCo, Spotless, Palantir Format, Error Prone Gradle plugin, JSpecify, PMD, pitest-junit5, JMH, JMH Gradle plugin, SLF4J, Logback, maven-publish plugin | see catalog | all equal to the newest upstream tag | Not behind. |

---

## Couldn't verify

- **Lucide** (icon set + icon metadata), **Inter** and **JetBrains Mono**: `THIRD-PARTY-NOTICES.md`
  calls Lucide "the same pinned archive as the icons", but no version string for any of the three
  appears in `gradle/libs.versions.toml`, the two CMake superbuilds, `gradle.properties`,
  `README.md`, `book/src/contributing/building.md`, `docs/ARCHITECTURE.md` or `docs/status.md`.
  Newest upstream, for whoever finds the pin: **Lucide 1.61.0** (npm `lucide`), **Inter v4.1**,
  **JetBrains Mono v2.304**. Locate the pin first (likely a build-logic download task or a
  checked-in archive), then decide.
- **pitest 1.30.1 changelog**: artifact exists on Maven Central (2026-10-14) with no corresponding
  GitHub release; treated as an unannotated patch above.
- **AsmJit distance from HEAD**: the GitHub API is unavailable from this environment, so the number
  of commits between the pin and master could not be counted — only that they differ.

---

## Status — 2026-10-24

Every SHOULD and OPTIONAL row in the table is applied, committed to `master` as one commit. Verified on
linux-x64 (Temurin 25.0.4, NVIDIA under GNOME Wayland) with `libgoldberry` rebuilt from clean deps:
`./gradlew build checkLicenses :natives:checkPinnedRefs -Pgoldberry.native.required=true
-Pgoldberry.gpu.videoDriver=x11` ran **12,990 tests, 0 failures** once the emoji golden was re-blessed
(144 skipped, all of them `:media` platform and hardware cases). Every `gpuTest` lane is green, and so are
the `:build-logic` suite (420) and `./gradlew run` on the dummy driver. `:example:nativeImage` on GraalVM CE
25.4.4.1 built in 3m 25s, and the image ran CI's walk under `xvfb-run` (`--frames=300 --resize=1580x1100`):
`painted 300 frame(s); exiting`.

| Step | State | What happened |
|---|---|---|
| 1 HarfBuzz 15.0.0 | done | `cleanNativeDeps` + `cmakeBuild`, and `hb-version.h` says 15.0.0. Nothing here binds `hb_font_set_face` or `hb_font_set_variation`, and the superbuild overrides no atomics. The layout probe passed, and no text golden moved: the one changed picture is the emoji sheet (step 4.1) |
| 2.1 Error Prone 2.51.0 | done, **two findings the plan did not name** | `CompareToZero` in `css.value.calc.Folding#extreme` (`Double.compare(…) * sign > 0`), now `Integer.signum(…) == sign`, the same result for a sign of ±1. `FoldingTest` gains min/max rows with the winner last, negatives, a tie and one argument, plus a case pinning -0 below 0. `MustBeClosedChecker` now treats `Arena.ofShared()` as a resource: `Demuxer.open` and `AvioBridge.open` hand theirs to the object they return and close it on every failure path, so each local carries a commented `@SuppressWarnings("MustBeClosedChecker")`. `IfChainToSwitch` reported nothing, even under `-Werror` |
| 2.2 NullAway 0.14.3 | done | No new findings, at error level |
| 2.3 SpotBugs 4.10.5 + plugin 6.5.13 | done | Resolved as 4.10.5. Every module's report is clean, including the `VarHandle` users in `:natives` and `:media` |
| 3.1 jqwik 1.10.2 | done | No change needed |
| 3.2 PIT 1.30.1 | done | See the note below the table |
| 3.3 Gradle 9.8.2 | done | `./gradlew wrapper --gradle-version 9.8.2` run twice (the second time on 9.8.2 itself). It also replaced `gradle-wrapper.jar` (SHA-256 `3b8a2577…`, which matches services.gradle.org's published checksum) and rewrote `gradlew.bat` from the newer template. `gradlew` is unchanged |
| 3.4 CI actions | done, **one change from the plan** | Every workflow, not just `release.yml`: `actions/checkout` v4 → v7 (16 uses), `actions/setup-java` v4 → v6 (13), and `gradle/actions/setup-gradle` v4 → **v5** (13), not v6. v6's caching is a proprietary component under Gradle's Terms of Use, and v5 keeps v4's MIT caching on Node 24. That was the user's choice, recorded in `docs/snapshot/adr/0650-…` (parked). checkout v7's fork refusal touches only `pull_request_target`/`workflow_run` triggers, and no workflow has either. `actions/cache`, `upload-artifact`, `download-artifact` and the pages actions were not in the plan and keep their majors |
| 4.1 Noto Color Emoji 2.057 | done, **pin was not where the plan looked** | The pin is `Asset.NOTO_EMOJI` in `:assets` (`dev.goldberry.assets.prepare.Asset`), not the `:emoji` build script. Upstream changed two things: tags are now named by date (`v2026-09-24-unicode18_0`), and the 2D fonts moved to `2D/fonts/`. The URL, licence URL and SHA-256 (`b8e25ea6…`, `head.fontRevision` 2.057) moved together. The `LICENSE` is byte-identical to the vendored `licenses/noto-emoji.txt`. `NOTICE` and `THIRD-PARTY-NOTICES.md` say v2.057. One golden re-blessed, `example/…/golden/gallery-emoji.png`: Noto redrew the smileys' shading, and layout and labels are pixel-identical. No book picture moved |
| 4.2 Unicode emoji data 18.0 | done | `Asset.UNICODE_EMOJI`: the 18.0.0 URL, SHA-256 `8f3735cd…`, `# Version: 18.0`. `THIRD-PARTY-NOTICES.md` names the 18.0 path |

**PIT (3.2):** only `:core` and `:widgets` have a `pitest` task (the conventions plugin's
`--targetClasses`). PIT 1.30.1 ran on `:widgets` here in 6m 27s: 1553 mutants, 1192 killed (77%), line
coverage 92% of the mutated classes, 12,965 test runs. There is no
local baseline to compare a score with: the nightly's `pitest` artifact holds the last one, and the next
nightly is where a drop would show.

Beyond the plan:

- `AssetTest` gains two drift guards for the next bump. A licence fetched from an asset's own repository
  must come from the same tag as the asset (it compares Noto's and Lucide's). Unicode's `emoji-test.txt`
  must come from the directory of the version the asset claims.
- `EmojiScreen`'s doc comment said 1499 `cmap` characters; the new face has 1508. The 1212 shown is
  unchanged.

Found:

- **The plan's Batch 4 check, "the showcase Emoji sheet groups the Unicode 18.0 additions", cannot
  pass on Java 25.** The sheet is the face's `cmap` filtered by `Character.isEmojiPresentation`, and
  Java 25's tables are Unicode 16.0. None of the 9 code points the new face adds is even
  `Character.isDefined`. They are in the font and the 18.0 grouping table already has them, so the
  sheet shows them when the JDK learns them, with no change here. `Asset.UNICODE_EMOJI`'s doc comment
  says so.

- **A local `:example:nativeImage` can package unwoven classes, and the image then dies at start-up
  with `nothing is bound to "app.clicks"`.** This predates this update. `weaveModels` rewrites
  `build/classes` in place and on purpose declares only a stamp as its output
  (`goldberry.weave.gradle`). So when a `jar` built from unwoven classes already exists (any earlier
  `build`), Gradle still sees the post-`compileJava` snapshot of the directory and reports
  `:example:jar UP-TO-DATE` straight after the weave. Here the jar had 0 binding references in
  `ShowcaseModel` against 324 in `build/classes`. Deleting the jar and building again gave a working
  image. CI builds from a clean checkout, so it never sees this. Not fixed here. A candidate is to make
  `jar` never up to date when `weaveModels` is in the task graph
  (`gradle.taskGraph.whenReady`), which keeps the plain `jar` out of the model weaver.

Open:

- Push: the commit is local.
- The first CI run on the new action majors, checkout v7 and setup-java v6 on all three OS lanes in particular.
- HarfBuzz 15's layout probe on macOS, Windows and linux-aarch64: only linux-x64 was rebuilt here.
