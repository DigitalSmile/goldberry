# ADR-0540: Each natives jar names its module, and no widget class is reflected on

- **Status:** Accepted.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0156](0156-the-image-s-metadata-is-traced-not-written.md),
  [ADR-0159](0159-a-native-image-carries-its-own-library.md),
  [ADR-0160](0160-a-modules-own-resources-are-declared-not-traced.md),
  `docs/goldberry-gaps.md` #26

## Context

Deploy Orc builds a native image, and two things in Goldberry got in its way.

**The natives jar had no module name.** `goldberry-natives-<target>.jar` holds
one directory, `dev/goldberry/natives/<target>/`, and no manifest entry. Gradle
puts a jar with no module name on the class path, so a modular build had to
split it off by hand. On the module path the JVM would derive `goldberry.natives`
from the file name, the same name for all four platforms.

A name alone is not enough, and that was checked before it was relied on: an
automatic module on the module path that nothing `requires` is not resolved.
With the name and nothing else, `ClassLoader.getSystemResourceAsStream` found
the library on the class path and with `--add-modules`, and found nothing on the
plain module path. `NativeLibrary` would have stopped finding its library the
day Gradle moved the jar.

**The trace recorded widget classes.** GraalVM's agent wrote a bare entry for
`ScrollBar`, `ScrollContent`, `MasonryBox` and every other widget class the run
re-described, and in Deploy Orc's trace its own widget records too. GraalVM
25.4 refused some ("Unresolved type … BiConsumer"), and the build stripped them
after tracing. They came from `Element`'s one reflective question,
`type.getMethod("restyle", ComputedStyle.class).getDeclaringClass()`, asked
once per widget class to learn whether it overrides `Styled.restyle`. Metadata
shipped in `goldberry-widgets` could only cover the toolkit's own classes; the
application's would still be traced.

## Decision

**Each classifier jar gets `Automatic-Module-Name: dev.goldberry.natives.<target
with an underscore>`, and the library is also read from the module path. The
element tree asks the widgets instead of reflecting on their classes.**

- `natives/build.gradle` writes the name into every `nativeJar*` manifest,
  following `toolkit/build.gradle`. `NativePlatform.moduleName()` spells it the
  same way, and `ClassifierJarTest` reads the build script to hold the two
  together.
- `ClassifierJar.open` is the lookup `NativeLibrary` and `WebviewLibrary`
  share: this module, then the system class loader, then the module path from
  `jdk.module.path`, finding the platform's module by name with a
  `ModuleFinder` and reading the library out of it. The directory is not a
  package name, so the jar is an automatic module with no packages, and
  nothing has to be opened.
- An image still takes the jar on `-cp` (or `--add-modules`), because
  `native-image` includes only resolved modules. The book says so.
- `Element.update` no longer asks `RESTYLES`. It calls `restyle` on the
  previous and the next widget with the cascade's cached style and invalidates
  the node's own style when the two answers differ. A widget that does not
  override `restyle` returns its argument, so for almost every node that is two
  calls and an identity check; one that does is compared by value, a flat
  record `equals` against a re-resolve two orders of magnitude dearer. It is
  also more precise: an overriding widget re-described to the same answer now
  keeps its cache.
- `NativeImageComplianceTest` walks every class in `dev.goldberry.widget` and
  holds it to the closed-world list a woven model is held to.
- The checked-in trace loses the seven widget entries. A fresh trace on this
  machine (GraalVM CE 25.3) recorded none of them; the rest of that trace was
  machine noise and was not kept.
- No metadata is shipped for widget records in `goldberry-widgets`: with nothing
  reflective left, there is nothing for it to describe. The widget catalogue is
  found through `ServiceLoader`, which `native-image` resolves on its own.

## Consequences

- A modular application keeps all four natives jars on the module path, and the
  library is found there by name with nothing required and nothing added.
- An application's trace no longer mentions its widget classes, so there is
  nothing to strip, on 25.4 or later.
- `logback.xml` still has to be registered by hand against the application's
  module; that is Logback's lookup and not the toolkit's, and the getting-started
  page's manual metadata now shows the line.
- A `restyle` with side effects would now run on re-description as well as on
  render. The contract already said it reads the widget and returns a style;
  this relies on it.
