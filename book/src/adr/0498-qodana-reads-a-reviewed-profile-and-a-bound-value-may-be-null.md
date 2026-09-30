# 498. Qodana reads a reviewed profile, and a bound value may be null

Date: 2026-09-30

## Status

Accepted. Carries out `docs/static-analysis-plan.md`, item 6 of
`docs/refactor-2026-09-30.md`. Applies
[ADR-0497](0497-every-package-says-what-it-is-and-is-null-marked.md)'s rule for
records everywhere Qodana found the old form, and adds to
[ADR-0341](0341-codeql-findings-are-fixed-where-real-and-answered-where-not.md)'s
table rather than replacing it.

## Context

The plan was written against `5878e577`, where Qodana reported 408 findings. By
the time it was carried out, item 5 of the same refactor had marked every
package `@NullMarked`, and a run at `6dd0cde9` reported **683**. The growth was
entirely `ConstantValue` (127 to 345) and `DataFlowIssue` (107 to 163). Under
`@NullMarked` every parameter is non-null by contract, so every
`x = x == null ? DEFAULT : x` in the 128 newly marked packages became a check
IntelliJ calls dead. 351 of the 508 nullness findings were inside 114 compact
record constructors.

Three other things the plan found needed a decision rather than a fix:

- **`qodana.yaml` asked for an inspection that does not exist.** It included
  `UnusedDeclaration`, and the Java inspection's ID is `unused`. It had never
  run. Turned on as written, it reported 445 findings, 242 of them public
  methods of a toolkit whose public API mostly has no caller in its own
  repository, and 70 of those were the `inflate` method the woven catalog calls.
- **101 `AutoCloseableResource` findings were one false positive.** A getter
  hands out a `Font`, a `Backend` or a `MediaPlayer` that its owner closes, and
  the inspection reads every such call as a leak.
- **`Observable<T>` said its value was never null.** A model field that is not
  loaded yet is null, and so is a `Property` made empty. Five `case null` arms
  and a dozen `value == null` checks on bound values were reported as dead,
  and they are exactly the checks a reader of a binding needs.

## Decision

**Qodana's profile is a file in the repository, `config/qodana/profile.yaml`,**
based on `qodana.starter` and named by `qodana.yaml`. It changes four things,
each with its reason beside it:

- `unused` is on, limited to private and package-private declarations. Public
  API is the toolkit's product, and "no caller here" is not "dead". The entry
  points the build reaches without a Java call are declared in
  `.idea/misc.xml`, where the IDE reads them too: `@Bind` and `@Action`
  members, and every widget's `inflate`.
- `AutoCloseableResource` ignores the types a window, a backend, a player or a
  tree owns, or that live as long as the application. The list keeps
  IntelliJ's own defaults, because setting the option replaces them.
  `Subscription` is **not** on it: the plan listed it as owned, and one of its
  two findings was a real leak, below.
- `OptionalUsedAsFieldOrParameterType` is off. It is a style opinion this
  codebase decided against.
- `EmptyStatementBody` counts a comment as content.

The dead exclusions `LongMethod`, `OverlyComplexMethod` and
`NonBooleanMethodNameMayNotStartWithQuestion` are gone. None of them is in
`qodana.starter`.

**A false positive is answered where it is, never in the profile.** A
`//noinspection` comment or `@SuppressWarnings` on the narrowest declaration,
with the reason in a sentence beside it. IntelliJ's suppression ID is not
always the rule ID the SARIF shows: `MismatchedArrayReadWrite` is suppressed as
`MismatchedReadAndWriteOfArray`, and `AutoCloseableResource` as `resource`.

**`Observable<T extends @Nullable Object>`,** and `Property` and `BoundField`
with it. `Validator.of` takes a predicate over `@Nullable T`, because a
validator is asked about a field with nothing in it. `Validator.parsing` takes a
parser that may answer null, as its documentation already said. NullAway does
not check type-argument nullness in this build, so no caller had to change.
IntelliJ does check it, and in three places it reads the nullable bound instead
of a declared non-null argument (`Property<List<Overlay>>`). Those three carry a
suppression that says so.

## Consequences

- Qodana went from 683 findings, 676 of them high, to 249, none of them high:
  247 unused declarations at weak-warning severity and two `while` loops the
  plan leaves alone. The baseline was regenerated from that run, so the gate
  starts clean.
- ADR-0497's record rule is now applied everywhere, not only where NullAway
  asked for it. 121 files were rewritten by a script, then formatted and
  compiled with NullAway on. The script handles a qualified type
  (`Outer.@Nullable Inner`), an array or varargs, and a component comment that
  contains a comma.
- **Three real bugs came out of it:**
  - A `Content-Range` or `Range` with more digits than a `long` or an `int`
    holds threw `NumberFormatException` out of `HttpIO` and the showcase's
    server. The reader now fails with an `IOException`, and the server reads an
    over-long bound as past the end, which is what RFC 9110 means by it.
  - The launcher subscribed every model's restyle and repaint listeners and
    never closed the subscriptions. A model that outlived one launch went on
    asking a closed window for frames. They are closed in `shutDown`, and
    `Models.frameListenerCount` lets a test say so.
  - An `@Action` taking a `double` and handed null threw a bare
    `NullPointerException` out of `Double.valueOf`, while one taking an `int`
    refused it by name. Both now refuse it by name.
- `Downloader` and `AssetCache` are `AutoCloseable`, so the standard
  downloader's `HttpClient` is closed. `PngEncoder` closes its `Deflater` with
  try-with-resources, which JDK 24 made possible.
- `Subtitles.parse` lost its unused `format` parameter. `:media` is published
  as a snapshot only, so no release had it.
