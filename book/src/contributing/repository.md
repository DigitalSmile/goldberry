# Repository layout

<p class="gb-lede">Thirteen Gradle modules, nine of them published, and a package rule that lets you read the pipeline off the package list.</p>

## The modules

`settings.gradle` is the map. Every published artifact is `goldberry-<module>` under the group `dev.goldberry`, and the group is the subject of [ADR-0510](../adr/0510-publish-under-dev-goldberry.md).

| Module | Artifact | What it holds | Published |
|---|---|---|---|
| `:common` | `goldberry-common` | What both halves need and neither owns: logging and the start-up timeline. The lowest module, it requires nothing of Goldberry's | Required |
| `:natives` | `goldberry-natives`, plus a classifier jar per platform | The FFM bindings and the CMake superbuild that produces `libgoldberry` | Required |
| `:core` | `goldberry-core` | The widget model, style, layout, text, paint and the backend SPI | Required |
| `:widgets` | `goldberry-widgets` | The widget catalogue, charts included | Required |
| `:html` | `goldberry-html` | Markdown through md4c, and HTML in Java | Optional |
| `:emoji` | `goldberry-emoji` | The Noto Color Emoji face and nothing else, 5 MB of paint graphs | Optional |
| `:gpu` | `goldberry-gpu` | `canvas3d` and the GPU composition path | Optional |
| `:media` | `goldberry-media`, plus `ffmpeg-<target>` and `ffmpeg-sources` classifiers | Audio and video over FFmpeg, and the operating system's own decoders behind the same SPI | Optional |
| `:bom` | `goldberry-bom` | A `java-platform` pinning every published artifact | Yes |
| `:toolkit` | `goldberry` | The umbrella: no code, the required modules as dependencies, the optional ones listed `<optional>` | Yes |
| `:assets` | none | A build-time tool that fetches the pinned fonts and icon set and compiles them into resources `:core` packages | No |
| `:weaver` | none | A build-time tool that runs over compiled classes: it collects `@Markup` widgets into a catalog, and weaves `@Bind` and `@Action` for a native image | No |
| `:example` | none | The showcase. A subproject so Gradle and every IDE see it | No. A native image of it is attached to each release |

The records behind the shape: [ADR-0174](../adr/0174-what-both-halves-need-is-its-own-module.md) for `:common`, [ADR-0014](../adr/0014-single-widgets-module.md) for one widgets module, [ADR-0190](../adr/0190-a-content-module-brings-its-own-natives.md) for `:html` as the first content module, [ADR-0460](../adr/0460-media-is-ffmpeg-driven-from-java-not-libvlc.md) for `:media`, [ADR-0336](../adr/0336-one-dependency-to-start-from-and-a-bom-to-line-up-the-rest.md) for the BOM and the umbrella, [ADR-0033](../adr/0033-assets-are-fetched-and-compiled-not-committed.md) for `:assets` as Java rather than build script, [ADR-0131](../adr/0131-a-widget-package-announces-itself.md) and [ADR-0155](../adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md) for the weaver's two halves, and [ADR-0023](../adr/0023-logging-and-the-example-as-a-subproject.md) for the example.

A new optional module is one line in `PublishedModules` in build-logic plus `id 'goldberry.publish'` in its build script. The BOM and the umbrella pick it up, and `PublishedModulesTest` holds each module's build script to the list.

## The package rule

**A package is named for the part its contents play, not for the library or the file they came from.** The CSS engine is a compiler, so it is `css.parse`, `css.select`, `css.cascade` and `css.value`. Input is a dispatcher, so it is `input.event`, `input.key`, `input.hit` and `input.handler`. A native library is split where the foreign memory stops: the wrappers that hold a handle stay beside the binding class, and the enums and values get packages of their own. The record is [ADR-0172](../adr/0172-a-package-is-a-role-and-the-module-is-the-fence.md), and the module descriptor is the fence a package can no longer be.

**Every package has a `package-info.java`** whose doc comment says what the package is for and whether, and to whom, it is exported. Every package of a module under the conventions is `@NullMarked`, so NullAway sees it. `PackageInfoTest` in build-logic fails on a package with no `package-info.java`, on one without a doc comment, and on one that is not marked. That is [ADR-0497](../adr/0497-every-package-says-what-it-is-and-is-null-marked.md).

> [!IMPORTANT]
> A raw `MemorySegment` never leaves `:natives`. The module descriptor enforces it, `ExportedSurfaceTest` reads the descriptor and fails if anything reachable from outside mentions one, and `:natives` exports to `:core` and to nobody else. The records are [ADR-0007](../adr/0007-jpms-modules-enforce-the-native-boundary.md) and [ADR-0280](../adr/0280-natives-exports-to-core-and-to-nobody-else.md).

The map that stays current is in the code. `docs/ARCHITECTURE.md` §2.1 has the groups as ADR-0172 split them, and says the tree has grown past the counts.

## Where things are

| Path | What it is |
|---|---|
| `docs/` | The design documents. `ARCHITECTURE.md` is the system layer by layer. `core-widgets.md`, `design-system.md`, `content-widgets.md` and `charts.md` are the specifications. `testing.md` and `releasing.md` are the runbooks |
| `book/` | This guide, and the decision log under `book/src/adr/`. `docs/book.md` is its runbook and style guide |
| `site/` | The landing page at goldberry.dev. `build.mjs` prerenders it and `content.js` is what it says |
| `build-logic/` | The convention plugins, and the drift guards in its tests |
| `natives/src/main/cmake/` | The superbuild: `CMakeLists.txt`, the export list in `exports/goldberry.symbols`, and the C shim |
| `assets/` | The fetch-and-compile tool for fonts and icons |
| `weaver/` | The class-file weaver |
| `example/` | The showcase |
| `config/` | The PMD ruleset, the SpotBugs exclusions, and Qodana's profile and baseline |
| `gradle/libs.versions.toml` | Every version and every upstream ref. The only place a ref lives |
| `licenses/` | The verbatim upstream licence texts, vendored by hand |
| `tools/` | Scripts: the nullness sweep, and `move_package.py` for a package move |

**Which document wins.** `docs/design-system.md` and `docs/core-widgets.md` are the authority on what a widget is. `docs/ARCHITECTURE.md` is a summary of them and records where it knowingly departs, in its §17.1. The decision log is the authority on why, and a design document describes what. [ADR-0001](../adr/0001-record-architecture-decisions.md) draws that line. [Status](../status.md) says what is built and [TODO](../TODO.md) what is not, and the two are kept apart on purpose.

**The convention plugins** are four precompiled Groovy script plugins under `build-logic/src/main/groovy/`:

| Plugin | What it configures |
|---|---|
| `goldberry.java-conventions` | The toolchain, the module path, Error Prone and NullAway, Spotless, PMD, SpotBugs, JaCoCo, the `benchmark` and `blessGoldens` tasks, PIT, and the CI annotations |
| `goldberry.versioning` | The group and the calendar version, `printVersion` and `bumpVersion` |
| `goldberry.publish` | The Maven Central publication, applied by the shipped modules and refused anywhere else |
| `goldberry.weave` | The weaver over a module's compiled classes, catalog half always and model half for a native image |

The build is written in the Groovy DSL, and [ADR-0013](../adr/0013-groovy-dsl-for-the-build.md) says what that trades away. The root `build.gradle` is a container: it aggregates coverage and owns `checkLicenses`, `checkMarkdown`, `formatMarkdown` and the root `blessGoldens`.

## Tests as drift guards

A fact that has to be true in two places gets a test that reads both. The rule came out of [ADR-0082](../adr/0082-a-preflight-check-that-cannot-fail-is-not-a-check.md), where a preflight check certified a toolchain that the next task could not use, and the knowledge that would have caught it existed in three places and reached the check in none of them.

| Test | Reads | Holds |
|---|---|---|
| `LinuxDependenciesTest` | The dependency table and the workflows | Every required package is installed by the workflows that run `checkToolchain` |
| `ExportListTest` | `goldberry.symbols` and the Java bindings | A symbol bound in Java is exported, and a symbol exported is bound |
| `PackageInfoTest` | Every package directory | A `package-info.java`, a doc comment, `@NullMarked` |
| `DecisionLogTest` | `book/src/adr/` and `SUMMARY.md` | Numbered, contiguous, with a status, and linked from the book |
| `PublishWorkflowsTest` | `.github/workflows/` | Only `publish.yml` uploads to Central, and the per-OS workflows have no `push` trigger |
| `PublishedModulesTest` | Each module's build script | The published list and the scripts agree |
| `DeclaredResourcesTest` | `src/main/resources` | Every resource is covered by a native-image glob |
| `ExportedSurfaceTest` | `:natives`' descriptor and class files | No reachable member mentions `MemorySegment` |
| `WrittenNamesTest` | The weaver's constants | Every class name the weaver writes as text resolves |
| `BoundaryTest`, `DeterminismTest` | The whole graph, through ArchUnit | The module arrows in `ARCHITECTURE.md` §2, and no clock, random source or default locale in the deterministic layer |
| `SiteTest` | `site/content.js` and the book | The pages the landing page links by name are where it says |

Most of these live in `build-logic/src/test/`, which `./gradlew :build-logic:test` runs. The ArchUnit pair lives in `:widgets`, because that is the module whose test classpath holds the whole graph.

## The export list and the layout probe

`natives/src/main/cmake/exports/goldberry.symbols` is the list of symbols `libgoldberry` exports, and the library exports exactly those and nothing else. The file states its own rule: a symbol bound in Java but missing here fails at load time, and a symbol here that nothing binds is dead weight. `ExportListTest` enforces both halves by reading the two files as text. It needs no library, because the question is what the source says.

The bindings are hand-written rather than generated, which is [ADR-0010](../adr/0010-hand-written-ffm-bindings.md). The check that decision rests on is the **layout probe**: `libgoldberry` exports a table of every struct's size and every field's offset, and `LayoutVerificationTest` compares it against what the Java side declares. It needs a built library, so it skips without one and fails under `-Dgoldberry.native.required=true`. That flag is [ADR-0016](../adr/0016-verify-the-artifact-and-never-skip-the-check.md), and CI's verify legs pass it on every platform.
