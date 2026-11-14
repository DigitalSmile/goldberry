# ADR-0685: The weaver is published, with a Gradle plugin that runs it

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0125 (the weaver rewrites the model's bytecode), ADR-0495 (media is published and snapshots publish again)

## Context

The weaver has two halves. Its catalog half declares a module's `@Markup`
widgets, which every build of a module with markup widgets of its own needs.
Its model half weaves `@Bind` fields, which a GraalVM native image needs. The
weaver was a build-time tool of this repository only. `PublishedModulesTest`
asserted it was not published, `weaver/build.gradle` disabled publishing, and
yet the book told users to depend on `dev.goldberry:goldberry-weaver`. A
downstream messenger needs a native image for its launch-time and memory
budgets, and has its own markup widgets. It could do neither.

The owner chose on 2026-11-08 to publish the jar **and** a Gradle plugin,
rather than the jar alone.

## Decision

- **`goldberry-weaver`** is published. `PublishedModule.Inclusion` gains
  `TOOL`: the BOM pins it, and the umbrella does not depend on it, because it
  reaches no application's run time. The weaver applies `java-library` and
  `goldberry.publish`. It keeps its classpath-program shape and its own
  toolchain.
- **`goldberry-gradle-plugin`** is a new published kind,
  `PublishedModule.GradlePlugin`, from the new project `:gradle-plugin`. Its
  marker, `dev.goldberry.weave.gradle.plugin`, means
  `plugins { id 'dev.goldberry.weave' version '…' }` resolves from Maven
  Central. A build lists `mavenCentral()` among its plugin repositories; the
  Gradle Plugin Portal is not used. The plugin does for an application what
  `goldberry.weave` does here:
  - `weaveCatalog` and `weaveTestCatalog` run before `classes` and
    `testClasses`.
  - `weaveModels` and `weaveTestModels` are always registered, and run before
    `classes` when `goldberry { nativeImage = true }` or
    `-Pgoldberry.nativeImage=true` says so.
  - Each runs in a JVM of its own on the project's Java toolchain, with the
    weaver, the classes and their compile classpath.
  - Weaving is in place, with a stamp as the declared output, for ADR-0125's
    incremental-compilation reason.
  - The weaver resolved is the version released with the plugin (written into
    its jar), unless `goldberry { weaverVersion = … }` names another.
- **The plugin compiles for Java 17**, not the toolkit's 25. It runs in the
  application's Gradle daemon, whose JVM is whatever started Gradle, and 17 is
  the oldest Gradle 9 accepts. The weaver itself runs on 25.
- The weaver's three doc comments that linked `:core` types it never imports
  are code spans now, because the published javadoc's lint refused them.

## Consequences

- Two more artifacts on every snapshot and release. `publishToMavenCentral`
  publishes every project that applies `goldberry.publish`, so no workflow
  changed.
- Inside this repository `goldberry.weave` stays: it uses `project(':weaver')`,
  which a published plugin cannot.
- Tested by `WeavePluginTest` (`ProjectBuilder`: the four tasks, the lifecycle
  with and without a native image, the weaver's version) and by
  `PublishedModulesTest` (the weaver published as a tool, the plugin found
  with its id). The guards `CentralPomTest`, `PackageInfoTest` and
  `SourceDocsTest` hold the new project to the same rules as the rest.
