# 160. A module's own resources are declared, not traced

Date: 2026-08-20

## Status

Accepted. Narrows [ADR-0156](0156-the-image-s-metadata-is-traced-not-written.md),
which said the metadata is traced. Most of it still is; resources are not.

## Context

ADR-0156 wrote down the cost of tracing before it had been paid:

> **The metadata is only as complete as the run that traced it.** A screen the
> 120-frame run never reaches contributes nothing, and the symptom is an image
> that starts and then dies opening a menu.

It was paid on the first real use of the image. Toggling the theme:

```
IllegalStateException: the NORD_LIGHT theme is missing from the jar: nord-light.css
```

The trace recorded `nord-dark.css`, because that is the theme the showcase starts
in. Three more had the same shape:

| Missing | Why the trace never saw it |
|---|---|
| `nord-light.css` | the run never toggled the theme |
| `density-compact.css` | the run never switched density |
| `JetBrainsMono.ttf` | the run drew no monospace text |
| `OpenMoji-black.ttf` | the run drew no emoji |

Every one is *the other half of a control the user can reach*. And the failure is
worse than a missing feature: it is a crash, in the frame loop, on a click that
worked yesterday in the jar.

Re-running the trace with more interaction would have found these four and
nothing about the fifth. A trace can be made longer; it cannot be made complete.

## Decision

**A module declares its own resources by glob, and ships that declaration
itself.**

`:core`, `:widgets` and `:example` each carry a
`META-INF/native-image/io.github.digitalsmile/<artifact>/reachability-metadata.json`
listing what they ship as globs:

```json
{ "module": "io.github.digitalsmile.goldberry.core",
  "glob": "io/github/digitalsmile/goldberry/css/*.css" }
```

Globs, because **this set is finite and known at build time** — unlike a trace,
which records only what one run happened to touch. Two themes, four fonts, one
icon table, two stylesheets: a directory listing answers it exactly, and a
directory listing cannot be one screen short.

Each module ships its own, because `native-image` reads
`META-INF/native-image/**` from **every** jar on the path. So an application
building an image gets the toolkit's resources without knowing it needs them —
which is the real point. A consumer should not have to discover that Goldberry
has two themes by shipping an image that crashes on the theme toggle.

Everything else in ADR-0156 stands. The FFM descriptors, the reflection and the
services are still traced, because those genuinely depend on what the code does
and no directory listing can enumerate them.

## Alternatives considered

**Trace harder.** Drive the showcase through every screen, every toggle and every
control in the metadata run. It would have caught these four, and it makes the
trace run a second test suite that has to be kept exhaustive by hand — with the
same failure mode, one control further out. It also makes the metadata depend on
*how thoroughly somebody clicked*, which is not a property a build should have.

**`-H:IncludeResources` on the `native-image` command line.** The same globs, in
the build file instead of the module. It works for the showcase and does nothing
for anyone else's application: the flag is not shipped with the jar, so every
consumer would have to write it out again from knowledge they do not have.

**One `io/github/digitalsmile/goldberry/**` glob per module.** Shorter, and it
matches every `.class` file in the module as a resource — carrying the whole
module a second time in the image heap. The paths are spelled out instead.

## Consequences

**The image grew from 41 MiB to 43 MiB**, which is the two fonts the trace had
been leaving out. That is the honest cost of completeness and it was always owed:
an image that fits because it is missing a font is not smaller, it is broken.

**The traced file and the written file are still separate**, and this adds a third
kind of entry to the written one. The rule from ADR-0156 holds — nothing
hand-written goes in the traced directory, because the next trace overwrites it.

**A resource added to a module needs no thought, and a resource *directory* does.**
Dropping `nord-dim.css` beside the other two is covered by the existing glob.
Adding `io/github/digitalsmile/goldberry/sounds/` is not, and nothing will say so
until an image is built and someone reaches the control that needs it.

**This does not fix the general case, and should not be read as doing so.** The
FFM and reflection metadata remain as complete as the run that traced them, and
ADR-0156's warning applies to them unchanged. What has changed is that the
*largest and most predictable* category — files this repository ships — no longer
depends on where somebody clicked.
