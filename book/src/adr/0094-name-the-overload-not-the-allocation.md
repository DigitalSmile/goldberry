# ADR-0094: Name the overload, not the allocation

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §11, extends [ADR-0093](0093-an-application-is-a-root-widget.md)

## Context

Two complaints about the same file, and they turned out to have different
answers.

**The showcase was one 770-line class** doing four unrelated jobs: the
application lifecycle, the view model, the widget tree, and the layout of three
panes. Every screen was a private method on one state object, and the state
object held the model.

**And building a tree read badly** — `new Row(new Text(…), new Spacer())`, nested
four deep. The suggestion was `Column.of(…)` instead of `new Column(…)`
throughout.

The second is worth taking apart, because the obvious fix is the wrong one.

## Decision

**`new` stays the way to build a widget.** Static factories are added only where
a *constructor cannot carry the meaning* — which was already this catalog's rule
(`Progress.sweeping()`, `Badge.of(fallback, source)`, `Scale.decibels()`) and is
now applied consistently.

Three arguments decided it:

**A public record's canonical constructor cannot be hidden.** The JLS requires it
to be at least as accessible as the record. So `new Column(…)` is public forever
and `of()` could only ever be *additive* — two permanent public doors, with no
way to steer anyone to one and no compiler help keeping them in step. Every other
argument is downstream of that.

**The noise is depth, not the keyword.** `Row.of(Text.of("a"))` saves four
characters against `new Row(new Text("a"))`. What actually made the showcase hard
to read was a 100-line `build()`, and decomposition fixed it — the file is 175
lines now and the deepest nesting in it is two.

**Performance is not a reason either way, and was measured rather than assumed.**
20 million allocations, best-of-15, on the JDK 25 toolchain:

```
new  45.23 ms   of  45.09 ms
new  45.44 ms   of  45.86 ms
new  45.09 ms   of  45.34 ms
```

Identical within noise, ~2.3 ns each. `-XX:+PrintInlining` says why:
`Box::of (10 bytes) inline (hot)` — the factory is inlined and the machine code
is the same. Memory is identical too: the same object, the same allocation,
nothing extra. The only costs are startup-side — one extra bytecode frame before
the JIT warms up, and ~45 more methods of metadata across the catalog — and both
are negligible. A factory only changes *memory* behaviour if it caches, which
would be a behaviour change and is unsafe for keyed widgets.

(The first attempt at that benchmark reported 87 ms against 46 ms and was
entirely wrong: it had a `String.equals` branch inside the loop. Recorded because
it is exactly how a microbenchmark lies, and because the wrong number pointed the
"right" way.)

**What does get named is the ambiguous overload.** `Slider` had two
five-argument constructors differing only in whether the fourth parameter is a
`double` or an `Observable`; `Knob` and `Toggle` had the same shape. A reader
cannot tell those apart at a call site and the compiler will pick one for a
`null`. Those four are now `Slider.of`, `Knob.of`, `Toggle.of` and `Progress.of`
— `of` because the catalog already used it for exactly this meaning, the bound
variant.

**The showcase is five classes and two documents.** `Showcase` is the
[Application] — lifecycle, stylesheets, registries, accelerators. `ShowcaseModel`
is the view model: properties, the methods that change them, and the two
registries a document resolves names against. `ui.Screen` is the layout,
`ui.Panes` loads the documents, `ui.Content` is the one pane that must be Java.

**`titlebar.kdl` and `sidebar.kdl` carry everything declarative**, which is the
first time §9's markup path runs in a *window* with all three registries live:
`bind=`, `change=`, `press=` and `icon=` all resolve against what
`ShowcaseModel` and `Showcase` register, and all three registries are strict, so
a typo fails at inflation with a line and column.

## Alternatives considered

**`of()` on every widget.** The user's original suggestion, and the honest reason
to want it: it reads lighter. Rejected on the canonical-constructor constraint
above — it cannot replace `new`, only join it.

**`of()` on the containers only** — `Row`, `Column`, `Panel`, the ones that
actually nest. Half the churn for most of the visual gain, and it leaves the
catalog inconsistent about which widgets have it, which is the worst of the three
outcomes: a reader has to remember rather than know.

**Keep the panes as private methods and just shorten them.** The smallest change.
Rejected because the four jobs in that file have four different lifetimes — the
model outlives the screen, the screen outlives a pane, and the documents outlive
the process — and a private method cannot express that.

**Put the whole window in KDL.** Tempting, and it fails on `Content`: its Undo
and Reset buttons are disabled when the click count is zero, and §8's markup has
no expressions. A document that could evaluate `clicks == 0` would be code in a
data file with no stack trace when it went wrong ([ADR-0062](0062-bind-is-a-path-and-nothing-else.md)).
The boundary is instructive and it is where the split was drawn.

**Keep the model on the element.** [ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)'s
`State` is for what the *UI* remembers — a scroll offset, a caret — and it is
right that those die with their widget. The click count and the gain are what the
application is *about*; a second screen showing the same gain should read the
model, not a copy.

## Consequences

**The showcase is a shape an application can copy.** 175 lines of application,
209 of model, 188 of UI across three classes, 100 of markup. It was 770 lines in
one file.

**Markup has window coverage for the first time.** `sidebar.kdl` builds every
control the catalog ships, and `ShowcaseDocumentsTest` asserts the shape rather
than trusting that a window opened — an empty document inflates to an empty
column and paints a blank panel, and the three-frame headless run would pass.
The test also asserts the *bindings reach the model's own properties*, which a
shape assertion misses entirely: a `bind=` resolving to nothing still renders a
control, and the control renders perfectly and never moves.

**Four constructors became factories, and the tests had to be repointed.** Which
is the argument for the change: the compiler could not tell those overloads apart
either, and a regex that assumed `new Toggle(label, x, y)` meant the bound one
mis-rewrote seven boolean call sites before the compiler caught it.

**The catalog now has four ways to build a widget** — constructor, markup,
attribute chain, and named factory. That is one more than [ADR-0093](0093-an-application-is-a-root-widget.md)
left, and the ceiling: the rule is that a factory exists only where a constructor
is ambiguous, and `ChainingTest` plus the parity tests hold the other three in
step.

**A second `opens` was needed**, for `…example.ui`. JPMS works at package
granularity, so an application that keeps documents beside more than one class
opens more than one package. The improved error message from ADR-0093 named the
missing line exactly, which is the first time that message earned its keep.

**`ShowcaseModel` carries two `Runnable`s** — `onChanged` and `onRestyle` — which
is a small observer wiring an application has to do by hand. A `Property` the
launcher watched would remove it, and would also mean the toolkit deciding what
counts as a restyle. Left as it is, and named here as the seam to revisit if a
second application writes the same two lines.
