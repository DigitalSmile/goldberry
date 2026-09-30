# Static analysis: a plan for the Qodana and CodeQL findings

Triage of 2026-09-30, at `5878e577`. This is item 6 of
[the 2026-09-30 refactor](refactor-2026-09-30.md). It is a plan: nothing below
is fixed yet. The triage before this one is
[ADR-0341](../book/src/adr/0341-codeql-findings-are-fixed-where-real-and-answered-where-not.md).

## How the numbers were taken

- **Qodana.** The committed baseline, `config/qodana/baseline.sarif.json`, has
  no rule catalogue. Qodana was therefore run locally by `docs/testing.md` §7's
  recipe, and the counts come from that run: 408 findings, 408 unchanged
  against the baseline, 0 new. The baseline matches HEAD.
- **CodeQL.** Version 2.27.0 with `java-security-and-quality.qls`, the suite
  `codeql.yml` runs, by `docs/testing.md` §2's recipe. It scanned 2182 of 2211
  files, and all 333 results are in tracked files. A result counts as new or
  old by `git blame` of its line against ADR-0341's commit (`57f4fdec`), then
  by matching against that ADR's table.
- The counts predate this batch's moves. `media-platform/…` paths are
  `media/…` now, and `…goldberry.qr` is `…goldberry.image.qr`.

## Headlines

1. **`include: UnusedDeclaration` in `qodana.yaml` does nothing.** The Java
   inspection's ID is `unused`. The effective profile has no
   `UnusedDeclaration`, and `unused` stays disabled. The one inspection the file
   means to turn on has never run.
2. **Qodana does not look at tests.** The default `test:*..*` scope is off.
   CodeQL does look at them: 83 of its 333 findings are in tests.
3. **Most of Qodana's 408 come from two nullness gaps, not from scattered
   bugs.**
   - About 55 `@NullMarked` records accept null and substitute a default. That
     is 148 findings.
   - 128 of 215 main packages are not `@NullMarked`, so NullAway never sees
     them. This is item 5 of the refactor, and closing it moves these findings
     from Qodana to the compiler.
4. **CodeQL is at 333, not the 163 ADR-0341 expected.** Nearly all of the
   growth is kinds ADR-0341 already answered: `_` bindings (68 → 150) and
   fixed-signature parameters. About 33 findings are new and worth fixing, and
   4 of those are real bugs.

## Qodana: 408 findings, all in main code

401 are warning/High and 7 note/Moderate. By module: widgets 186, core 122,
media 37 (25 + 12 from the old `media-platform`), example 21, gpu 13, html 13,
build-logic 11, assets 2, weaver 2, common 1.

| Rule | Count | Where |
| --- | --- | --- |
| ConstantValue | 127 | widgets 102, core 16, html 6, media 2, example 1 |
| DataFlowIssue | 107 | widgets 65, core 30, html 5, media 3, 1 each in assets, example, weaver and common |
| AutoCloseableResource | 101 | core 48, example 18, gpu 13, widgets 12, media 9, assets 1 |
| OptionalUsedAsFieldOrParameterType | 22 | core 7, media 13, build-logic 1, example 1 |
| UnstableApiUsage | 10 | build-logic 10 |
| UnusedAssignment | 7 | media 4, core 3 |
| MissingSerialAnnotation | 6 | core 3, media 2, weaver 1 |
| PatternVariableHidesField | 4 | core 3, html 1 |
| LoggingSimilarMessage (Moderate) | 4 | media 2, widgets 2 |
| IgnoreResultOfCall | 3 | media 2, widgets 1 |
| EmptyStatementBody, MalformedFormatString, PointlessBooleanExpression, WhileCanBeDoWhile, WrapperTypeMayBePrimitive | 2 each | core, widgets |
| ClassEscapesItsScope, IfStatementWithIdenticalBranches, MismatchedArrayReadWrite, MismatchedCollectionQueryUpdate, OptionalGetWithoutIsPresent, SuspiciousListRemoveInLoop, SuspiciousMethodCalls | 1 each | core, html |

### Q1. Null-tolerant record constructors: 148 findings (P2, decided in ADR-0497)

**Decided 2026-09-30, as option A**, because item 5 of the refactor marked every
`:widgets` package and forced the question. The spike on `Toast` compiled under
javac and NullAway. The records NullAway's findings touched have been rewritten
that way. Qodana's remaining Q1 findings follow the same rule.


These are 93 ConstantValue and 51 DataFlowIssue findings on
`x = x == null ? DEFAULT : x`, plus 4 on `finite(…)`. Examples are
`widgets/…/panel/card/Card.java:68`, `widgets/…/panel/timeline/Timeline.java:89`,
`html/…/markdown/view/MarkdownView.java:116` and `core/…/css/value/Shadow.java:75`.

The behaviour is deliberate and tested: `PanelTest.nullAttributesAreNone`, and
tests that pass `null` to `CodeInput`. The declared contract is what is wrong.
Under `@NullMarked` the parameters are non-null, so the checks are dead by
contract. The Qodana gate fails on a *new* High finding, so every new widget
written this way turns it red.

**Decide one of two, for the whole codebase:**

- **(A) Say what the code does.** An explicit canonical constructor whose
  parameters are `@Nullable`, assigning the non-null fields. javac should accept
  type-use annotations on the parameters that differ from the components', but
  that has to be checked. The effort is L.
- **(B) Suppress where it is meant.** `@SuppressWarnings({"ConstantValue",
  "DataFlowIssue"})` on each compact constructor, with the ADR cited. The effort
  is M.

**Recommendation:** spike A on `Panel` first, against javac, NullAway and a
Qodana run. Take A if it compiles cleanly, because it makes the contract true
for every caller and every tool. Take B if it does not. Either way the rule
goes in one ADR.

### Q2. Nullness contracts that are really wrong: about 40 findings (P2)

- **`@Nullable` is missing where callers pass null:**
  - `TraySpec` (`tooltip`)
  - `PrimaryModifier.resolve(…, override)`
  - `Icons.resolve`
  - `controls.Scale.of`
  - `Panel`/`Card`/`MasonryBox.id()`, which return `attributes.id()`, and that
    is `@Nullable`
  - `CatalogWeaver.catalog`
- **`@Nullable` is present and never true:**
  - `Transform.java:404`, `:476` and `:443`
  - the `accessibleName()` of `MediaControlsBar`, `MediaPlayerBox` and
    `VideoSurface`
  - `SliderControl.localPart()`
- **`Observable<T>` says non-null, and bound values can be null.** One fix,
  `Observable<T extends @Nullable Object>` plus a `Validator` that accepts null,
  clears about 15 findings:
  - five "switch label `null` is unreachable"
  - `Validator` ×5
  - `BindingRegistry`
  - `FieldState`
  - probably the two `Boolean.TRUE.equals(leaving.get())`, which must **not**
    be simplified until this is settled
- **Dead checks to delete:** about 28 of them, e.g. `DonutSurface`, `Reading`,
  `FaceCoverage`. The rule for each: if a doc comment or any caller passes null,
  annotate `@Nullable`; otherwise delete the check.
- **Kept as intentional:** `FirstAnswer:38`, which guards a third-party
  provider. `HttpIO:632` is a false positive (`floorKey`).

### Q3. `@Nullable` fields used unchecked where NullAway cannot see: about 34 (P2)

- **Where:** `RenderTree` ×14 (`RenderObject.box()`), `WidgetRenderer`,
  `Window:957`, `Element:503`, `WebView:572`, `TreeState`,
  `SelectableDocument`, `ChartSurface`, `HeadlessTray`, `FieldState`.
- **Fix:** a non-null accessor such as `RenderObject.appliedBox()` that throws
  with a message, or a local copy with `requireNonNull` or an early return.
- **Relation to item 5:** item 5's `@NullMarked` sweep over `core/paint/tree`
  and `core/widget` turns these into compile errors, which is the better gate.

### Q4. AutoCloseableResource: 101 findings (P3, mostly configuration)

- **False positives:** about 97, getters returning resources someone else owns
  or that live as long as the application. The types are Font ×20,
  MediaPlayer ×15, Backend ×10, SdlGpuDevice ×8, Icon ×7, EventLoop ×6, and
  others.
- **Real:**
  - `Downloader.java:80`, an `HttpClient` that is never closed
  - `PngEncoder.java:117`, where `Deflater` is `AutoCloseable` on JDK 24+
  - `Launcher.java:444`, where `Subscription`s are dropped; their lifetime
    needs checking
- **Fix:** an ignore-list of owned types in the profile (see
  [Configuration](#configuration)), then the three real ones.

### Q5–Q7. Style and small fixes (P3)

- **OptionalUsedAsFieldOrParameterType ×22:** exclude it. It is a style
  opinion this codebase decided against. Tidy
  `WaylandDecorations.java:182`'s unchecked `Optional.get()`.
- **UnstableApiUsage ×10:** Gradle's `FlowAction` in `BuildFailureAnnotation`
  has no stable replacement. Suppress it on the class, with a comment.
- **Small real fixes:**
  - `CssTokenizer:78`: a dead assignment
  - `RenderObject:442`: `remove`+`add` → `set`
  - `RuntimeBinding:191`: `SuspiciousMethodCalls`
  - `@Serial` ×6
  - pattern variables that hide fields ×4
  - `Grid:95`: `int`
  - `Word:61`: visibility
  - `Slider:176` and `HttpIO:306/730`: ignored results, `var _ =`, or use the
    value
  - `ImageState:105` and `Playback:889/947`: similar log messages
- **False positives left alone:**
  - `HeadlessClipboard:185`
  - `Frame:130`, where the field keeps buffers alive (add a suppression)
  - `Sdl3Backend:1043/1047`, empty statements that are commented
  - WhileCanBeDoWhile ×2
  - MalformedFormatString at `LogTicks:157` and `ChartSurface:645`: the format
    is built at run time. Make it one helper with one suppression.

## CodeQL: 333 findings

311 are notes, 19 warnings and 3 errors. By module: widgets 109, core 54,
media 63 (36 + 27), natives 34, html 31, gpu 15, example 10, build-logic 6,
emoji 5, weaver 4, assets 2.

| Rule | Count | Main / test | Status |
| --- | --- | --- | --- |
| local-variable-is-never-read | 151 | 97 / 54 | 143 are `_`, already answered. 5 are `ignored` creeping back, and 3 are benchmark sinks |
| unused-parameter | 111 | 97 / 14 | mostly answered (`inflate`, interface contracts, upcalls); see C4 |
| uncaught-number-format-exception | 25 | 11 / 14 | 4 real (C1), the rest answered or false positives |
| internal-representation-exposure | 18 | 18 / 0 | false positives: the constructors copy |
| random-used-once | 6 | 0 / 6 | false positives: seeded fixtures |
| missing-case-in-switch | 5 | 5 / 0 | false positives: `case A, B ->` |
| unused-container (error) | 3 | 0 / 3 | 1 answered, 2 to fix |
| comparison-with-wider-type (security 8.1) | 2 | 0 / 2 | **fix** (C2) |
| input-resource-leak | 2 | tests | fix |
| class-name-matches-super-class | 2 | tests | rename |
| ignored-error-status-of-call | 2 | main | fix; the same as Qodana's IgnoreResultOfCall |
| potentially-weak-cryptographic-algorithm (security 7.5) | 1 | test | false positive: an MD5 frame fingerprint |
| dereferenced-value-may-be-null | 1 | main | fix; the same as Qodana's `Downloader:149` |
| constant-comparison, empty-zip-file-entry | 1 each | tests | false positives |
| local-shadows-field | 1 | main | rename |
| inefficient-string-constructor | 1 | test | answered |

**Against ADR-0341.**

- **258 are answered by it.** They are `_` ×150, `inflate(…)` ×72, interface
  contracts ×8, upcalls ×4, number parsing ×10, exposure ×9, switch ×3,
  container ×1 and `new String` ×1.
- **32 are the same kinds in new code.** ADR-0341's table needs rows for them:
  - upcalls in `GlibLog`, `SdlLog`, `IoCallbacks` and the macOS decoders
  - validated parsing in `qr/Segment` and `GraalVmRelease`
  - nine copying constructors
  - `Playback:460/780`, multi-label switch arms
- **10 are new false-positive kinds:** seeded `Random` ×6, a test MD5,
  `MemoryIO:195` (`wait()`), a directory zip entry, and an interface signature.
- **33 are to fix.** They are listed below.

### C1. Number parsing on outside input (P1)

- **`HttpIO.java:815` (`contentRange`):** a server that sends a 20-digit
  `Content-Range` gets a `NumberFormatException` out of the reader instead of
  an `IOException`. Catch it and rethrow it as an unreadable range, with a
  test.
- **`ShowcaseServer.java:101/103`:** the same bug for `Range`. Answer 416.

### C2. Wider-type comparisons (P1, for consistency with ADR-0341)

`LayerZOrderTest.java:178` compares an `int` with a `float`, and
`SdlAudioSinkTest.java:57` an `int` with a `long`. ADR-0341 fixed all four
earlier ones as policy.

### C3. `Downloader.last` (P2, clears both tools)

`Downloader.java:147/149` cannot be null, since the constructor rejects
`attempts < 1`. Say so with an explicit guard.

### C4. Dead parameters (P2 for the first, P3 for the rest)

- `Subtitles.parse`'s `format` is public and unused. Use it or remove it.
- Test helpers: `ShadowTest:370`, `HiddenSubtreeTest:92`, `GpuApiTest:673`,
  `BackdropContrastTest:292` and `StepsGoldenTest:52`.
- Three probes' `main(String[] args)`, which can be JDK 25's `static void main()`.

### C5. Test hygiene (P3)

- `YuvDrawTest:67`: the `SHADERS` list is filled and never released.
- `TreeFocusScopeTest:47`: `chosen` is never asserted.
- `QrVectorTest:34` and `NativeImagePropertiesTest:73`: need try-with-resources.
- `FrameGpuLayerTest:49/59`: rename the doubles.
- `TourVeil:86`: rename the local `target`.
- `TextAreaFrameBenchmark:203` and `ReadPacketBenchmark:78/84`: benchmark
  sinks.
- `ignored` back to `_` in `MediaScreen`, `PacketQueue` and `TextField`. Use
  `var _` inside record patterns, for palantir's sake.

## Where the tools overlap and disagree

- **One fix clears both tools:** `Downloader:149`, and `HttpIO:306/730`.
- **`var _ =` for a call kept for its side effect** clears a Qodana finding and
  adds a CodeQL `_` finding, which ADR-0341 already answers. Accept the trade.
- **`_` bindings:** IntelliJ reads JEP 456 and CodeQL does not. IntelliJ is
  right.
- **Nullness:** Qodana honours JSpecify and CodeQL ignores it. Qodana has about
  234 findings, CodeQL one.
- **Unused parameters:** CodeQL has 111 and Qodana 0, partly because `unused`
  never ran (headline 1).
- **Scope:** CodeQL covers tests and `natives`; Qodana covers neither.

## Order of work

Each batch is one commit. Every Gradle command takes
`export JAVA_HOME=~/.sdkman/candidates/java/current` and
`-Pgoldberry.skipNative=true`.

| # | Batch | Effort | Clears | Verify |
| --- | --- | --- | --- | --- |
| 1 | **P1 correctness:** C1 (`HttpIO` with a test, `ShowcaseServer`), C2, C3 | S | ~7 CodeQL, 1 Qodana | `:media:test --tests '*HttpIO*' :assets:test :gpu:compileTestJava :media:compileTestJava :example:compileJava` |
| 2 | **Item 5 of the refactor:** `@NullMarked` everywhere, with the NullAway sweep. **Done in ADR-0497** | L | turns Q3 into compile errors, fixed as they surface | `./gradlew check` |
| 3 | **Q2 without `Observable`:** annotations and dead checks | M | ~35 Qodana | `./gradlew check`, then a local Qodana run |
| 4 | **Q2's `Observable<T extends @Nullable Object>`** | M–L | ~15 Qodana | `./gradlew check` |
| 5 | **Q1:** the rule is ADR-0497's; apply it to the records Qodana still names | M | ~148 Qodana, fewer after item 5 | `./gradlew check`, a local Qodana run |
| 6 | **Q3's leftovers:** `RenderObject.appliedBox()` and the rest | M | ~34 Qodana | `:core:test :widgets:test :html:test`; `FrameBudgetTest` alone |
| 7 | **Configuration**, below; then regenerate the baseline | S | ~120 Qodana | a local Qodana run |
| 8 | **Q5–Q7 small fixes**, and Q4's three real ones | S | ~30 Qodana | `./gradlew spotlessApply check` |
| 9 | **C4, C5 test hygiene** | S | ~20 CodeQL | `compileTestJava check`, a local CodeQL run (expect ~300, nearly all answered kinds) |
| 10 | **Record it:** ADR-0341's table gets the 32 new instances and 10 new false-positive rows, and `docs/testing.md` §2's "settles at 163" gets the new number | S | — | `:build-logic:test` (`DecisionLogTest`) |

Batch 2 comes before the rest of the nullness work on purpose. Once a package
is `@NullMarked`, NullAway fails the build on what Qodana only reports, so
Q2 and Q3 are cheaper to fix after it.

## Configuration

- **`qodana.yaml`:** change `include: - name: UnusedDeclaration` to
  `- name: unused`. Size it with a run first, because on a library it may call
  a lot of public API unused. The inspection's visibility options limit that.
- **A profile** based on `qodana.starter` that:
  - gives `AutoCloseableResource` an ignore-list of owned and
    application-lifetime types: Backend, BackendWindow, Window, EventLoop,
    MediaPlayer, Playback, Font, FontFace, Icon, SdlGpuDevice, Shader,
    GpuTexture, GpuSampler, GraphicsPipeline, Popup and Subscription. This
    clears about 97 findings. Copy the exact option keys from a profile the IDE
    exports.
  - optionally has `EmptyStatementBody` treat a comment as content.
- **Exclude `OptionalUsedAsFieldOrParameterType`.**
- **Drop the dead exclusions** `LongMethod`, `OverlyComplexMethod` and
  `NonBooleanMethodNameMayNotStartWithQuestion`. None of them is in
  `qodana.starter`.
- **Keep** the path exclusions. No finding comes from generated code or
  fixtures.
- **CodeQL: no change.** ADR-0341 decided against excluding rules, and no query
  filter can tell a `_` binding by its name.
