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
- **Comparison:** **one** reference set, shared by every platform, compared with a per-channel tolerance of 2/256 and a cap of 2% of pixels allowed to differ at all (`GoldenImage`, ADR-0050).

  This is the reverse of the obvious design, and the reason is Blend2D: it compiles its rasterizer pipelines at run time with AsmJit, specialized to the CPU it finds — AVX2 on one runner, SSE2 on another, NEON on an Apple Silicon one. Those pipelines agree on what they draw and are *not* required to agree on the last bit of a blended subpixel. Byte-exactness would therefore fail on whichever architecture the goldens were not generated on, and the fix would be per-platform sets: three references that can each rot separately, and three diffs a reviewer has to read to approve one visual change.

  The tolerance does not hide regressions, because it is not an epsilon on the whole image: a colour that changed, a box in the wrong place or text that stopped shaping moves thousands of pixels by tens of levels, and either bound catches it on its own. What the tolerance absorbs is antialiased edges, which is exactly where the pipelines disagree and a small fraction of any frame.
- **Scale sweep:** a golden pins one scale, and it is 1.0 for almost all of them. Every image that matches is then redrawn at 2× and 1.5× and checked for describing the same picture, with nothing further committed — a second *question* rather than a second set of files (`ScaleInvariance`, ADR-0157, ADR-0162). `-Dgoldberry.golden.scales=false` turns it off.
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

- **§1.1 property-based tests.** jqwik, over the three pieces of the chart stack
  that have invariants rather than answers — `Scale` is a bijection, `Lttb`
  returns a subsequence with both ends kept, `Ticks` produces an ordered,
  internally consistent labelling (`SeriesPropertyTest`).
- **§1.2/§1.3/§1.4 predate this document**: the headless backend, the virtual
  clock, `GoldenImage` with its scale sweep, and the native layout probe.
- **§0.1 determinism, enforced and with no exceptions.** `DeterminismTest` bans
  clocks, randomness, default locale and default time zone in the layer whose
  output is asserted exactly. The three typeahead sites that used to be
  allow-listed now read `Host.clock()`, so `TypeaheadClockTest` asserts a timeout
  by advancing a virtual clock instead of sleeping through it.
- **§1.3/§5 `blessGoldens`**, per module and at the root.
- **§2 ArchUnit.** `BoundaryTest` asserts `ARCHITECTURE.md` §2's arrows and
  §3.1's FFM boundary — seven rules the module graph cannot state.
- **§2 Spotless**, whitespace/tabs/final-newline only. See "deliberately
  narrowed" below.
- **§2 Error Prone + NullAway**, blocking on `src/main`. NullAway runs in
  `OnlyNullMarked` mode; `io.github.digitalsmile.goldberry.log` is the first
  package to opt in, and a `return null` added to it fails the build.
- **§2 PMD**, eight hand-picked rules in `config/pmd/ruleset.xml`.
- **§3 JaCoCo**, per module and aggregated, genuinely **merged across binding
  modes**: the exec file is named for the mode, so a reflective run and a woven
  run leave two files and one report reads both. Floors on `:core` and
  `:widgets` are ratchets set from measured values.
- **§1.6 dual-mode CI.** `linux.yml` runs the suite reflectively and again with
  `-Pgoldberry.nativeImage=true`, then uploads the aggregate report.
- **§2 CodeQL**, nightly and on pull requests (`codeql.yml`).
- **§3 PIT**, as a `pitest` task per module — see the limit below.

### Deliberately narrowed

- **Spotless does not run a whole-file formatter.** §2 names
  palantir-java-format; applying it rewrote 138 files and produced no defect.
  The comments in this codebase are prose whose line breaks are chosen by hand,
  and a formatter reflowing them destroys what makes them readable. Enforcing an
  import order alone moved every static import below the rest, which is the
  opposite of the existing convention. `removeUnusedImports` throws inside its
  own parser on `TourState.java`. What is enforced is what is mechanical and
  unarguable.
- **`ReferenceEquality` is off**, and `CloseResource` is out of the PMD set. Each
  was wrong every time it fired — the first on the thread-confinement check
  eighteen files make, the second on 47 sites where an owner holds a closeable
  and closes it in its own lifecycle (ADR-0043). A gate that is never right
  teaches people to skip the report.
- **`:natives` has Error Prone off**: it is hand-written FFM bindings, and the
  plugin throws rather than reports on `Blend2dContext`. Its contract is the
  layout probe, which is the same argument §3 uses for keeping it out of the
  coverage gates.

### Not built, and what each is waiting on

- **§1.7's semantics sweep.** It asserts "every interactive node exposes role +
  name", and there is no role and no name to expose: the semantics tree is
  `ARCHITECTURE.md` prose and the AccessKit bridge is M5, not started. This is
  the one item here blocked on a subsystem rather than on effort. Contrast, the
  other half of §1.7, is built and has been for a while (`ContrastTest`).
- **PIT on the modular modules.** The task is wired and runs, and reports "no
  mutations" correctly where the target packages are absent. On `:core` the
  coverage minion exits with `UNKNOWN_ERROR` after the tests are sent: PIT forks
  a JVM that runs everything on the classpath, and these tests run on the module
  path. Passing the test task's JVM arguments and system properties through got
  it as far as creating 92 mutation units; the module path is the remaining
  problem, and it is a real integration rather than a setting.
- **JSpecify on all public API.** The plumbing is live and one package is marked.
  The sweep is nine hundred classes and belongs in its own commits, one package
  at a time, each checked from the moment it opts in.
- **JMH (§1.5).** The plugin resolves; wiring it into a JPMS build with a
  generated benchmark source set is the same class of problem PIT hit. The
  repository already measures the hot seams through its `benchmark` task, on the
  footing ADR-0028 and ADR-0031 set: numbers printed and argued about in prose
  rather than pinned by a threshold that fails on shared CI hardware.
- **SpotBugs + fb-contrib (§2).** Not attempted after PMD: two overlapping
  bytecode analysers on the same codebase is a second report to triage for the
  same findings, and PMD's ruleset here took three attempts to make load at all.
- **Qodana and Codecov (§2, §3).** Both need repository secrets and an account,
  which a commit cannot establish. CodeQL is in because it needs neither.
- **§4's full CI matrix.** The dual-mode and coverage lanes are in `linux.yml`;
  spreading them across the Windows and macOS workflows and adding a nightly
  schedule is mechanical and untestable from here.

### Two failures worth keeping

Both were silent, and both were found by reading output rather than exit codes.

- The PMD ruleset **did not load** for its first two revisions — an XML comment
  cannot contain `--`, and PMD 7 had removed a rule I named. Each time PMD
  printed `Cannot load ruleset` to stderr and then passed with no rules at all.
- `TourState.java` carried a javadoc reference split across two lines, `[` on
  one and the target on the next. It crashed Spotless's parser and Error Prone's,
  and neither said why.
