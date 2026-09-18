# 397. A benchmark's names are resolved under check

Date: 2026-09-18

## Status

Accepted. Fixes the Nightly `Benchmarks` job, red since 2026-09-12.

Keeps [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md), which is why a
benchmark here asserts no timing: what moves to `check` is a *name*, not a
number.

## Context

`BindingBenchmark.showcaseModel` times the showcase's own `app.click` through
the action registry. On 2026-09-12 (`a90c3096`) the showcase's actions moved
out of `ShowcaseModel` into a nested `ShowcaseModel.Actions` record, which is
the right shape (ADR-0137) and which every screen and test in `:example` was
updated for. The benchmark was not:

```
the binding schema, before and after > the showcase's own model, end to end FAILED
    java.lang.IllegalArgumentException: no action named "app.click" is bound. Bound: (none)
```

Nothing caught it for six days, because nothing could. A benchmark is tagged
`benchmark`, `check` excludes the tag (docs/testing.md §1.5), and the only
thing that runs it is the nightly lane — whose failure is one more red badge on
a job people read when they are looking for numbers, not for breakage. A
rename in a class the benchmark reaches into is invisible to every push.

## Decision

**The names a benchmark resolves are resolved under `check` by a test beside
the code, as a count.**

- `BindingBenchmark.showcaseModel` resolves `app.click` on
  `new ShowcaseModel.Actions(model)` and reads `app.clicks` off the model, which
  is the pair `Showcase` publishes.
- `ShowcaseActionsTest.theRoadsClickCounts` resolves the same two names under
  `check` and asserts that two clicks count two. It is a count, so the reason
  timings stay out of `check` (docs/testing.md §1.5) does not apply to it; and
  it is the benchmark's setup line by line, so the next rename fails the push
  that made it rather than the nightly that follows.
- The benchmark's doc names the guard and the guard names the benchmark, so
  the pair is found from either end.
- Both hold the `Actions` record in a local and fence it with
  `Reference.reachabilityFence`: a binding is a weak window onto its model
  (`RuntimeBinding`), and an actions record built inline was collected
  half-way through the benchmark's loop the first time this was run.

The general rule this states: **a benchmark's scaffolding is not exempt from
`check`, only its measurement is.** Where a benchmark reaches into a class by
name — an action, a binding path, a resource, a widget type — the name belongs
in a `check` test too, and the benchmark should say which one.

## Consequences

- Nightly's `Benchmarks` job is green again on the next run; nothing about the
  numbers it prints changes.
- Other benchmarks that resolve names by string (`BindingBenchmark`'s
  `app.say`/`app.noop`/`app.label` on its own local models, and the `:weaver`
  scheme benchmarks on the test models beside them) resolve names on classes
  in the same file, so a rename there is a compile error and needs no guard.
  The showcase case was the only one reaching across.
