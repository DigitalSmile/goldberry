# 398. The build declares what it actually writes

Date: 2026-09-18

## Status

Accepted. Answers B1, B2, B6 and B7 of the whole-tree review recorded in
`docs/review-2026-09-18.md`.

## Context

Four of the build's own declarations were false, and each was false in the same
direction: the build said it was doing something it was not, or doing it
somewhere it was not, and nothing failed to say so.

**The weaver declared javac's output directory as its own.** `weaveCatalog`
rewrites the compiled classes in place — it collects a module's `@Markup`
widgets into a `WidgetCatalog`, patches `provides … with` into
`module-info.class` and writes a service entry — and it declared that directory
as an `outputs.dir` so that Gradle would know what it touched. Gradle's answer
to two tasks writing into one place is to throw away the compiler's incremental
state, so **every build** of a tree where nothing had changed reported

```
Task ':widgets:compileJava' is not up-to-date because:
Full recompilation is required because no incremental change information
is available.
```

for `:widgets`, `:html`, `:example` and everything downstream of them. The
declaration bought nothing at all: the task also carried
`outputs.upToDateWhen { false }`, so it ran unconditionally either way.

**Spotless formatted no markdown.** The `format 'markdown'` step lived in
`goldberry.java-conventions`, which every module applies, and its globs were
`docs/**/*.md`, `book/src/**/*.md` and `*.md`. A spotless target resolves against
the project that declares it, and no module has a `docs/` or a `book/src/`. The
root, which has both, applies no Java conventions. So the step had existed for
months and had never touched a file.

**PMD read no module descriptor.** Every `pmdMain` run reported

```
ParseException ... at line 28, column 32: Encountered <IDENTIFIER: "org">.
Was expecting one of: ";" ... "." ...
```

on `module-info.java`. PMD's Java grammar takes at most **one** modifier on a
`requires` directive; every module here declares `requires transitive static
org.jspecify`, which the JLS has allowed in either order since Java 9. The
review proposed `requires static transitive` as the workaround. That was
measured against PMD 7.19.0 and 7.20.0 and fails identically — the order is not
what PMD objects to, the second modifier is.

**`blessGoldens` named tasks that do not exist.** The root task depended on
`":${it.name}:blessGoldens"` for every subproject but `:bom`, and `:assets` and
`:weaver` apply plain `java` rather than the conventions plugin. `./gradlew
blessGoldens` — the one command `docs/testing.md` §1.3 tells a contributor to run
after a deliberate visual change — failed with `Task with name 'blessGoldens'
not found in project ':assets'` before blessing anything.

## Decision

**A task declares the files it owns, and only those.**

- `weaveCatalog` and `weaveModels` declare a **stamp file** as their output and
  the classes directory as an input only. The stamp is a file each task alone
  writes, which is enough for Gradle to treat it as a producer rather than as a
  second writer into javac's directory. In-place weaving stays: it is idempotent,
  and a separate woven directory would have to be threaded through
  `output.classesDirs`, the jar, `testClassesDirs` and every consumer — a much
  larger change to fix a declaration.
- **The root formats its own prose**, with two ordinary tasks —
  `checkMarkdown` and `formatMarkdown` — rather than with spotless. Spotless
  refuses a target outside its own project directory, so no module can reach
  these files; and the root cannot apply a `build-logic` plugin, because that
  puts that build's whole classpath under every module and `:core`'s
  `alias(libs.plugins.jmh)` then fails to resolve, which the conventions plugin
  already records beside its CI annotations. Two tasks that trim trailing
  whitespace and end a file with one newline are the whole of what the spotless
  step claimed to do.
- **`module-info.java` is excluded from PMD on purpose**, with the reason written
  at the exclusion. A module descriptor has no resource to leak and no string
  built in a loop, so the triaged ruleset has nothing to say about it; an
  explicit exclusion says that where a stack trace in every report did not.
- `blessGoldens` depends on `tasks.matching { it.name == 'blessGoldens' }` per
  subproject — a live view, so a module that gains the conventions plugin later
  is picked up with no edit here.

## Alternatives considered

- **Weaving into a separate directory.** Correct, and the shape a from-scratch
  design would take: `compileJava` writes raw classes, the weaver produces the
  directory everything else consumes, and Gradle can cache and skip it. Rejected
  for now because `sourceSets.main.output.classesDirs` is what the jar, the test
  task's `testClassesDirs` and every IDE read, and pointing those at a woven
  directory while leaving the raw one on the same classpath puts two copies of
  `module-info.class` in front of the JVM. That is an ADR of its own, not a line
  in this one.
- **Dropping `transitive` or `static` from the jspecify requires.** It would let
  PMD parse the file, at the cost of changing what consumers of the toolkit see:
  `transitive` is there because `@Nullable` appears on exported signatures and
  `-Xlint:exports` requires a consumer to be able to read it. A static-analysis
  tool's grammar is not a reason to change a module's API.
- **Leaving the markdown step where it was and making the globs absolute.**
  Spotless rejects it outright ("All target files must be within the project
  dir"), and if it had not, ten modules would each have formatted the same root
  files.

## Consequences

- `:widgets:compileJava` is up to date on a second build, and so is everything
  downstream. What this costs is that Gradle no longer knows the weaver writes
  into the classes directory — a clean build and a `--rerun-tasks` are the same
  as before, but a hypothetical task that wanted "the woven classes" as an input
  cannot ask for them by output. Nothing asks today.
- The prose is formatted by a task that is not spotless, so a contributor now has
  two formatting commands rather than one: `spotlessApply` for Java,
  `formatMarkdown` for everything else. `checkMarkdown` runs in `linux.yml`'s
  `java` job beside `checkLicenses`, which is where the first run found five
  files with trailing whitespace in them.
- PMD's report is empty rather than full of stack traces, which means the next
  real finding in it will be visible. It also means a module descriptor is
  analysed by nothing at all — accepted, and written down here so that the next
  person who wonders finds the answer rather than the exception.
