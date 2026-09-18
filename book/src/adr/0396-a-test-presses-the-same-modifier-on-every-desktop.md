# 396. A test presses the same modifier on every desktop

Date: 2026-09-18

## Status

Accepted. Fixes Snapshot run 15, whose macOS verify leg lost 3 tests in `:core`
and 22 in `:widgets` after [ADR-0378](0378-the-desktops-own-modifier-has-a-name.md)
landed.

Follows ADR-0378, which is the decision this one keeps testable.

## Context

ADR-0378 put the toolkit's editing accelerators — select-all, copy, cut, paste,
undo, redo — on the platform primary modifier: `Cmd` on macOS, `Ctrl` everywhere
else, read once from `os.name` by `PrimaryModifier.current()`. That is right for
an application. It was wrong for the suite, silently, on one platform.

Fifteen test files type `Modifiers.of(Mod.CTRL)` into a field and expect a
selection, a clipboard or an undo. On Linux and Windows `Ctrl` *is* the primary
modifier and nothing changed. On the macOS runner it is not, so `Ctrl+C` became a
keystroke the editor does not answer:

```
TextInputTest > the clipboard > copy puts the selection on the session's clipboard FAILED
    expected: <Goldberry> but was: <>
TimePickerTest > the field is the source of truth > clearing it reports null rather than nothing FAILED
    expected: <2> but was: <1>
```

Twenty-five failures, one cause, and every one of them green on the machine the
change was written on. ADR-0378 had already provided the override —
`-Dgoldberry.input.primary=ctrl|meta`, "for a test and for an application that
has a reason" — and nothing set it.

Two fixes were possible. Every test could ask `PrimaryModifier.current()`
instead of spelling `Mod.CTRL`, so the suite would press `Cmd` on macOS and
`Ctrl` elsewhere. Or the build could pin the answer, so the suite is one suite.

## Decision

**The test conventions pin the primary modifier to `Ctrl`, and one test holds
the pin.**

- `goldberry.java-conventions.gradle` sets `-Dgoldberry.input.primary=ctrl` on
  every `Test` task. A test that presses `Ctrl` is the same test on every
  desktop, which is what a golden or a clipboard assertion needs to be.
- `PrimaryModifierTest` asserts the property is set and `current()` is `Ctrl`.
  It fails on every platform if the pin is dropped, rather than on the one
  runner where the twenty-five tests it protects would fail instead.
- What macOS resolves to *without* the override stays covered by
  `ShortcutTest`, through `PrimaryModifier.resolve(osName, override)` — the
  testable half ADR-0378 built for exactly this, which needs no macOS to run.

The tests were not rewritten to ask `current()`. A test named "Ctrl+A selects
everything" that pressed `Cmd+A` on one runner would be a test whose name and
body disagree on that runner, and the platform-specific answer is one function
with three unit tests, not twenty-five integration tests run three times.

## Consequences

- Snapshot's macOS verify leg is green again; the other legs never saw the
  problem and do not change.
- A test that means to exercise the macOS mapping end to end — a `text-input`
  answering `Cmd+C` through the whole event path — has to say so by setting the
  property to `meta` for that test's JVM. None does today; `EditKeysTest`'s
  "the accelerators are the same six on all three" asks `current()` and is
  therefore a `Ctrl` test under this pin, which is what it was before ADR-0378.
- `docs/testing.md` §1.2 records the pin beside the virtual clock, which is the
  same kind of thing: a source of platform variance the suite fixes so that an
  assertion means the same everywhere.
