# ADR-0013: Groovy DSL for the build

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §15

## Context

`docs/ARCHITECTURE.md` §15 specified Gradle with the Kotlin DSL, and the build
was originally written that way. The Kotlin DSL's advantages are real but they
scale with build size: compile-time checking of build scripts, IDE completion and
navigation into Gradle's API, and generated type-safe accessors — all of which
matter most on a build large or intricate enough for a mistyped property to be
hard to find.

Goldberry's build is neither. Four modules, one convention plugin, one version
catalog, and a CMake invocation. Against that, the Kotlin DSL charges a real
price: slower first configuration while scripts compile, and Kotlin's stricter
typing turning ordinary Gradle idioms into ceremony.

Groovy also remains the dialect the majority of Gradle documentation, Stack
Overflow answers, and existing build files are written in.

## Decision

Write the build in the Groovy DSL. `settings.gradle`, `build.gradle`, the four
module scripts, and the convention plugin under `build-logic/` — which becomes a
precompiled *Groovy* script plugin via the `groovy-gradle-plugin` plugin rather
than `kotlin-dsl`.

`build-logic` itself is kept. It exists so that the toolchain, lint, JPMS, and
JUnit configuration is written once rather than four times, and it is the only
approach that stays compatible with Gradle's project-isolation direction — a
root-level `subprojects { }` block is precisely what project isolation breaks.

## Alternatives considered

- **Keep the Kotlin DSL** (what §15 specified). Rejected as above: its benefits
  are proportional to build complexity, and this build has little.
- **Groovy, but drop `build-logic` for `subprojects { }` in the root build.**
  Rejected: it is simpler today and a migration tomorrow, and it forfeits project
  isolation. Reasonable to revisit if the convention plugin never grows.
- **Groovy, with `apply from: 'gradle/java-conventions.gradle'` script plugins.**
  Rejected: simpler than `build-logic` but not configuration-cache friendly, and
  it has no plugin identity, so `plugins { id '...' }` is unavailable.

## Consequences

- Build scripts are shorter and read like most Gradle in the wild.
- **Build errors move from configuration time to execution time.** A misspelled
  property is a runtime failure rather than a compile error, and the IDE cannot
  complete or navigate Gradle's API. This is the whole of what is being given up,
  and on a build this size it is a fair trade.
- **The version catalog is clumsier inside the convention plugin.** Precompiled
  Groovy script plugins get no generated `libs` accessors, so the catalog is read
  through `extensions.getByType(VersionCatalogsExtension).named('libs')` and
  entries are looked up by string. A renamed catalog entry now fails at
  configuration time instead of at compile time. Module build scripts are
  unaffected — they still get the generated `libs` accessor.
- `docs/ARCHITECTURE.md` §15 has been corrected; it was the specification that
  said Kotlin DSL.
- Reversible at low cost while the build is this small. That will stop being true.
