# 387. A resource directory is a package

Date: 2026-09-18

## Status

Accepted. Fixes the start-up failure ADR-0384 shipped, and adds the check that
would have caught it.

## Context

`goldberry-emoji` put its face where the asset step had always put faces:

```
io/github/digitalsmile/goldberry/assets/fonts/OpenMoji-black.ttf
```

`goldberry-core` still ships four faces and an icon table in that same
directory. **A resource directory is a package to the module system**, exactly
as a directory of classes is — so two modules contained
`io.github.digitalsmile.goldberry.assets.fonts`, and the showcase died before
its first frame:

```
java.lang.LayerInstantiationException: Package io.github.digitalsmile.goldberry.assets.fonts
    in both module io.github.digitalsmile.goldberry.emoji and module …core
```

Every test passed, and that is the part worth writing down. Tests run on a
**class path**, where a split package is two directories and nobody objects;
`./gradlew :example:run` puts the modules on a **module path**, which is where
the rule lives. A green suite and an application that will not open is the exact
shape of failure this repository has tests for — and the module graph, which
`docs/ARCHITECTURE.md` §3.1 calls load-bearing, had no test at all.

## Decision

**A module's resources live under that module's own package, and a test walks
the artifacts to say so.**

- `PrepareAssets` takes `--root=`, so a build script says where inside the jar
  its assets land. `:core` keeps `io/github/digitalsmile/goldberry/assets`;
  `:emoji` writes `io/github/digitalsmile/goldberry/emoji`, and its face is
  `…/emoji/fonts/OpenMoji-black.ttf`.
- `SplitPackageTest` is `:example`'s, because `:example` is the one module that
  depends on every other. It walks each Goldberry artifact on the class path —
  directory or jar — collects the directories that hold files, and fails when a
  package is in two modules.
- It asks the question the JVM asks, rather than reading
  `ModuleDescriptor.packages()`: that set comes from the `ModulePackages`
  attribute javac wrote about the classes it compiled, and what the runtime
  objected to came from the jar's contents.

## Consequences

- The showcase runs again, and the run is part of what was verified rather than
  assumed: `./gradlew :example:run` painting three frames on the dummy driver.
- The test is discovery-based, so a module added later is covered without anybody
  remembering to add it — and it refuses to pass if it finds almost nothing,
  which is how a discovery test says it has stopped discovering.
- The rule now has a name to cite: assets belong under the module that ships
  them. `:html` already obeyed it by accident, having no resources outside its
  own package.
