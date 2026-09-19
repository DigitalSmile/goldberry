# 438. A JVM consumer carries no platform, so a variant has nothing to match

Date: 2026-09-19

## Status

Accepted, and it **answers rather than builds**. Qualifies
[ADR-0336](0336-one-dependency-to-start-from-and-a-bom-to-line-up-the-rest.md),
whose open consequence proposed two fixes of which one does not work.

## Context

`book/src/TODO.md` carried this:

> **An application still adds its platform's natives jar by hand.** The
> `goldberry` umbrella cannot pick `goldberry-natives:<v>:linux-x64` for the
> consumer's platform — a POM has no way to — so the BOM lines up its version and
> the classifier is the application's. **A Gradle plugin, or module-metadata
> variants keyed on OS and architecture, would close it.**

`docs/releasing.md` repeats the same pair. The first half of the diagnosis is
exactly right: a POM is a version table and has no notion of an operating system.
The question is the second half — whether Gradle Module Metadata variants are a
way out that costs less than shipping a plugin.

They are not, and the reason is worth writing down because it is not obvious from
the documentation: **a variant is selected by matching the consumer's attributes,
and a plain JVM consumer has no platform attribute to match with.**

## The experiment

A throwaway two-project build, published to a file repository: a producer whose
`java` component carries two extra variants attributed with
`OperatingSystemFamily` and `MachineArchitecture` and carrying a classifier jar
each — precisely the shape the entry proposes — and a consumer that is an
ordinary `java-library`.

**A consumer that does not ask gets the plain jar, silently.**

```
RESOLVED: producer-1.0.jar
```

No error, no warning, no classifier jar. Which is the current situation with more
machinery behind it: an application that did nothing would still ship without a
native library, and would still find out at `UnsatisfiedLinkError` time.

**A consumer that does ask gets an ambiguity failure.**

Adding the two attributes to the consumer's `runtimeClasspath`:

```
However we cannot choose between the following variants of probe:producer:1.0:
  - linuxRuntime
  - runtimeElements
…
- Variant 'runtimeElements' …
    - Unmatched attributes:
        - Doesn't say anything about org.gradle.native.operatingSystem (required 'linux')
```

This is the load-bearing detail. In Gradle's variant model **a missing attribute
is compatible with any requested value**, so the ordinary `runtimeElements`
variant — the bindings jar every consumer needs regardless of platform — remains a
candidate alongside the platform-specific one, and the two tie.

There are only three ways out of that tie and the producer owns none of them:

- **Attribute `runtimeElements` too.** Then the plain jar is platform-specific,
  which it is not: `goldberry-natives` is the FFM bindings, shared by every
  target, and the `.so` is the separate classifier artifact.
- **Remove the unattributed variant.** Every consumer then has to opt in, which
  breaks the ones that work today.
- **A disambiguation rule.** These are registered on the **consumer's**
  `attributesSchema`. A producer cannot install one.

## Decision

**Variants are not the answer, and the entry's second option is the only one.**
The closing move is a consumer-side Gradle plugin that adds the right classifier
dependency from `os.name` and `os.arch` — which is what JavaFX, LWJGL and
sqlite-jdbc all ship, and is not a coincidence.

**That plugin is not built here**, and the reason is scope rather than
difficulty: it is a new published artifact with its own coordinates, its own
release surface and its own compatibility promise, on a publishing chain that has
never run once. It is a decision for after the first release, not a line item in a
sweep.

What lands instead is the correction and one documentation fix.

**The documented snippet now adds all four classifiers**, not one. `NativeLibrary`
picks the right jar at run time by `os.name` and `os.arch`, so four `runtimeOnly`
lines are the arrangement that works on every machine an application is built or
run on — including the case the one-line form gets wrong quietly, which is a
developer on macOS building an application that ships to Linux. Slimming to a
single platform is the deliberate act, and is documented as such rather than being
the default that happens to work where it was written.

## Consequences

- **The entry stays open in a narrower form**: an application still adds its
  natives jars by hand, and the thing that would close it is named exactly
  instead of being one of two guesses.
- **`docs/releasing.md`'s "or Gradle module-metadata variants" is corrected.**
  Leaving it would have cost somebody the afternoon this ADR cost, and they would
  have got as far as the ambiguity error before finding out.
- **Four `runtimeOnly` lines is about 10 MB of jars on the runtime classpath**
  where one would be 2.5 MB. That is the price of the default working everywhere,
  and the one-platform form is one line away for anybody who cares.
- **Nothing in this repository changes shape.** No variant is published, so a
  later plugin is unconstrained by a half-mechanism that had to be kept working.
- The experiment is not kept as a test. It measures Gradle's behaviour rather than
  Goldberry's, and a test that pins another tool's variant-matching rules would
  fail on a Gradle upgrade while telling us nothing about this code.
