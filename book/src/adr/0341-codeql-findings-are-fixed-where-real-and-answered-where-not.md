# ADR-0341: CodeQL findings are fixed where they are real and answered here where they are not

- **Status:** Accepted
- **Date:** 2026-09-17
- **Relates to:** `docs/testing.md` §2

## Context

`codeql.yml` (ADR-0338's advisory half) ran its first scheduled analysis on
2026-09-14 over commit `558e01d0`, with the `security-and-quality` suite on
CodeQL 2.27.0. The dashboard filled with 184 findings in tracked files. Read one
at a time they are four kinds of thing, and the kinds need different answers:

| Kind | Count | What they are |
|---|---|---|
| Security: `comparison-with-wider-type` (severity 8.1) | 4 | An `int` loop counter run against a `long` or `double` bound |
| Correctness: index bound, null path, inherited-call, `NumberFormatException` | 22 | Twelve real, ten in code whose input is validated first or where a crash *is* the answer |
| Quality: never-read locals and unused parameters | 142 | 61 pattern bindings named `ignored`; 81 parameters, of which 71 are contract signatures |
| Test smells: empty container, unused container, useless null check, `new String` | 4 | Two vacuous assertions, one GC anchor, one deliberate non-identical string |
| False positives: representation exposure, missing switch case | 12 | Compact record constructors that already `List.copyOf`; multi-label `case A, B ->` arms the query reads as one label |

The alerts themselves are behind the code-scanning API, which needs a token even
on a public repository. Nothing in the job log lists them. The way to *see* them
without an account is to run the same CLI and suite locally, which is what was
done, and what §2 now records.

Two things made this a decision rather than a cleanup. The workflow's own
comment says the wider suite was chosen because "a false positive costs nothing
but a read" — so silencing rules to make the dashboard green would reverse a
choice already made. And 142 of the findings are one idiom: a binding the code
does not use, spelled `ignored` because the language had no other spelling when
they were written. JDK 22 gave it one (JEP 456), and the toolchain is JDK 25.

## Decision

**Every finding is answered, and the answer is in code wherever the code can
carry it.** The four security findings and twelve correctness findings are
fixed. The `ignored` bindings become unnamed variables, `_`, which is what they
were saying. Dead parameters that were merely dead are removed. What remains on
the dashboard is listed below with its reason, and the reason is the whole of
the answer: **no rule is excluded from the suite and no path is excluded from
the scan.**

What was fixed:

- **Loop counters are as wide as their bounds.** `ScaleInvariance.resample`
  hoists its `Math.ceil` bounds to `int`s before the loops; `SdlClipboard`
  counts MIME types with a `long`; `TimeTicks` names the quantity it was
  comparing — how many labels a rung produces, as an `int` — instead of
  comparing a `double` ratio with an `int` budget.
- **`Path`'s coordinate stride is a switch over the six verbs**, not an array
  indexed by a masked byte. The array's bound was true and unprovable; the
  switch has no bound and refuses an unknown verb the way its callers already
  do.
- **`Transform.mix` takes each side from its own list or grows it from the
  other's identity** in one expression per side, so neither is ever null and no
  reader has to work out that `length` is the longer list's.
- **`ParagraphCache`'s eviction hook says `super.size()`.** The enclosing cache
  has a `size()` too, and an unqualified call inside the map read as either.
- **A `NumberFormatException` is turned into a refusal that names what was
  asked** where the text came from somebody: the launcher's `--frames=` and
  `--size=` flags, an SVG `points` list at build time (which icon, which token),
  a document's action argument (which action, what it was given), and the
  showcase's `md.toggle-task`.
- **Two test helpers lose a parameter nothing read**, `SdlTray.surfaceOf`
  loses an arena it never allocated from, and the `try` that opened it goes
  with it.
- **Two tests now assert what their names claim.** `ImmutabilityTest`'s
  "handlers defeat equality" asserted `other != null`; it asserts two handlers
  are two sliders. `SelectPopupTest`'s "selected row is marked" asserted
  against a list nothing wrote to; it asserts that choosing the current value is
  a request the handler hears.

What stays on the dashboard, and why:

| Finding | Where | Why it stays |
|---|---|---|
| `local-variable-is-never-read` on `_` | 61 | The binding *has* no name now, which is the language's own spelling of "never read". CodeQL 2.27.0 — and, as of this record, its `main` branch — still models an unnamed pattern variable as a local with no reads, so the count does not move until the query learns JEP 456. Reverting to `ignored` would be the same count with a worse spelling |
| `unused-parameter` on `_ ->` lambdas | 7 | The same limitation: an unnamed lambda parameter is reported as `<anonymous parameter>` |
| `unused-parameter` on `inflate(node, children, wiring)` | 62 widgets | The factory signature is the markup contract (`@Markup`, ADR-0130); a `text` has no children and a `spacer` no wiring, and a parameter cannot be `_` |
| `unused-parameter` on upcall targets | `SdlTray`, `SdlEventWatch`, `MeasureCallback` | The signature is the C function pointer's; `userdata` and `node` are what SDL and Yoga pass, not what Java asked for |
| `unused-parameter` on interface methods with a documented parameter | `Handles.onFocusWithin`, `Input.caretOffsetIn`, `Input.onFocusChanged`, `Measure.measure`, `CodeEditor.focusChanged`, `AreaEditor.focusChanged`, `TextEditor.located` | An implementation that ignores `fromKeyboard` or `clip` is one that has nothing to draw differently; the parameter is there for the one that does |
| `internal-representation-exposure` | 9 record compact constructors | Every one already assigns `List.copyOf(...)` or `Set.copyOf(...)` in the compact constructor; the query does not follow the reassignment to the implicit field store. `ImmutabilityTest` is the real check |
| `missing-case-in-switch` | `MarkdownParser` ×2, `MarkdownWidgets` | The "missing" constants are the second and third labels of a `case A, B, C ->` arm; the query reads one label per case. Error Prone's `MissingCasesInEnumSwitch` is the check that would fire if a case were missing, and it is blocking |
| `uncaught-number-format-exception` | `CssTokenizer` ×2, `CssColor` ×2 | The digits are validated one character at a time before they are parsed, and `parseDouble` has no overflow; there is no input that throws |
| `uncaught-number-format-exception` in tests | 6 | A test that parses what it wrote should fail loudly if the text is not a number; a catch would hide the failure |
| `unused-container` | `BindingSchemeBenchmark.alive` | The list exists to be held, not read: it keeps models reachable so the population a benchmark line reports is exact |
| `inefficient-string-constructor` | `PropertyTest` | `new String("frost")` is the point: equal, not identical, is still unchanged |

## Alternatives considered

- **A `query-filters:` block excluding `java/unused-parameter`.** It would
  remove 78 findings and the three that were real with them, and it reverses
  the workflow's own reasoning about breadth. If the noise is ever the problem
  the fix is a filter scoped to the rule, and it is one line; it is not taken
  here because nothing has been read yet that the noise hid.
- **Dismissing the false positives in the GitHub UI.** Right for what they are,
  and the account holder can; a dismissal is not in the repository, so this
  record is what says why, and a re-scan on a new default branch would bring
  them back without it.
- **Renaming contract parameters to `unused`/`ignored`.** The query does not
  read names, so the finding stays and the signature reads worse.
- **Splitting `case A, B ->` arms to satisfy the switch query.** Duplicates
  the body or adds an empty arm to work around a query limitation; the compiler
  and Error Prone already have the exhaustiveness question.
- **`paths-ignore` for `src/test`.** Removes eleven findings and the two that
  found vacuous assertions. Tests are code.

## Consequences

- The dashboard settles at 163 findings — the local re-run says so — and every
  one of them is in the table above; 68 of them are the `_` spelling CodeQL
  does not read yet, and go the day it does. A *new* finding is therefore
  something to read, which is what an advisory dashboard is for.
- `_` is the spelling for a binding nothing reads, in type patterns
  (`case Close _ ->`), record patterns (`Link(var _, var _, var text)`), lambda
  parameters (`_ -> revalidate()`) and side-effect-only locals
  (`var _ = calls.showCursor().call()`). One cost, found the first time: palantir
  2.97 drops a **bare** `_` inside a record pattern and fails its own lint, so
  the record-pattern form is `var _`. A future formatter may allow the bare form;
  until then the convention is `var _` inside parentheses and `_` elsewhere.
- Four refusals have messages that name a flag, an icon's point list, an action
  or a document ordinal. Each is a small contract and each has a test.
- The `Path` stride is a `switch` on every replay, where it was an array read.
  It compiles to a jump table over six constants; `FrameBudgetTest` is the place
  a difference would show, and it was not re-run for this — it is a nightly
  number, not a gate.
- Reproducing the scan locally is a documented, tokenless path (§2), which is
  what makes the next triage a morning rather than a request for access.
