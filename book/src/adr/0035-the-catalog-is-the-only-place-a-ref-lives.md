# ADR-0035: The catalog is the only place a ref lives

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** [ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md), [ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md)

## Context

Six upstream refs were written down in five places: `gradle/libs.versions.toml`,
the CMake defaults, and three CI workflows. `:natives:checkPinnedRefs` compared
them and failed when they drifted.

The reason given was that the Linux legs build inside a manylinux container with
no JDK, so CI cannot run Gradle to read the catalog
([ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)). That is true and it
is not the relevant constraint. Reading a version catalog does not need a JDK —
it needs something that can parse a text file, and CMake is already doing exactly
that a hundred lines further down `CMakeLists.txt`, where it reads
`exports/goldberry.symbols` line by line.

The cost of five copies was not theoretical:

- A ref bump meant five edits, and `checkPinnedRefs` caught the fifth only after
  a failed build.
- `example.yml` was pinning **Blend2D to a floating `master`**. The check looked
  at three workflows and not at that one, so nothing ever compared it. It had
  been wrong since the file was written. It also turned out to be dead — that job
  builds through `./gradlew :natives:cmakeBuild`, which passed the catalog's
  values and ignored the environment entirely.
- The check could only ever say "these five agree". It could not say they were
  *right*, which is how Blend2D stayed on a floating `master` through four ADRs
  with every copy agreeing.

## Decision

**`CMakeLists.txt` reads `gradle/libs.versions.toml` directly.** A small function
scans the catalog for `key = "value"`, anchored at the start of the line so a
`[libraries]` entry mentioning the same word cannot match. Nothing else passes
refs: not Gradle, not the workflows.

**There is no default to fall back to.** A ref the catalog does not name is a
`FATAL_ERROR` naming the key. A default is a copy, and a copy is the thing being
removed.

**A floating ref is rejected at configure time.** `master`, `main`, `HEAD`,
`latest` and `trunk` fail with a message pointing at
[ADR-0030](0030-pin-blend2d-and-asmjit-by-commit-sha.md). "Pins, not ranges" was
a sentence in a comment for four ADRs while `example.yml` floated; now it is a
mechanism.

**The catalog is a `CMAKE_CONFIGURE_DEPENDS`,** so editing it re-runs configure
and the new ref is fetched, rather than the previous one being kept from `_deps`.
It is a Gradle task input too, for the same reason at the other layer.

**`checkPinnedRefs` is inverted.** It no longer compares copies — it asserts
there are none: no `set(GOLDBERRY_*_REF "...")` in CMake, no `*_REF:` and no
`-DGOLDBERRY_*_REF` in any workflow, and every key present and non-floating in
the catalog. It reads **every** workflow rather than a hard-coded three, which is
what would have caught `example.yml`.

## Alternatives considered

**Generate the workflow env from the catalog.** A script, and a check that the
generated file is committed. It keeps five copies and adds a generator.

**Have CI run Gradle to print the refs, then pass them on.** Puts a JDK in the
manylinux container to read six strings out of a text file.

**A separate `refs.properties` both tools read.** Removes the drift and splits
the pins from the versions catalog they belong beside, so a contributor has two
files to look in and Dependabot-style tooling has one it does not understand.

**Leave it, and add `example.yml` to the check.** The smallest fix, and it treats
the symptom: the next workflow would have been missed the same way.

## Consequences

**A ref bump is one edit.** The four in this change — Yoga `v3.2.1`, HarfBuzz
`14.3.1`, SDL3 `release-3.4.14`, libxkbcommon `1.13.2` — were made by editing the
catalog alone, and everything downstream followed.

**Configuring from outside a full checkout needs a flag.** The catalog is found
at a path relative to the CMake source directory. `-DGOLDBERRY_VERSION_CATALOG=`
overrides it, and the failure says so.

**The bump surfaced a toolchain floor the build could not see.** libxkbcommon
1.13 requires Meson >= 1.4; Ubuntu 24.04 ships 1.3.2. It failed ninety seconds
in, from inside meson, naming neither Goldberry nor the pin that raised the
requirement. `checkToolchain` now enforces version floors rather than mere
presence, and `example.yml` installs Meson from pip rather than apt.

**The test tasks did not re-run against a rebuilt library.** Found while
verifying this change: `dependsOn cmakeBuild` orders the tasks and nothing more,
so a freshly built `libgoldberry` left every test `UP-TO-DATE`. Four upstreams
moved and the layout verification reported green without running. Both `:natives`
and `:core` now declare the library as a task **input**, which is the difference
between "built first" and "verified against". That was the most valuable thing
this change found, and it had nothing to do with refs.
