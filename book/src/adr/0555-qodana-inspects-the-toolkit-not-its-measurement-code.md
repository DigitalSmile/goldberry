# ADR-0555: Qodana inspects the toolkit, not its measurement code

- **Status:** Accepted
- **Date:** 2026-10-03
- **Relates to:** `docs/ci-fixes-2026-10-03.md`,
  [ADR-0551](0551-benchmarks-are-a-source-set-run-one-at-a-time-by-their-own-workflow.md),
  [ADR-0498](0498-qodana-reads-a-reviewed-profile-and-a-bound-value-may-be-null.md)

## Context

Qodana's gate fails on any new high-severity finding. Its committed baseline has
no finding in any `src/test` tree, because IntelliJ imports a test source set as
tests, and the dashboard does not report on test code.

ADR-0551 moved the benchmarks and probes out of the test trees into a source
set of their own, `src/benchmark`. IntelliJ imports a source set it does not
know as production code. On the first push after that change, Qodana inspected
eight modules' benchmarks as toolkit code. It reported 85 new problems, 54 of
them high, and 51 of the high ones were in `src/benchmark`: data-flow warnings
on fixtures, `AutoCloseable`s a benchmark keeps open on purpose, and a busy wait
in a probe that measures one. `core/src/jmh` had been in the same position since
it was added. It produced no high finding only because it holds one class.

## Decision

**`qodana.yaml` excludes every source set that is neither the toolkit nor its
tests**, by name: `**/src/benchmark` and `**/src/jmh`. They are measurement code,
which the dashboard leaves out for the same reason it leaves out the tests.

**`QodanaScopeTest`, in build-logic, holds this.** It lists every
`<module>/src/<set>/java` in the repository and fails on a set other than `main`,
`test` and `testFixtures` that `qodana.yaml` does not exclude. The next source
set is added to the exclusions or the test fails. The gate does not find out on
`master`.

The other three high findings were in `:example`'s main code and were real.
`SecondWindowCard` now keeps the second window's close listener and drops it
with the card, and `BoundTextCard` no longer hands a possibly-null bound value to
a field. They are fixed, not excluded.

## Consequences

- The benchmarks are still compiled by `check` under the same Error Prone and
  NullAway settings as before. Qodana is the only analyser that stops reading
  them.
- A finding in measurement code is not reported anywhere. That was already true
  of every benchmark while it lived in a test tree.
