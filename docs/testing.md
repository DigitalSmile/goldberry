# Goldberry — Testing Approach

Companion to `ARCHITECTURE.md`, `core-widgets.md`, `content-widgets.md`, `goldberry-design-system.md`. Describes what is tested, how, and what gates a merge.

## 0. Principles

1. **Determinism is a feature under test.** Embedded fonts, CPU rasterization, seeded randomness, and a virtual clock make output byte-stable — so the test suite asserts *exact* results, and any nondeterminism is itself a bug.
2. **Pixels are the assertion for painters.** Line coverage of paint code proves reachability, not correctness; golden images prove correctness. Logic modules get asserted the classic way.
3. **Both execution modes are first-class.** Every suite runs woven (Class-File-API weaver output) *and* in reflection fallback. The two paths must be semantically identical; CI enforces it.
4. **A widget isn't done until it's in the gallery.** The gallery app is simultaneously the demo, the visual-regression corpus, and the accessibility sweep.
5. Test the seams that can break silently: native struct layouts, cascade order, cache invalidation, focus order — not getters.

## 1. Test kinds

### 1.1 Unit tests (JUnit 5)
Pure-logic modules with exact assertions: CSS tokenizer/parser/cascade/specificity, KDL parsing and inflater errors (source positions!), Yoga property mapping, text segmentation (Bidi runs, break candidates), tick labeling (Wilkinson), M4/LTTB aggregation, OKLCH ramps, easing curves, binding path resolution.
**Property-based tests (jqwik)** where round-trips exist: KDL parse↔serialize, CSS tokenizer on generated input, color conversions, series aggregation invariants (M4 envelope ⊆ raw min/max).

### 1.2 Widget & interaction tests (headless backend)
The `headless` backend renders to `BLImage` and pumps synthetic events through the normal dispatch path — no display server anywhere.
- **Semantics queries, not pixel poking:** `click(byRole(BUTTON, "Apply"))`, `type(byRole(TEXTBOX, "Name"), "…")`. This keeps tests refactor-stable and doubles as an accessibility completeness check.
- **Virtual clock:** `clock.advance(160)` steps animations deterministically; tests assert mid-transition frames, enter/exit lifecycle states (`closing` disables input), and reduced-motion collapse.
- Focus-order tests walk Tab/arrow traversal per the `core-widgets.md` keyboard maps.
- Capture UIs test against synthetic sources (color-bar camera, sine/noise/sweep mic) — no hardware in CI.

### 1.3 Golden-image tests
- **Corpus:** the gallery matrix — every widget × every state (rest/hover/active/focus-visible/disabled/checked/invalid) × light+dark × regular+compact density × text scale 100% and 150%. Plus content modules: HTML documents, PDF pages, chart types, animation keyframes.
- **Comparison:** byte-exact against per-platform goldens (linux-x64 is the reference; each CI OS/arch keeps its own set — Blend2D SIMD paths may differ across architectures, and hiding that behind epsilon would hide real regressions).
- **Workflow:** failures upload actual/expected/diff triptychs as CI artifacts; intentional changes are blessed with `./gradlew blessGoldens` and reviewed as image diffs in the PR. Goldens live in-repo as PNGs (small, UI-sized); move to Git LFS only if the corpus outgrows ~100 MB.
- **Determinism rules for golden tests:** embedded fonts only, fixed scale factor, virtual clock, seeded RNG, no wall-clock or locale dependence.

### 1.4 Native layer
- **Layout agreement:** jextract bindings are generated per platform in CI; a check asserts struct layouts/offsets agree across platforms (guards Win64 `long` and alignment surprises).
- Superbuild smoke: `libgoldberry` loads, version symbols match pinned dependency versions.
- FFM lifecycle tests: arena closure invalidates wrappers with `IllegalStateException`, `Cleaner` safety net fires under leak simulation.

### 1.5 Performance (JMH + frame budget)
- JMH benches on the hot seams: shaping + paragraph cache, Yoga measure upcalls, M4 aggregation over `MemorySegment` (Vector API), damage/composite cost.
- Frame-budget tests: scripted scenarios (resize wrapped text, scroll long list, pan 10M-point chart) on the headless backend must stay under budget; tracked over time, alert on >10% regression. Nightly, not per-PR.

### 1.6 Dual-mode & native-image lanes
- Full suite twice per PR: woven and reflection-fallback (`-Pgoldberry.weave=false`). Test runtime classpath includes weave output so woven artifacts are what's actually exercised.
- Native-image lane (nightly + release): build the gallery with GraalVM on all three OSes, run a scripted smoke (launch, render reference screens, compare goldens, exit). JVM lanes measure coverage; the native lane proves AOT viability — it contributes no coverage by design.

### 1.7 Accessibility & design-system checks
- Contrast: every semantic text/surface token pair validated ≥ 4.5:1 (3:1 large) in both themes, including the frost worst-case floor — plain unit tests over the token tables.
- Semantics completeness: walking the gallery asserts every interactive node exposes role + name.
- GDS conformance: gallery renders at 150% text scale must not clip (golden + overflow assertions); spacing values are asserted against the legal ramp where computable.

## 2. Architecture & static analysis gates

- **ArchUnit** (JUnit): only `goldberry.natives` touches `MemorySegment`; widgets never import backend classes; nothing outside `theme` references raw Nord constants; module dependency direction matches ARCHITECTURE §2.
- **Blocking at compile:** Spotless (palantir-java-format) + Error Prone + NullAway (JSpecify annotations on all public API).
- **Blocking after triage:** SpotBugs + fb-contrib, PMD/CPD (duplication guard for the widget/painter corpus).
- **Advisory dashboards:** Qodana Community for JVM (shared inspection profile in-repo, incl. structural-search rules like "no literal colors in painters — tokens only"; baseline committed, quality gate on new issues), CodeQL (security, free on public repos), Sonar/Codecov badges.

## 3. Coverage

- **JaCoCo** per module (toolVersion pinned latest for JDK 25 class files) + Gradle `jacoco-report-aggregation` for one project-wide XML/HTML.
- **Merged across modes:** execution data from woven and fallback runs feed one report — a line is uncovered only if neither mode reaches it.
- **Exclusions:** weaver *output* classes (no source mapping) excluded from reports; `natives` bindings module excluded from gates (its real test is layout agreement); the weaver *module itself* is covered normally.
- **Gates:** per-module `jacocoTestCoverageVerification` — strict on logic (CSS, KDL, weaver, text, series), lenient on painters (goldens carry correctness there). No single global number.
- **Codecov** (free OSS) for PR diff coverage + badge (`#A3BE8C`); same XML feeds Sonar/Qodana coverage import.
- **PIT (pitest)** nightly on parser/cascade/inflater/aggregation modules — mutation testing catches "covered but unasserted," the failure mode golden-heavy suites develop.

## 4. CI matrix

| Lane | Trigger | OSes | Contents |
|------|---------|------|----------|
| fast | every PR push | linux-x64 | Spotless/ErrorProne/NullAway, unit + widget tests (both modes), ArchUnit, goldens (linux set) |
| full | PR label / merge queue | linux-x64, windows-x64, macos-aarch64 | everything in fast + per-OS goldens, layout agreement, SpotBugs/PMD, Qodana, coverage upload |
| nightly | schedule | all + linux-aarch64 | JMH tracking, PIT, native-image smoke, CodeQL deep |
| release | tag | all | full + native-image artifacts + reachability-metadata drift check (tracing agent vs shipped JSON) |

## 5. Contributor workflow

`./gradlew check` = the fast lane locally (formatting auto-applied, blocking analysis, both-mode tests, linux goldens if on linux — otherwise golden tests compare against the platform's set or skip with a notice). Golden updates: run `blessGoldens`, commit the PNGs, explain the visual change in the PR. New widgets must arrive with: spec (core-widgets format), GDS metrics row, gallery pages, semantics assertions, and goldens — per the GDS §5 governance rule: before code review, not after.
## 6. What is built

This section tracks the gap between the plan above and the repository. It is the
same discipline `book/src/status.md` applies to the toolkit: a plan with no
status beside it reads as a description of what exists.

### Built

- **§1.1 property-based tests.** jqwik, on the three pieces of the chart stack
  that have invariants rather than answers — `Scale` is a bijection (`from(at(v))`
  is `v`), `Lttb` returns a subsequence with both ends kept, `Ticks` produces an
  ordered labelling that is internally consistent. `SeriesPropertyTest`.
- **§1.2/§1.3/§1.4 were already built** and predate this document: the headless
  backend, the virtual clock, `GoldenImage` with its scale sweep, and the native
  layout probe.
- **§1.3/§5 `blessGoldens`.** Per module and aggregated at the root. It runs the
  whole suite under `goldberry.golden.update` rather than a filtered set, because
  goldens are asserted from classes that are not all named `*GoldenTest`.
- **§2 ArchUnit.** `BoundaryTest` asserts the arrows in `ARCHITECTURE.md` §2 and
  the FFM boundary in §3.1 — a raw `MemorySegment` never leaves `:natives`,
  `:common` knows nobody, `:core` never reaches its own catalog, a widget never
  imports a backend or opens a window. `DeterminismTest` asserts §0.1 over the
  layer whose output is compared exactly.
- **§3 JaCoCo.** Per module, aggregated at the root by `jacoco-report-aggregation`;
  `./gradlew coverage` writes one XML and one HTML. `:natives` is excluded — its
  real test is the layout probe, and thousands of generated accessors in the
  denominator would make every other module's figure meaningless.

### Not built, and what each is waiting on

- **§1.3's per-platform byte-exact goldens.** This *contradicts* what is built.
  `GoldenImage` deliberately keeps one reference set with a per-channel tolerance,
  and argues the case in its own class documentation: Blend2D compiles its
  pipelines at run time with AsmJit, so AVX2 and NEON agree on what they draw and
  not on the last bit of a blended subpixel — and three reference sets are three
  things that can each rot separately. Adopting §1.3 means reversing that
  decision, which needs an ADR superseding it rather than a patch.
- **§0.1's last three wall-clock reads.** `SelectState`, `ListState` and
  `TreeState` measure typeahead against a real clock, so the typeahead timeout is
  the one behaviour in the catalog a test cannot drive. Allow-listed in
  `DeterminismTest` with the cost written down; the fix is threading the frame
  clock into three `State` subclasses that already hold a `host`.
- **§2's formatting and nullness gates.** Spotless with palantir-java-format
  would reformat every file in the repository, and this codebase's line breaks
  and comment layout are deliberate. Error Prone, NullAway and JSpecify need an
  annotation sweep across every public API. Both are worth doing and neither is a
  side-effect of adding a plugin.
- **§1.5 JMH, §1.6 dual-mode lanes, §1.7 semantics sweep, §3's PIT, §4's CI
  matrix.** Nightly and CI-shaped work. The two binding modes already exist as
  `-Pgoldberry.nativeImage=true` versus the default, and JaCoCo appends across
  runs, so §3's "merged across modes" needs a lane rather than a mechanism.
- **§2's advisory dashboards.** Qodana, CodeQL and Codecov need repository
  secrets and an account, which is not something a commit can establish.
