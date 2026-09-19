# 405. `check` generates the published javadoc

Date: 2026-09-19

## Status

Accepted. Completes [ADR-0343](0343-the-published-javadoc-is-linted.md), whose
last consequence — "the release's last step cannot be the first to find one" —
was not true as written.

## Context

Snapshot run 17, on `1deba933`, went red in `publish / Maven Central (snapshot)`:

```
widgets/.../data/linechart/LineChart.java:106: error: reference not found
/// [ChartSpec#fill] puts a flat wash or a fade beneath the data — `charts.md`
1 error
> Task :widgets:javadoc FAILED
```

`ChartSpec` has no `fill`, and says so itself: its own class comment reads "the
one knob that is **not** here is `fill`", and `LineChart.fill` carries a
paragraph explaining why it is declared on the chart rather than the interface.
The link wanted `[#fill]` — this type's own member, which is what it said before
`502ed0b1` rewrote it while merging a sweep that moved the other knobs' links
onto `ChartSpec`. That commit was right about `[ChartSpec#markers]`,
`[ChartSpec#curve]` and the eight others, and wrong about the one member that
had deliberately stayed behind.

The one-word mistake is not the interesting part. **Where it was caught is.**

ADR-0343 turned doclint on for every published module and recorded that
`./gradlew javadoc` would now fail on a rotted `[link]`. Nothing runs
`./gradlew javadoc`. The only thing that generates javadoc is `javadocJar`, and
the only thing that runs *that* is `publish.yml` — so the lint fired:

- after the three Java jobs had built and tested every module on Linux, macOS
  and Windows, all green;
- after four native libraries had been built on four runners and verified on
  four more;
- in the last step of the twelfth and final job, thirteen minutes in;
- and **partway through an upload**. This was the first run with the Central
  credentials actually set, so the gate resolved to `upload=true` and the job
  published rather than rehearsing. Seven modules finished
  `publishMavenPublicationToMavenCentralRepository` — `:common`, `:core`,
  `:gpu`, `:emoji`, `:toolkit`, `:bom` and `:natives`, the last two *after*
  `:widgets:javadoc` had already failed, because Gradle finishes the tasks it has
  started. `:widgets` and `:html` never uploaded.

That last point is the failure mode `publish.yml`'s own header warns about,
arrived at from a direction the header did not anticipate. It reasons about a
runner publishing *its own platform*; this was one runner publishing *most of
the modules*. The snapshot repository was left holding seven of the nine at that
version, with the two catalogs — the modules an application actually writes
widgets against — missing. A snapshot is overwritten by the next run, so the
repair is the next green snapshot rather than anything by hand; a release would
not have been so forgiving, which is why `publish.yml` refuses a release without
credentials but allows a snapshot to rehearse.

A lint nothing invokes is a lint that runs once, at the least recoverable
moment.

## Decision

**`check` depends on `javadoc`, in `goldberry.publish` — so the modules that are
linted are exactly the modules that ship.**

```groovy
pluginManager.withPlugin('java-library') {
    tasks.named('check') {
        dependsOn tasks.named('javadoc')
    }
}
```

Three things about the shape.

**In `goldberry.publish`, not `goldberry.java-conventions`.** The rule being
enforced is "what we publish must document itself", so it belongs to the plugin
that decides what is published. `:assets` and `:weaver` are build-time modules
that nobody consumes; both have `reference not found` errors today, and neither
is worth the churn of fixing to buy nothing. Putting the dependency in the java
conventions would have made those two errors block every build in the repo.

**Under `pluginManager.withPlugin('java-library')`.** `:bom` is a
`java-platform` and has no `javadoc` task. A bare `tasks.named('javadoc')` at
this plugin's top level fails *configuration* of `:bom`, which is every Gradle
invocation in the repo rather than only a publish — the loudest possible way to
land a fix for a quiet problem.

**`check`, not `build`.** It is a correctness gate, and it belongs with the other
ones — the coverage floor, SpotBugs, PMD — so `-x check` turns all of them off
together and nothing else has to know about it.

## Consequences

- A broken `[link]` in a published module fails the Linux, macOS and Windows
  Java jobs, on every push and every pull request, about four minutes in. It can
  no longer reach `publish.yml`.
- Every `check` across the eight published modules with sources costs roughly
  35 s of javadoc on this machine, three times over in CI because the three OS
  jobs each run `build`. That is the price, and it is paid against a failure
  that otherwise costs thirteen minutes and a half-finished upload.
- `:assets` and `:weaver` keep their four `reference not found` errors. They are
  recorded here rather than fixed: neither module is published, and `javadoc` is
  not wired into their `check`. A later decision to publish either one has to
  clear them first, which is exactly the gate this ADR installs.
- `PublishedJavadocTest` holds both halves — the doclint flags and the `check`
  wiring — as text, the way ADR-0082's other drift guards do.

## What to write instead

A link to a member of the type the comment is on is `[#member]`. A link that
names a type spells a member that type actually declares — `[ChartSpec#curve]`
resolves because `ChartSpec` declares `curve`; `[ChartSpec#fill]` does not,
because the whole point of `LineChart.fill` is that `ChartSpec` has no such
knob. The IDE's resolution is not the test; `./gradlew check` is, now.
