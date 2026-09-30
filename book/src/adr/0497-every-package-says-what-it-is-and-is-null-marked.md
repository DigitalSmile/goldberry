# 497. Every package says what it is, and is null-marked

Date: 2026-09-30

## Status

Accepted. Finishes the JSpecify adoption `docs/testing.md` §2 began one package
at a time. It decides Q1 of `docs/static-analysis-plan.md`, how a record that
accepts null for a default says so.

## Context

Of 215 main packages, 122 had no `package-info.java`. Six more had one without
`@NullMarked`. That mattered for more than documentation. NullAway runs in
`OnlyNullMarked` mode, so a package without the annotation is not checked at
all. Nothing required a new package to be marked, and the adoption stopped at
103. The Qodana triage of the same day found the result: most of its 408
findings were nullness in the packages NullAway never saw.

Three tools that were meant to make the sweep mechanical did not work, and each
failure looked like success:

- **`tools/nullness/sweep.sh` compiled in `warn` mode and counted `error:`
  lines.** It reported "clean" after one pass on a module with a hundred
  findings.
- **It compiled with Gradle's up-to-date check.** A cached compile prints no
  warnings, so an already-built module looked clean even with the first fix.
- **javac prints at most 100 warnings and then stops, silently.** Every
  warn-mode count was capped. `:core`'s "95" was 154 and `:widgets`' "89" was
  353.

## Decision

**Every package that has a class in it has a `package-info.java` whose doc
comment says what the package is for.** That covers `src/main/java`, and
`src/testFixtures/java` where the package is not also a main one. The comments
describe roles, per ADR-0172, and say whether and to whom the package is
exported.

**Every package of a module under the conventions is `@NullMarked`.** The build
tools (`:assets`, `:weaver`, `build-logic`) run no Error Prone, so their
`package-info` files document without marking, and do not claim a check that
never runs.

**`PackageInfoTest`** (build-logic) keeps both true. It fails on a package with
no `package-info.java`, on one without a doc comment, and on a package in a
module under NullAway that is not `@NullMarked`. On its first run it found the
six unmarked files nobody had listed.

**A record that takes null for a default says so in an explicit canonical
constructor** (option A of Q1). The component stays non-null, because the field
never holds null, and the constructor's parameter is `@Nullable`:

```java
public Toast(String text, @Nullable String label, @Nullable Runnable onPress, @Nullable Duration timeout) {
    Objects.requireNonNull(text, "text");
    timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    …
    this.timeout = timeout;
}
```

A component that stores null and returns it (`Toast.label`, `Progress.source`)
is `@Nullable` on the component itself. The compact-constructor form had to
pick one lie. Either the parameter was non-null, so every `new Card(…, null)`
and every null from markup was a finding and the default was dead code by
contract, or the component was nullable, so every reader was told to check for
a null that could not arrive. The explicit constructor is javac's way of
declaring the two sides separately. It was spiked on `Toast` against javac and
NullAway before the rule was adopted.

**A `:natives` parameter that takes null is `@Nullable` there**, although
`:natives` runs no Error Prone (its build script says why). The annotations are
its contract for the modules that do. `:core`'s NullAway read
`YogaNode.setMeasureFunction`, `SdlTray.open`/`icon`, `SdlTrayItem` and
`SdlFileDialogs.show` as non-null and needed seven suppressions. With the
parameters annotated upstream the suppressions are gone. `:natives` now
`requires transitive static org.jspecify`, as every other module does, so
`-Xlint:exports` accepts an annotation on an exported signature.

**The sweep works.** `sweep.sh` normalises NullAway's warnings before counting
and compiles with `--rerun`. The conventions raise `-Xmaxwarns` and
`-Xmaxerrs` in `-Pgoldberry.nullaway=warn` mode.

## Consequences

- NullAway now checks every package it can see. The findings marking
  surfaced were resolved without behaviour changes, module by module, upstream
  first. `:core` had about 154:
  - about 69 parameters and 29 returns annotated `@Nullable` where the code
    already handled null
  - about 11 fields made `@Nullable`
  - 27 `requireNonNull` calls stating an invariant. Thirteen of them go through
    a new `RenderObject.appliedBox()`.
  - seven wrong annotations from the auto-annotator corrected, among them
    `Paragraph.layout`, which never returns null
  - `NullAway.Init` on `Launcher`'s six fields that `run()` creates

  No real bug could happen today. One latent one was made explicit:
  `Window.repaintIfRestyled` assumes a pointer router, which both callers
  install first.
- `:widgets` had about 353 findings, `:example` 42, `:html` 5 and `:media` 1.
  They were resolved the same way. `docs/refactor-2026-09-30.md` has the
  breakdown. **One real bug came out of it**: `Accelerators.unbind` passed a
  null owner down, which the router reads as "only keys nobody owns", although
  its contract is "whoever holds those keys now". It removes the key outright
  now.
- `Popup.focusOn`, `Attributes.id`, `Box.painting` and `TraySpec.tooltip`
  documented or stored null and did not declare it. They do now, and the last
  suppression that stood in for them is gone. What remains is `NullAway.Init`
  on fields a lifecycle method sets: `Launcher`'s six, two text states, and the
  showcase's screens.
- Public signatures that now say `@Nullable` are listed in the sweep's record
  in `docs/refactor-2026-09-30.md`. Each widens a contract the code already
  honoured. `Host.popup`'s `fit` is one of them, so an implementation must
  match.
- `docs/static-analysis-plan.md` Q1 is decided. Its batch 5 is now a sweep with
  a known rule rather than a spike.
