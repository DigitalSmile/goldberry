# ADR-0548: Four CodeQL queries that only report false positives are excluded

- **Status:** Accepted
- **Date:** 2026-10-03
- **Amends:** [ADR-0341](0341-codeql-findings-are-fixed-where-real-and-answered-where-not.md),
  whose "nothing is excluded from the suite or the scan" this replaces for four
  queries
- **Relates to:** `docs/codeql-2026-10-03.md`, `docs/testing.md` §2

## Context

ADR-0341 kept the whole `security-and-quality` suite and answered every false
positive in a table instead. Three triages later (2026-09-14, 2026-09-30,
2026-10-03) the dashboard has 352 findings, none of them real, and the count
grows with the code rather than with defects: 151 `_` locals on 2026-09-30, 178
on 2026-10-03. Four queries produce 315 of the 352:

| Query | Findings | What every one of them is |
|---|---:|---|
| `java/local-variable-is-never-read` | 178 | An unnamed `_` (JEP 456), which CodeQL 2.27.0 still models as a local with no reads |
| `java/unused-parameter` | 114 | A signature someone else fixed: 73 `inflate` contracts, 25 C and Objective-C upcalls, documented interface parameters, `_ ->` lambdas |
| `java/internal-representation-exposure` | 17 | A constructor that copies with `List.copyOf`, `Set.copyOf`, `Map.copyOf` or `clone()` before it stores; the query does not follow the reassignment |
| `java/missing-case-in-switch` | 6 | A multi-label `case A, B ->` arm, read as one label |

A dashboard that is 90% answered noise is one nobody reads, which is the
failure ADR-0341 set out to avoid.

## Decision

**`config/codeql/goldberry.qls` is `security-and-quality` less those four
queries,** and both `codeql.yml` and `docs/testing.md` §2's local recipe run it.
A query is excluded only when both hold:

1. every finding it has made here, across three triages read site by site, was
   a false positive, and
2. its real case is caught by a check that blocks a build:
   - a named local nobody reads, or an unused parameter of a private method, is
     Error Prone's `UnusedVariable`, which `-Werror` makes a failure;
   - a missing enum case is Error Prone's `MissingCasesInEnumSwitch`;
   - a collection stored without a copy is ADR-0497's constructor rule, and
     `ImmutabilityTest` is what fails.

The other queries stay, including the ones whose current findings are all
answered (`uncaught-number-format-exception`, `random-used-once` and five
singletons, 37 findings). Those kinds have found real bugs here, 12 of them in
the first triage, so the right tool for a false one is dismissing that alert on
GitHub, not excluding the query. Tests stay in the scan, for ADR-0341's reason.

`CodeQlSuiteTest` in build-logic holds the wiring: the workflow and the recipe
name the suite file, the suite builds on `security-and-quality`, and it excludes
exactly these four. Excluding a fifth is a change to that test and to this
record.

## Consequences

- The dashboard goes from 352 to 37 on the next scan, and a new finding is again
  something to read.
- **`:natives` loses a check.** Error Prone is off there (its build script says
  why), so a named local nobody reads, or an unused private-method parameter, in
  `:natives` is now caught by nothing. That module is FFM bindings and upcalls,
  where every unused parameter so far has been a C signature, so the loss is
  small, but it is a loss.
- `unused-parameter` also covered unused parameters of non-private methods,
  which no blocking check does. On this codebase every one so far was a
  contract.
- In-source suppression (`// codeql[...]`) was not used: CI's suite does not run
  the alert-suppression queries, and nobody here could check how the dashboard
  treats a suppressed result.
- When CodeQL learns JEP 456, `local-variable-is-never-read` is worth putting
  back: its `_` findings would go, and it would cover `:natives`.
