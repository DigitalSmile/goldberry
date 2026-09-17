# 336. One dependency to start from, and a BOM to line up the rest

Date: 2026-09-17

## Status

Accepted. Extends ADR-0334's published set; applies ADR-0190's "none of them a
dependency of `-core` or `-widgets`" to the POM.

## Context

ADR-0334 publishes six libraries: `goldberry-common`, `-natives`, `-core`,
`-widgets`, `-html` and `-gpu`. An application that wants the toolkit has to know
that list, name four of them, and repeat one version four times. An application
that also wants Markdown adds a fifth and has to keep that version in step by
hand. Every content module ADR-0190 describes — PDF, code, terminal and the rest
— makes both of those worse.

The content modules and `:gpu` are optional by design, and the dependency cannot
point the other way: `:html` depends on `:widgets`, so `goldberry-widgets` naming
`goldberry-html`, even as `<optional>`, would be a cycle.

## Decision

**Two more artifacts, both generated from `PublishedModules`.**

- **`goldberry-bom`** (project `:bom`, a `java-platform`): a constraint on every
  library and on the umbrella, at the build's version. It adds nothing to a graph;
  it decides the version of whatever the application does add.
- **`goldberry`** (project `:toolkit`, no code): the umbrella. Its POM lists
  - `goldberry-common`, `-natives`, `-core`, `-widgets` as ordinary compile
    dependencies, and
  - `goldberry-html`, `-gpu` as `<optional>true</optional>`.

```groovy
implementation platform('io.github.digitalsmile:goldberry-bom:2026.1')
implementation 'io.github.digitalsmile:goldberry'
implementation 'io.github.digitalsmile:goldberry-html'     // opting in; no version
```

`PublishedModule` is a sealed interface — `Library(project, Inclusion)`, `Bom`,
`Umbrella` — and `Inclusion` is `REQUIRED` or `OPTIONAL`. The BOM's constraints
and the umbrella's dependencies are loops over it, so a new content module is one
`new Library("pdf", Inclusion.OPTIONAL)` and nothing else. `goldberry.publish`
configures a `JavaPlatform` or a `JavaLibrary` by which kind the project is, and
refuses a project whose plugins do not match.

Version and group moved out of `goldberry.java-conventions` into
`goldberry.versioning`, because a `java-platform` cannot also be a `java-library`
and the BOM still needs both.

**The umbrella publishes no Gradle Module Metadata.** Gradle's only way to say
*optional* in a `.module` file is a feature variant, which needs a source set and
publishes an empty jar per feature. Without the file Gradle reads the POM, where
it treats an `<optional>` dependency as a version constraint rather than a
dependency — which is the meaning wanted. Checked with a consumer build against a
local repository: `goldberry` alone resolves the four required modules and not
`goldberry-html`; adding `goldberry-html` without a version resolves it at the
BOM's.

## Alternatives considered

- **Optional dependencies on `goldberry-widgets`.** A cycle; see Context.
- **Gradle feature variants on the umbrella** (`registerFeature('html')`). Proper
  Gradle semantics — `requireCapability` — at the cost of an empty classifier jar
  per feature on Central, a source set per feature, and capabilities that have to
  be named so they cannot collide with the real `goldberry-html`. The POM already
  says the same thing to both build tools.
- **BOM only.** Aligns versions and leaves "which four do I need" to the reader.
- **Umbrella only.** An optional dependency is versioned in the umbrella's POM,
  but Maven does not import versions from a dependency's POM, so a Maven user
  adding `goldberry-html` would still have to write its version.
- **A `pom`-packaged umbrella.** No empty jar, but a Maven consumer then has to
  write `<type>pom</type>`, which nobody remembers.

## Consequences

- Two more artifacts per release, both small: the BOM is a POM, the umbrella an
  empty jar with a sources and javadoc jar beside it because Central requires them
  for jar packaging.
- The umbrella's jar is on an application's module path. It has no module
  descriptor and exports nothing; its manifest names it
  `io.github.digitalsmile.goldberry.toolkit` so the automatic module name derived
  from `goldberry-2026.1.jar` cannot collide with anything.
- **The natives' platform jars are still the application's to add**
  (`goldberry-natives:<v>:linux-x64`). A POM cannot choose a classifier by the
  consumer's platform. The BOM does line up their version.
- A Gradle consumer of the umbrella reads a POM rather than module metadata, so
  it loses variant-aware resolution for that one artifact. It has no variants.
- The root `blessGoldens` skips `:bom`, which has no tests.
