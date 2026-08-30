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
**Property-based tests (jqwik)** where round-trips exist. Built today over the chart stack, which is where the invariants are: `Scale` is a bijection (`from(at(v))` is `v`) and order-preserving, `Lttb` returns a subsequence of its input with both ends kept, and `Ticks` produces an ordered labelling consistent with its own `count` and `step` (`SeriesPropertyTest`). KDL parse↔serialize is not among them because there is no serializer to round-trip against.

### 1.2 Widget & interaction tests (headless backend)
The `headless` backend renders to `BLImage` and pumps synthetic events through the normal dispatch path — no display server anywhere.
- **Semantics queries, not pixel poking** — *planned, not built*. There is no role and no name to query: the semantics tree is `ARCHITECTURE.md` prose and the AccessKit bridge is M5. Tests find widgets through `Described`, which walks the element tree by type and id. When roles exist this is where `byRole` goes, and the rewrite is mechanical.
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

### 1.5 Performance (frame budget)
- **Benchmarks are JUnit classes tagged `benchmark`**, run by `./gradlew benchmark` and never by `check`. They print measurements and assert almost nothing, deliberately: a timing assertion on shared CI hardware fails for reasons that have nothing to do with the code, and ADR-0028 and ADR-0031 both took their numbers this way — measured, written down, and argued about in prose.
- Hot seams covered: shaping and the paragraph cache, Yoga measure upcalls, binding schemes (woven against reflective), and `FrameBudgetTest`'s scripted scenarios on the headless backend.
- **JMH itself is not wired.** Its Gradle plugin generates a source set, and doing that inside a JPMS build is the integration PIT also ran into (§6). The `benchmark` task is what exists, and it runs nightly.

### 1.6 Dual-mode & native-image lanes
- Full suite twice per PR: reflection-fallback (the default, which is what a jar does) and woven (`-Pgoldberry.nativeImage=true`, which is what a native image does — ADR-0155). Two invocations rather than two tasks, because weaving rewrites the compiled classes in place and one build cannot hold both forms. Coverage survives the split: the exec file is named for the mode and the report reads every file it finds.
- Native-image lane (nightly + release): build the gallery with GraalVM on all three OSes, run a scripted smoke (launch, render reference screens, compare goldens, exit). JVM lanes measure coverage; the native lane proves AOT viability — it contributes no coverage by design.

### 1.7 Accessibility & design-system checks
- Contrast: every semantic text/surface token pair validated ≥ 4.5:1 (3:1 large) in both themes, including the frost worst-case floor — plain unit tests over the token tables.
- Semantics completeness: walking the gallery asserts every interactive node exposes role + name.
- GDS conformance: gallery renders at 150% text scale must not clip (golden + overflow assertions); spacing values are asserted against the legal ramp where computable.

## 2. Architecture & static analysis gates

- **ArchUnit** (`BoundaryTest`, `DeterminismTest`): a raw `MemorySegment` never leaves `:natives`; `:common` depends on nothing of Goldberry's; `:core` never reaches its own catalog; a widget never imports a backend or opens a window; and nothing in the deterministic layer reads a clock, a random source, the default locale or the default time zone.

  Not built: "nothing outside `theme` references raw Nord constants". The colours live in `nord-dark.css` and `nord-light.css` and reach code only through `var(--gb-*)`, so there is no constant to reference and nothing for a rule to catch. The equivalent check — no literal colour in a painter — needs to read int literals, which ArchUnit does not do.
- **Blocking at compile:** Spotless (palantir-java-format, all 919 files), Error Prone, NullAway. JSpecify is wired and adopted one package at a time: NullAway runs in `OnlyNullMarked` mode, so an unannotated package is invisible to it and an annotated one is checked from the moment it opts in.
- **Blocking after triage:** PMD, with a hand-picked ruleset in `config/pmd/ruleset.xml` — PMD's defaults are a style guide, and this codebase's comments are prose and its painters are long because a rasterizer step is long. SpotBugs is not wired: two overlapping bytecode analysers is a second report to triage for the same findings.
- **Advisory dashboards:** CodeQL (`codeql.yml`, nightly and per-PR) is live and needs no account. Qodana (`qodana.yaml`, `qodana.yml`) and Codecov (a step in `linux.yml`) are wired and **guarded on their secrets**, so both are silent until connected rather than red until then. §7 is the checklist.

## 3. Coverage

- **JaCoCo** per module (toolVersion pinned latest for JDK 25 class files) + Gradle `jacoco-report-aggregation` for one project-wide XML/HTML.
- **Merged across modes:** execution data from woven and fallback runs feed one report — a line is uncovered only if neither mode reaches it.
- **Exclusions:** weaver *output* classes (no source mapping) excluded from reports; `natives` bindings module excluded from gates (its real test is layout agreement); the weaver *module itself* is covered normally.
- **Gates:** per-module `jacocoTestCoverageVerification`, wired into `check`, and trivially passing in a module that declares no rules — so a floor exists only where a module has said what its own means. `:core` is at 80% line / 68% branch, `:widgets` at 87% / 71%, and the `css`, `kdl` and `bind` packages at 70%. Every number is a **ratchet set from a measured value**, not a target: it catches a change that drops coverage and never blocks one that merely fails to raise it.
- **Codecov** for PR diff coverage — wired, guarded, not yet connected (§7).
- **PIT (pitest)** nightly over the logic packages, run through PIT's own command line rather than `gradle-pitest-plugin`, which reads `reporting.baseDir` and so cannot be applied on Gradle 9. It completes where the test runtime is plain and its coverage minion dies where the tests run on the module path (§6).

## 4. CI matrix

What the workflows actually do. Every lane below exists; the column that used to
be aspirational is now §6's business.

| Lane | Workflow | Trigger | Contents |
|------|----------|---------|----------|
| fast | `linux.yml` (`java` job) | every push + PR | `./gradlew build checkLicenses` — Spotless, Error Prone, NullAway, PMD, ArchUnit, the coverage gates, unit + widget tests. Then the suite again woven, then the aggregate coverage report and the Codecov upload |
| per-OS | `linux.yml`, `macos.yml`, `windows.yml` (`natives`, `verify`) | every push + PR | The superbuild, the glibc floor check, layout agreement across platforms, and the whole `:core`/`:widgets` suite against the real library — including every golden image, since Blend2D JITs its pipelines per CPU |
| showcase | `example.yml`, `showcase.yml` | every push + PR | The example builds and runs; the self-contained image builds |
| advisory | `codeql.yml`, `qodana.yml` | PR + schedule | CodeQL security queries; Qodana's inspection set once a token exists |
| nightly | `nightly.yml` | 03:40 UTC | PIT mutation testing, the benchmarks, and coverage over both binding modes |
| release | `release.yml` | tag | Every OS, the native-image artifacts, and the reachability-metadata drift check |

**Goldens are on every PR, not on a `full` label.** They are the assertion for
painters (§0.2), so gating them behind a label would mean the check that matters
most runs least. What is on the nightly lane instead is everything whose answer
is a number to read rather than a gate to pass.

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
- **§2 Spotless with palantir-java-format**, over **all 919 Java files**, no
  exclusions. Plus `removeUnusedImports` and an import order that keeps static
  imports first, where the tool's own default would have moved them last.
- **§2 Error Prone + NullAway**, blocking on `src/main`. NullAway runs in
  `OnlyNullMarked` mode; `io.github.digitalsmile.goldberry.log` is the first
  package to opt in, and a `return null` added to it fails the build.
- **§2 PMD**, eight hand-picked rules in `config/pmd/ruleset.xml`, on `src/main`
  only.
- **510 broken ADR links repaired.** Every relative `.md` link from Java source
  into the book resolved to nothing — the `../` depths had been wrong since they
  were written — and the book is built and published by nothing, so there was
  nowhere else for them to point. They are now the plain reference they always
  effectively were, which is also what let the formatter reach every file.
- **§4's lanes.** `nightly.yml` (PIT, benchmarks, both-mode coverage),
  `qodana.yml` and the Codecov step, both guarded on their secrets.
- **§3 JaCoCo**, per module and aggregated, genuinely **merged across binding
  modes**: the exec file is named for the mode, so a reflective run and a woven
  run leave two files and one report reads both. Floors on `:core` and
  `:widgets` are ratchets set from measured values.
- **§1.6 dual-mode CI.** `linux.yml` runs the suite reflectively and again with
  `-Pgoldberry.nativeImage=true`, then uploads the aggregate report.
- **§2 CodeQL**, nightly and on pull requests (`codeql.yml`).
- **§3 PIT**, as a `pitest` task per module — see the limit below.

### Deliberately narrowed

- **Nothing is excluded from the formatter now**, and the way that was reached
  is worth keeping. palantir 2.97.0 mishandles JDK 23+ `///` markdown javadoc:
  when a line cannot fit at its indentation it wraps the content onto a new line
  *without* re-emitting the `///`, so the comment becomes a statement and the
  file stops compiling. Run over the whole tree it wrote 11 corrupted files and
  80 compile errors to disk before reporting anything, and `formatJavadoc(false)`
  does not prevent it — the step treats `///` as a line comment, which that flag
  does not govern.

  Nineteen files were excluded for a week. What they had in common was a token
  the formatter could not place: an ADR link whose URL was a relative path ninety
  characters long. Those links turned out to be broken — 510 of 511 — so the fix
  was not a concession to the tool but a repair the tool found. The 59 doc lines
  still over 100 characters afterwards were re-wrapped at word boundaries,
  changing no word and skipping code fences, tables and box drawing.
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
  `benchmark` task is what exists and it runs nightly, on the footing ADR-0028
  and ADR-0031 set: numbers printed and argued about in prose rather than pinned
  by a threshold that fails on shared CI hardware.
- **SpotBugs + fb-contrib (§2).** Not attempted after PMD: two overlapping
  bytecode analysers on the same codebase is a second report to triage for the
  same findings, and PMD's ruleset here took three attempts to make load at all.
- **Qodana and Codecov are wired but not connected.** Both need a token this
  repository does not have. Neither fails a build in the meantime — the Qodana
  job gates itself on the secret and skips with a note, and the Codecov step is
  conditional on the same. §7 is the two checklists.

### Two failures worth keeping

Both were silent, and both were found by reading output rather than exit codes.

- The PMD ruleset **did not load** for its first two revisions — an XML comment
  cannot contain `--`, and PMD 7 had removed a rule I named. Each time PMD
  printed `Cannot load ruleset` to stderr and then passed with no rules at all.
- **Two files carried a javadoc reference split across two lines**, `[` on one
  and the target on the next: `TourState.java` and `Blend2dContext.java`. Each
  crashed a Java parser with a bare `NoSuchElementException` — Error Prone's on
  one, palantir's and Spotless's on both — and none of the three said why. They
  are the reason `:natives` has Error Prone disabled and the reason
  `removeUnusedImports` was switched off for a while; both are fixed, and both
  were one-line edits once the cause was visible.

## 7. Connecting Qodana and Codecov

Both are wired and both are inert. Each needs one secret, and until it exists the
job skips rather than fails — an analyser nobody has connected should be silent,
not a red cross on every pull request.

### Codecov — PR diff coverage

Everything in the repository is done: `linux.yml` builds one aggregated JaCoCo
XML across both binding modes and has the upload step, guarded on the secret.

1. Sign in at <https://codecov.io> with the GitHub account that owns the repo and
   add `digitalsmile/goldberry`.
2. Copy the **repository upload token** it shows you.
3. Add it as an Actions secret named `CODECOV_TOKEN`:
   *Settings → Secrets and variables → Actions → New repository secret*.
   A public repository can upload tokenless, but it is rate-limited and fails
   opaquely on forks; the token is worth the two minutes.
4. Nothing else. The next push to `main`/`master` uploads
   `build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml`
   under the `jvm` flag.
5. Optional: add the badge to `README.md`. Codecov gives you the markdown; the
   design system's green is `#A3BE8C` if you want it to match.

**What you will see first.** Around 84% line and 72% branch overall. Do not read
that as a target to raise: more than half of `:core` is painters, where §0.2 says
line coverage proves reachability and the goldens prove correctness. The number
worth watching is the **diff** coverage on a pull request.

**`fail_ci_if_error` is false** on purpose. Coverage reporting is a dashboard,
and an upload that failed because a third party was down is not a reason to block
a merge.

### Qodana — IntelliJ's inspection set

The profile, the exclusions and the quality gate are in `qodana.yaml` at the
repository root, so they are reviewed as code rather than configured in a web
form nobody can diff.

1. Sign in at <https://qodana.cloud> with JetBrains or GitHub.
2. Create an **organization**, then a **project** for this repository. Community
   for JVM (`jetbrains/qodana-jvm-community`) is free and is what `qodana.yaml`
   names — the paid tiers add taint analysis and a licence audit, neither of
   which is what this is for.
3. Copy the project token.
4. Add it as an Actions secret named `QODANA_TOKEN`.
5. Push. The first run has no baseline, so expect a large report; accept it as
   the baseline in Qodana Cloud, after which the quality gate in `qodana.yaml`
   fails only on **new** high-severity findings.

**Why the gate is new-issues-only.** A baseline of existing findings does not
have to be cleared before the tool is useful, and requiring that is how static
analysis gets switched off. An existing finding is something to schedule; a new
one arrived with a pull request and has somebody to ask about it.

**Expect overlap.** Error Prone, NullAway, PMD and ArchUnit already run on every
PR and block. Qodana is advisory precisely because most of what it reports will
be one of those findings again, or a style opinion this codebase has already
decided against. It earns its place on the few that are neither.
