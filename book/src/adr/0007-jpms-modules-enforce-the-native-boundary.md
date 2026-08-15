# ADR-0007: JPMS modules enforce the native boundary

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §15

## Context

`docs/ARCHITECTURE.md` §3.1 states that raw `MemorySegment` never escapes the
`natives` module. That is the single most important invariant in the native
layer: once a segment leaks into application code, ownership becomes ambiguous,
the arena discipline stops being enforceable, and use-after-free becomes a
supported feature.

Stated as a rule in a document, it is a convention. Conventions of this kind hold
until the first deadline.

There is a second, unrelated pressure pointing the same way. JEP 472 restricts
the JNI and FFM APIs: Java 25 emits a warning when restricted native access
happens from the unnamed module, and a later release turns that warning into an
error. `--enable-native-access` takes a *module* name. A classpath project has no
module name to give it.

## Decision

Every Goldberry module ships a `module-info.java` from the first commit, and the
build sets `modularity.inferModulePath = true`. The module graph is:

```
natives   (exports nothing yet — wrapper packages only, once M0 lands)
core      → exports io.github.digitalsmile.goldberry
charts    → requires transitive core
gpu       → requires transitive core
gallery   → requires core, charts, gpu
```

`io.github.digitalsmile.goldberry.natives` is the module named in
`--enable-native-access`. The jextract-generated binding packages stay
unexported permanently; only the hand-written wrapper packages are exported.

## Alternatives considered

- **Classpath now, modules later.** Rejected. Retrofitting a module graph onto an
  established codebase means discovering every accidental cross-package
  dependency at once, and split packages are found late and fixed expensively.
  The cost of doing it on day 1, when there are five empty modules, is
  approximately zero.
- **Modules only for `:natives`.** Rejected: a module cannot depend on the
  classpath cleanly, so this poisons the boundary it is meant to protect.
- **Enforce the boundary with an ArchUnit-style test.** Rejected as the primary
  mechanism — it catches violations after they are written rather than making
  them unrepresentable. Reasonable as a supplement later.

## Consequences

- The §3.1 invariant is enforced by javac. Code outside `:natives` cannot name a
  generated binding type, whatever its author intended.
- `--enable-native-access=io.github.digitalsmile.goldberry.natives` is expressible
  today, so the JEP 472 deprecation is a non-event.
- Consumers of Goldberry get a clean module graph, which matters for anyone
  building with `jlink` or native-image.
- JPMS imposes real constraints: no split packages, and reflective access needs
  explicit `opens`. Test source sets need `--patch-module` handling, which Gradle
  does automatically but which shows up in stack traces when it goes wrong.
- Adding a module now costs a `module-info.java` per module — trivial while the
  modules are empty, which is precisely why this is being decided now.
