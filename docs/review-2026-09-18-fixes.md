# Answering the review of 2026-09-18

Working notes for the fixes that came out of [`review-2026-09-18.md`](review-2026-09-18.md).
The review is the record of what was found; this file is the record of what was
done about it, entry by entry, in the order §12 suggested.

The review's own numbering is kept — `C1`…`C22` for `:core`, `W1`…`W15` for
`:widgets`, `H1`…`H7` for `:html`, `N1`…`N7` for `:natives`/`:weaver`,
`B1`…`B12` for the build — so a row here can be read beside the paragraph that
produced it. Nothing in the review's text was edited; a fixed entry is answered
here rather than struck through there.

## How this is being worked

**Not one ADR per entry.** The `docs/gaps.md` batches take one ADR per entry
because each entry is a feature with a decision inside it. Most of the rows
below are defects, and "the loop should terminate" is not a decision. An ADR is
written where a fix *chose* between two defensible behaviours — cascade order,
what an unmounted element is told, what the weaver preserves — and the rest are
recorded here with the test that now holds them.

**One wave at a time, in the review's suggested order.** Parsers that hang or
crash first, then what a user can see, then the crash paths, then the weaver and
the build, then the cascade and the charts, then the tests, then the prose.

## Status

| Wave | What | State |
|------|------|-------|
| 1 | C1–C3, C22 parsers; W1–W3 tabs and the two editors; H1, H2 markdown; B1–B12 build and CI | **done** except N1–N3, which moved to wave 2 |
| 2 | N1–N3 the weaver; C7, C15, C16, C19, C20 — the pointer, the popups and the overlays | **done** |
| 3 | C4, C5, C10, C11, C21 — cascade, lint, damage, editing, the animation shorthand; W4–W8 — the scrollbar, the ticks and the charts | **done** (one golden blessed, reviewed as an image diff) |
| 4 | C8, C9, C17, C18 and the `EventSink` contract — the backends; C6, C12, C13 | **done** |
| 4b | H3–H7 the html module; W9–W15 and §11.5's parity sweep | **done** |
| 4c | C14, N4–N7 and the §4 tail | **done** (the native ABI is 12; the library was rebuilt and the suite re-run against it) |
| 5 | §6 and §11 — the tests | §11.5 and the `EventLoop` clock seam **done**; §11.1–11.3 running for `:core` and `:widgets` |
| 6 | §7 dead code and duplication; §8 the prose | §8 **done**; §7 running |

## 1. `:core`

| # | State | What landed |
|---|-------|-------------|
| C1 | **done** | The dispatch in `next()` sent *any* backslash into `identLike()`; a `\` that begins no escape is a `<delim-token>` now, per CSS Syntax 3 §4.3.1. It was an **OOM, not a hang** — the loop allocates a token per turn. `CssTokenizerTest$Escapes.backslashNewlineOutsideAString`, bounded at 500 ms. |
| C2 | **done** | `takeSlashdash()` skipped trivia to end of input and returned `true` regardless, leaving every caller to peek at nothing. It refuses a dangling `/-` with a positioned `KdlSyntaxException`. `KdlParserTest$Comments.danglingSlashdash`, four cases. Only `/-` *inside* a node crashed; a bare `/-` document already threw. |
| C3 | **done** | `Character.isSurrogate((char) code)` kept the low sixteen bits, so the test was applied to the wrong number. A private `isSurrogate(int)` leaves the spec's three cases exactly. `CssTokenizerTest$Escapes.supplementaryEscape`. |
| C4 | **done** | `Candidate` carries a sheet index beside the layer, and `CASCADE` compares it between layer and rule order — so no layer or specificity comparison can change. `StyleResolverTest.Cascade.sheetOrderWithinALayer` plus three tests pinning what must not move. Fixes `resolveStarting` for free. [ADR-0402](../book/src/adr/0402-a-sheet-has-a-position-in-its-layer.md). |
| C5 | **done** | `StyleResolver.substitutedFor(element, value)` substitutes a written value's `var()`s without cascading, so a loser is checked on its own value at its own line. `anOverriddenDeclarationIsCheckedOnItsOwnValue` (0 findings → 1), `aGoodDeclarationUnderABadOneIsQuiet` (2 → 1). [ADR-0402](../book/src/adr/0402-a-sheet-has-a-position-in-its-layer.md). |
| C6 | **done** | `rebind(String, Runnable)` removes the valued binding of the same name, which the `Consumer` overload always did. `ActionRegistryTest` is new — `:core` had no test for the class — and three of its assertions fail without the fix. |
| C7 | **done** | Narrower than the review prescribed: a widget the router has **disposed** hears nothing; the application's `Attributes` hook still finishes the pair it opened, which ADR-0327 added deliberately. `RehoverTest.theDeadAreNotToldTheyExited` and `anAncestorUnmountedMidDispatchIsSkipped`. [ADR-0401](../book/src/adr/0401-the-router-tells-the-living-and-finishes-the-applications-pair.md), which corrects ADR-0303. |
| C8 | **done** | The nanosecond-to-millisecond crossing truncated a sub-millisecond remainder to zero, which the branch below read as "poll". `waitMillis` ceils and caps, pinned as arithmetic because "did it spin?" is a stopwatch question. [ADR-0403](../book/src/adr/0403-the-events-a-failed-handler-never-saw-wait-for-the-next-pump.md). |
| C9 | **done** | Both "not a directory" and "a directory I cannot read" are `Optional.empty()`, which the comment beside the throw already claimed. `WaylandDecorationsTest.ReadingTheDirectory` makes a real unreadable directory. |
| C10 | **done** | `bounds` gained a four-argument overload taking the matrix to start from; the three-argument one stays `Affine.IDENTITY` and stays the layer path's, which is what it was written for. `DamageTest.underATransformedAncestor` and `aTransformThatChanged`. |
| C11 | **done** | `caretMoved()` invalidates, and only while something is composing — with no composition the shaping does not depend on the caret, and unconditional invalidation would reshape on every keystroke of a held arrow key. Three tests in `EditorPreeditTest`. The review misses `verticalBy`, which does it too. |
| C12 | **done** | Refused at the roots — a bounded `skip` that says how many bytes were named and how many are left, and a pixel ceiling stated in the format's own words — with `ArithmeticException` added to the translation net. `GifDecoderTest` is new; six assertions fail without it. |
| C13 | **done** | UTS #51: an `emoji_modifier_sequence` has emoji presentation whatever its base has on its own. Two tests in `ItemizerTest`, plus the counterweight — a modifier after a base that cannot take one is still its own run. |
| C14 | **done** | Three states — pending, fired, cancelled — rather than one boolean, marked **before** the action so a handler asking about its own timer is told it is firing. `EventLoopTimerTest.firedIsNotPending` and `cancelAfterFiring`. |
| C15 | **done** | `pointerReleased` cleared the anchor at the *end*, because `dispatch` reads it for the `RELEASED` and `CLICKED`; the no-target return jumped the tail. `endGesture()` is called on both ways out. `GestureAnchorTest.clearedWhenTheReleaseFindsNobody`. Reachable only after a `releasePointer()` mid-drag, since a press captures implicitly. |
| C16 | **done** | Guarded on `captured == null`, so the shape stays part of what the gesture decided. `CursorTest.leavingTheWindowMidDragKeepsTheShape`. |
| C17 | **done** | `volatile`, with what it does and does not order written on the field. **Two** off-thread readers, not the one the review names: `drawDuringModalLoop` is the second. `Sdl3EventPathTest.wakeupAfterCloseTouchesNothing` also asserts the declaration, which is the half a tidy-up would drop. |
| C18 | **done** | The wheel's own position fields go through `inTheWindowsOwnSpace` like motion, buttons and drops. Verified failing first: `expected: <-352.0> but was: <5000.0>`. |
| C19 | **done** | Each popup's own `dismissedByInput()` is added up, rather than asking whether everything is shut. `PopupLifecycleTest.aDismissalIsNotAClickWhileATooltipIsOpen`, driven through the real launcher and loop. |
| C20 | **done** | Boxes trace back through their `Element` to the child of the `WindowRoot` they descend from. A box-*less* child shifts placements; a box-*ful* one (a composition) is what overruns the list — and the content itself was assumed to produce exactly one box. `OverlayLayerTest.aBoxlessOverlayPlacesNothing`. |
| C21 | **done** | The `NUMBER` branch moved above the `<time>` one, which is CSS's own reading. `aBareZeroIsACount`, `aZeroDelayNeedsItsUnit`. No shipped sheet writes a bare number there. |
| C22 | **done** | The guard admitted only a *bare* identifier. It is `startsIdentifier() \|\| startsQuotedOrRawString()` now — raw keys are legal KDL 2.0 too, which the entry did not mention. `KdlParserTest$Values.quotedPropertyNames`. |

## 2. `:widgets`

| # | State | What landed |
|---|-------|-------------|
| W1 | **done** | `pendingReveal` is written in `build()`, not `select()`: a strip does not decide its own selection, so a bound value, `Ctrl+Tab` and an application list change all arrive as a rebuild. `TabRevealTest`, three tests. Note ADR-0120's "exactly one header per build carries the callback" is no longer true (ADR-0372/0377). |
| W2 | **done** | The cut moves back to a `BreakIterator.getCharacterInstance()` boundary — the same class a caret steps with. Lifted to `form/parts/MaxLength`. `MaxLengthTest` (9), plus paste cases in both controls. [ADR-0400](../book/src/adr/0400-a-clause-is-a-start-and-a-length.md). |
| W3 | **done** | `clauseEnd` is `clauseLength` throughout: SDL puts a length in the struct and both implementations always computed `start + length`. Lifted to `form/parts/Preedit`. Latent through today's only caller — `PreeditEvent.caret()` *is* the clause end — so the tests drive the seam directly. [ADR-0400](../book/src/adr/0400-a-clause-is-a-start-and-a-length.md). |
| W4 | **done** | The first `MOVED` read a position, which cannot encode where on the thumb the grab landed. It anchors the gesture now, as `SplitDivider` and `TableGrip` do. Invisible from **any** press at either end of travel, so the test grabs mid-travel. |
| W5 | **done** | `powers()` returns the powers on the axis and `every()` strides through those. `SPIKY` at 5 gets **four** labels, not five — four whole decades beat the 1-5 subdivision under the documented tie rule, which is the rule working. |
| W6 | **done** | `ZonedDateTime.plus` adds a date unit to the local date and a time unit to the instant, so days survived DST and hours did not. Both step a `LocalDateTime` and resolve into the zone, skipping a step that lands in a spring-forward gap. |
| W7 | **done** | One scale, built once from `points()` outside the series loop. `LineChartTest.oneScaleForEveryLine`. |
| W8 | **done** | The domain is asked twice — of the positives-only data and of the data as it came — so filtering happens only once the whole domain is known positive. |
| W9 | **done** | The entry holds its exit timer and `dispose()` cancels it. **Not** routed through `Departure`: that is a per-state singleton and a toaster departs once *per entry*; its continuation must run inside a `setState` because it reflows the survivors; and a toast reads no reduced-motion flag at all. `ToastTest.Unmounting`. |
| W10 | **done** | The state remembers which slide the pending timer was scheduled for, and whether the interval changed — decided in `build`, because for a **controlled** carousel the new index is not visible until the application has rebuilt. `TimedHost.handedOut()` is what tells a kept countdown from a restarted one; `pending()` is true either way, which is why every existing test passed. |
| W11 | **done** | Both the sizing and the clamp read one `content()` = `length − DIVIDER`. The old `minimums` asserted the fraction the clamp produced, so it agreed with the bug by construction; the new tests assert the **width**. |
| W12 | **done** | A floor remainder, `min − floor(min / step) × step`. Java's `%` truncates toward zero, so every negative `min` passed the test and a labelling stepping straight over zero was credited with it. `TicksTest.zeroIsNotCreditedTwice`. |
| W13 | **done** | The open-list guard is just `isOpen()` — `list.content()` is idempotent, so the other modes are unaffected. The blur now reports an exact match through `onChange` after `restore()`. Pinned in `SelectLoopTest` through the real launcher, because a `TestHost` opens no popups and there is no seam below the running app. |
| W14 | **done** | `stack` and `qr-code` are in `builtInTypes()` — and the sweeps that read it now read the whole registry instead (§11.5). |
| W15 | **done** | `Panel`'s compact constructor defaults a null `attributes` to `NONE`, as `Card` and `GroupBox` already did. The failure was an NPE from `id()` a frame later, inside the cascade, naming nothing useful. `PanelTest` is new. |

## 3. `:html`

| # | State | What landed |
|---|-------|-------------|
| H1 | **done** | Asks md4c for `task_mark_offset` rather than re-deriving the position with a pattern; converted from UTF-8 bytes to UTF-16 units. `TasksTest.NESTED` covers a four-space sub-task, a quoted box and a fenced one. [ADR-0399](../book/src/adr/0399-a-task-box-is-counted-by-the-parser-that-found-it.md). |
| H2 | **done** | `marker()` mints a word as a side effect; `item` now calls it only on the branch that keeps it. `SelectionTest.aTaskListHasNoPhantomBullet`. |
| H3 | **done** | Not `xOf` itself: a `Word` wrapping a child returns early from `render` and never reaches `WordGeometry.shaped`, so its paragraph stayed null. Such a word is measured **across its own rectangle** — exact at the ends, proportional between. Two tests in `SelectionTest`. The copied *text* was already right; only the wash was missing. |
| H4 | **done** | The block boundary moved from the top of `blocks()` into `flush`, guarded by a new `Prose.isEmpty()`. `SelectionTest.inlineAfterABlock`: `<div><p>one</p>two</div>` copies as `one
two`. |
| H5 | **done** | The Living Standard's "in cell" mode: a section closes an open cell. `caption`/`col`/`colgroup` are not in this parser's tables, and `table` is excluded because a nested table is legal. `HtmlTest.ImpliedCloses.sectionsEndCells`. |
| H6 | **done** | **The spec and this model disagree and the model won.** "In body" treats a stray `<tr>` as a parse error and drops the tag; `Element`'s own rule is that nothing is dropped for being unknown, and the fold draws a stray `p` in a list where it is. `HtmlViewTest.Builds.rowWithNoTable`. |
| H7 | **done** | The `ImageSource`'s identity is in the signature — one `identityHashCode` per **build**, not per block, because `signature()` is a field read. Stated price: replacing the source rebuilds the whole note once, and a source that answers differently without being replaced is not noticed. All three of the review's missing `BlockReuseTest` paths are written. |

## 4. `:natives` and `:weaver`

| # | State | What landed |
|---|-------|-------------|
| N1 | **done** | `transformMethod` with a `MethodTransform` hands each element on and replaces only the body, where the four-argument `withMethod` carried the name, descriptor and flags and nothing else. `MethodAttributesTest` reads the **woven bytes**, which is what `NativeImageComplianceTest.annotationRetention` could not. |
| N2 | **done** | A method is rebuilt only if it contains a write the weaver replaces; everything else is copied verbatim, frames included. And the weaver runs with the module's classes and their compile classpath on it. `UntouchedMethodsTest` fingerprints who wrote a method last by its Code attribute order. |
| N3 | **done** | `rewired()` answers for a woven model too, and a `goldberry$set$…` call counts as a write — the mirror case the review does not name: recompiling only the model re-wove it with private setters for an `IllegalAccessError` at the first click. `IncrementalWeaveTest`. **Latent until B2 landed**; the two are merged together on purpose. |
| N4 | **done** | Three `.calls` packages were missing, not two — `desktop.calls` is the third and **stays out**, because `PortalSettings` binds libdbus in a static initialiser and build-time initialising it bakes the build machine's D-Bus into the image. `NativeImagePropertiesTest` reads the **shipped resource** against `ForeignSurface.holderClassNames()`. |
| N5 | **done** | Four symbols gone; the list is 246 where it was 250. `ExportListTest` asserts set equality in both directions, composing Yoga's 26 length setters the way `StyleCalls` does, and fails if its own scan finds fewer than 200 bindings — so a dead regex fails loudly rather than agreeing with everything. |
| N6 | **done** | The shim reports all three structs field by field, plus the `WEBP_DEMUX_ABI_VERSION` row `WebpCalls` claimed the probe would notice. **The hand-counted numbers were not wrong**, only unverified — the fix is about who does the arithmetic. `LayoutVerificationTest` verifies 36 layouts where it verified 33. |
| N7 | **done** | `SdlSubsystem` gets a `nativeName()`, the registry folds the eight bits in, and the shim reports them. The `@CsvSource` of literals is replaced by `everyBitIsRegistered`, which is the step a *new* subsystem is actually forgotten at. |
| §4 tail | **done** | Six members with no caller deleted; two needless `assumeTrue` guards gone (they bypassed ADR-0016); a dozen comments corrected. **One was not a comment problem**: `YogaNode.free()` closed the measure arena *before* `nodeFree`, so Yoga briefly held a measure function whose upcall stub had been unmapped. The comment already described the right order. **ABI 11 → 12.** |

## 5. Build, CI and example

| # | State | What landed |
|---|-------|-------------|
| B1 | **done** | Depends on `tasks.matching { it.name == 'blessGoldens' }` per subproject — a live view. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B2 | **done** | The weave tasks declare a stamp file rather than javac's directory, so Gradle stops discarding the compiler's incremental state. `:widgets:compileJava` is up to date on a second build again. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B3 | **done** | `:emoji` forwards `goldberry.native.required` like `:core`, `:widgets` and `:html`, and joins the native leg on all three platforms. |
| B4 | **done** | The triptych lists gain `html/`, `emoji/` and (on linux) `example/`. |
| B5 | **done** | The nightly `coverage` job is gone: it was `linux.yml`'s `java` job step for step, and its comment described a scale sweep `-Pgoldberry.skipNative` cannot draw. |
| B6 | **done** | Two root tasks, `checkMarkdown` and `formatMarkdown`. Spotless refuses a target outside its project and the root cannot apply a build-logic plugin. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B7 | **done** | **The review's workaround does not work.** `requires static transitive` fails identically on PMD 7.19.0 and 7.20.0 — the grammar takes one modifier, and the order is not what it objects to. `module-info.java` is excluded from PMD on purpose, with the reason at the exclusion. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B8 | **done** | `AppMenu.Handlers` carries `startTour` and `about`; `Showcase` has an about box. `ShowcaseShellTest.eachRowRunsItsOwnHandler` presses every row and asserts which handler fired. |
| B9 | **done** | The last tour stop says thirteen screens and ten digits. Every other count in the example follows. |
| B10 | **done** | `:emoji` joins `jacocoAggregation`. |
| B11 | **done** | `--baseline` is gone; the file it named has never existed. |
| B12 | **done** | `pitestPlugin` removed; `pmd` and `pitestJunit5` are catalog entries now. |

## 6. Tests, §7 smells, §8 prose

| Item | State | What landed |
|------|-------|-------------|
| §6 should not run under `check` | **mostly done** | `FrameBudgetTest` is tagged `benchmark`, with the counting pair it is owed named at the tag. `EventLoop` now reads an injectable clock and `EventLoopTimerTest` runs on one — which removes a known wall-clock flake. `TooltipTest`'s sleeps are **kept**: what they measure is the loop's own timer, and its own comment says a test that measured it with itself would pass whatever it did. |
| §6 asserts nothing | **done** | Eight sites, each now asserting what its own name says. |
| §6 duplicated scaffolding | **open** | The one item not reached: `id(String)` in 29 `:widgets` test files, `find` in 14, `later` in 8, and the chart sixes. `:core` has a test-fixtures set and `:widgets` has none; that is the change, and it is a module-wide edit rather than a defect. |
| §7 dead code | **done** | The `:core` half: a painter is compared in `sameAppearance`, so a `canvas` whose painter changed is damaged (a real bug, with `DamageTest.thePainterChanged`); `partialRepaint`'s third conjunct and a comma strip that cannot fire are gone; `Transform.Origin.y` is not `@Nullable`; the motion block no longer runs for a node with no box. The rest followed: the four caller-less members, `DonutChart`'s unreachable guard, `WordGeometry`'s `size == 0` branch and the parameter it read, and `SelectableDocument.document`. **Nothing on the list turned out to still have a caller**, and `QrCache.encodings` is **kept** — identity cannot answer "did the encoder run", because `matrix()` returns the race winner either way, and `BlockMemo.kept()`/`built()` is the same pattern in `:html`. |
| §7 duplication | **done** | ~390 lines of triplicated chart wither become ~120 on a self-typed `ChartSpec`. `lerp` lifted where the copies were byte-identical (`css.value`), and the two tour copies turned out to be a duplicated *method* rather than a duplicated `lerp` — both now `Lit`. `FaceCoverage` goes through `TableDirectory.table`, which buys the bounds check it lacked. `HtmlWidgets.SKIPPED` is `Tags.isMetadata`. **`mix` in three is not duplication**: one is a documented one-line facade over `Oklch.mix`, one is an unrelated byte blend in `:example`. |
| §8 contradicts the code | **done** | README, NOTICE, THIRD-PARTY-NOTICES, releasing.md, TODO.md, design-system.md, applications.md, the example's counts, `gpu/module-info`, `book/src/native.md` (246 symbols, six upcalls) and `book/src/weaving.md`. `DecisionLogTest` now answers the ADR-status question the review answered by reading 397 files. |
| §8 stale doc comments | **done** | Ten more, plus `Paragraph.layout()` losing `@Nullable` and the dead null branches behind it in `Editor`, `DocumentLines` and `TextDocument`. `Clip.java` claimed `bl_context_save` is not exported; ADR-0193 exported it. |
| §8 comments on the wrong member | **done** | A dozen, with the members they had left undocumented now documented. Two were not misplacements: `Hud` carried the same doc comment twice, and `TreeRow` had a truncated `@param` list in front of prose that belongs before the full one. |
| §11.1 the five habits | **done** | `:core` 49 methods deleted, nine **kept** against the review with the reason at each. `:widgets` the seventeen per-widget registration blocks, the composition-node test written nine times and four withers, all now covered by the widened catalogue sweeps. `:natives` stops restating the enum literals the C probe already compares. |
| §11.2 parameterized merges | **done** | `:core` 61 methods → 13 tables, plus 11 more merged on the way; no input lost and several rows got *stronger*. The `:widgets` and remaining-module passes landed as far as they got. |
| §11.3 fragile assertions | **done** | Exception wording, `toString` formatting and the vacuous guards. `FrameSummaryTest.describe` stays exempt (ADR-0342 — a workflow greps it), and five sites listed under this habit already asserted a datum rather than prose and were left alone. |
| §11.5 the parity sweep | **done** | A test-scope `CatalogMarkup` exposes `Widgets.inflater().registered()` and a table of the *arguments* the §13 widgets refuse to exist without. Parity, immutability and chaining all read it. `WidgetParityTest` went from 40 tests to **203**. `DensityTest.SIZED` names all eight controls resolving `--gb-control-height`. |

## Records written

| ADR | What it decides |
|-----|-----------------|
| [0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md) | A task declares the files it owns and only those — the weaver's stamp, the prose tasks, PMD's exclusion, `blessGoldens` |
| [0399](../book/src/adr/0399-a-task-box-is-counted-by-the-parser-that-found-it.md) | `toggleTask` asks md4c where the box is; supersedes one sentence of ADR-0300 |
| [0400](../book/src/adr/0400-a-clause-is-a-start-and-a-length.md) | A preedit clause is a start and a length; a limit cuts on a grapheme boundary; both live in `form/parts` |
| [0401](../book/src/adr/0401-the-router-tells-the-living-and-finishes-the-applications-pair.md) | The router tells the living, and finishes the application's pair; corrects ADR-0303, extends ADR-0317 |
| [0402](../book/src/adr/0402-a-sheet-has-a-position-in-its-layer.md) | A sheet carries its index; the cascade compares it between layer and rule order, and the lint reads the loser's own value |
| [0403](../book/src/adr/0403-the-events-a-failed-handler-never-saw-wait-for-the-next-pump.md) | The code moves to the `EventSink` contract rather than the contract to the code; with C8, C9 and C17 recorded beside it |
| [0404](../book/src/adr/0404-a-memo-sees-the-source-a-picture-came-from.md) | An image source's identity is in the memo's signature; an unshaped word is measured across its rectangle; a stray `<tr>` keeps its cells |

## What the sweeps found, and what is now owed

The widened sweeps of §11.5 turned up four things the review did not name. Each
is recorded here rather than fixed quietly, and the two exemptions are **live** —
each sweep has a `theExemptionsAreLive` test, so an exemption cannot go stale or
start passing without anyone noticing.

- **`series` and `point` accept `id=` and `class=` and throw them away.**
  `ChartSeries` and `ChartPoint` have a CSS type and paint, and implement none of
  `Attributed` — the only place in the catalogue where markup takes an attribute
  and drops it. Exempted by name in `ChainingTest.NOT_CHAINABLE` and
  `WidgetParityTest.NO_ATTRIBUTES`, with the reasoning written out. **Owed: make
  them `Attributed`, or refuse the attributes at inflation.**
- **Parity's own walk was wrong, not `dialog`.** `styledNode` took the *first*
  styled node, which for a `dialog` is the `dialog-scrim` it wraps itself in —
  correct while the sweep covered ten primitives, a false failure over the whole
  catalogue. It looks for the node carrying the type now.
- **Six registered names describe no node at all**: `action` and `page` (a
  dialog's button and a wizard's step, drawn by their owner), `marker` (a chart
  annotation drawn on an axis), and `list`/`table`/`tree`, which inflate to
  `Bound` and deliberately describe `Widget.nothing()` until a model is bound.
  Exempt from parity with a written reason; still swept by chaining and
  immutability, which they pass.
- **`statistic` throws a bare `NullPointerException: label`** where every sibling
  raises a §13 `IllegalArgumentException` naming what is missing. **Owed: one
  line.**

And one the `:widgets` agent found next to W11: **`SplitPaneState.offsetOf()` and
`dragTo()` still convert pixels through `length`** while the fraction now
measures the content, leaving the drag anchor up to ~6 px × fraction out of step
with where the divider is drawn. It is a different defect from W11 and was left
alone rather than widening that change. **Owed: a review entry of its own.**

## Found while answering it, and not in the review

- **`Shadow.toString()` did not round-trip through `Shadow.parse`.** It printed
  the colour packed as `#aarrggbb` and CSS reads eight hex digits as
  `#rrggbbaa`, so `0px 2px 8px #40000000` parsed back as alpha zero —
  `Shadow.NONE`. Found by trying the round trip §11.3's habit implies. Fixed,
  with five assertions that fail without it.
- **`YogaNode.free()` closed the measure arena before `nodeFree`**, so Yoga
  briefly held a measure function whose upcall stub had been unmapped. The
  review listed this as a stale *comment*; the comment described the right order
  and the code did not.
- The four things the widened parity sweep found, above.

## What the review got wrong

Kept because a review is a record and being wrong in a traceable way is worth
more than being quietly corrected.

- **C1 is an `OutOfMemoryError`, not a hang.** The loop allocates a token per
  turn, so an unfixed tree dies in seconds. It matters for the test: a five-second
  bound never fires, because the heap fills first and takes the whole test
  executor with it.
- **C2's line reference is the call site.** `takeSlashdash()` contains no
  `peek()`; the unguarded one is in `node()`. The fix still belongs in
  `takeSlashdash()`, because `nodes()` calls it with the same assumption. And a
  bare `/-` document already threw the right exception — only `/-` inside a node
  crashed.
- **C22 is wider than quoted keys.** The guard also excluded *raw* strings, which
  KDL 2.0 admits as property keys.
- **B7's workaround does not work.** `requires static transitive` was tried
  against PMD 7.19.0 and 7.20.0 and fails identically. PMD takes one modifier;
  the order is not the objection.
- **H1's frame invites too small a fix.** The four-space indent is one symptom of
  three; a block quote is the same bug and the entry does not mention it.
- **C7's prescription is too broad.** "`emit()` has no `isMounted()` guard where
  `mark()` and `notifyFocus` do" is true of the widget handler and false of the
  `Attributes` hook in the same method. A guard over the whole of `emit` breaks
  `HoverHookTest` and contradicts ADR-0327 — silently, by dropping an exit the
  application is waiting for.
- **`WheelAndCaptureTest:832-844` does not exist.** The file is 369 lines and
  `releaseOnDescendant` is at 289–301. The other five citations in that paragraph
  are accurate.
- **C20's "or throws" needs a box-*ful* overlay.** A child contributing no box
  shifts the later placements; only one contributing *several* overruns the list.
  The same mismatch applies to the content itself, which the entry does not
  mention.
- **ADR-0080 and ADR-0081 do have a status.** They say `*Accepted, 2026-08-17.*`
  in italics rather than in a `**Status:**` bullet or a `## Status` section — one
  of three spellings in use across the log. `DecisionLogTest` now answers the
  question the review answered by reading 397 files.
- **C11 misses `verticalBy`.** `Up`, `Down`, `PageUp` and `PageDown` move the
  caret without invalidating, exactly as `pointerAt`, `move` and `caretTo` do.
- **N3 was unreachable through Gradle until B2 landed.** While the weave task
  declared javac's directory as its own output, `compileJava` re-ran on every
  build and the weaver always met a fully unwoven tree. The mixed tree N3
  describes becomes the normal case the moment the stamp change merges, which is
  why the two are on master together.
- **N3 has a second half the review does not name.** Recompiling only the *model*
  leaves the woven sibling's `putfield` already gone, so a weaver counting only
  `putfield`s concludes nobody writes to the model and re-weaves it with private
  setters — an `IllegalAccessError` at the first click, from a build that changed
  neither class's source.
- **The `EventSink` entry presumes its conclusion.** It frames the contract as
  false; the contract is the part worth keeping. The argument that the SDL events
  "have already been pulled out of the platform queue" does not show they are
  unrecoverable — it shows the backend is the only place they can wait.
- **C17 names one off-thread reader and there are two.** `drawDuringModalLoop`
  is the second, and the class doc three lines above the field says so.
- **W5's `SPIKY` gets four labels, not five.** Once the decade count is right, 3…30000
  has four decades against a target of five, and the documented "nearest, coarser
  wins a tie" rule prefers four whole decades to the eight the 1-5 subdivision
  would give. The golden to bless has four.
- **W4 is invisible from any press at either end of travel**, because the bad
  offset clamps back to where the view already was — a second reason `ScrollTest`
  never caught it.
- **Several line numbers in §6 do not resolve.** `HeadlessBackendTest.java:717-731`
  (the test is at 274–288), `EventSink.java:729` (the file is 18 lines), and
  `WheelAndCaptureTest:832-844` (369 lines). The findings behind them are all
  real; only the citations are off.
- **H3 is not located where the entry says.** Nothing at
  `WordGeometry.java:316-326` is wrong on its own; the defect is that a
  child-bearing `Word` never reaches `shaped`, and the fix has to decide what an
  unshaped word means. The copied *text* of a double-clicked link was already
  right — only the wash was missing — and an image was never affected, carrying
  no text.
- **H6 asks for something the HTML spec does not do.** "In body" treats a stray
  `<tr>` as a parse error and drops the tag. `Element`'s rule that nothing is
  dropped for being unknown is the repository's, not the standard's, and it is
  the one followed here.
- **H7's fix has a price the entry does not mention.** Identity in the signature
  rebuilds the *whole* note when the source is replaced, not only the blocks
  holding pictures.
- **`UiExecutor.drain` is the model, not a defect.** The entry pairs it with
  `Sdl3FileDialogs.deliverPending` as suppressing failures; it collects every
  one, logs each, and throws with the first as cause and the rest as
  `addSuppressed`. Only `Sdl3FileDialogs` needed fixing, and it was fixed into
  that shape.
- **The `BarChart` entry is incomplete and partly mis-framed.** `curve` and
  `logY` never lied — `BarChart.curve`'s own doc said a bar ignores it, and
  `ChartCurveTest.barsHaveNoCurve` asserts it in pixels. `markers` and `times`
  did. And `markers` is ignored by `area-chart` too, which the entry does not
  say.
- **W3 is latent through today's only caller.** `PreeditEvent.caret()` is defined
  as the clause end whenever a clause is reported, so the caret comparison caught
  every resize by accident. The contract is still wrong and bites the moment an
  event carries a caret of its own.
