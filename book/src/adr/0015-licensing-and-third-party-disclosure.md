# ADR-0015: Licensing and third-party disclosure

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3, §6.1, §6.2, §6.3, §15

## Context

`docs/ARCHITECTURE.md` §15 stated the licensing intent in one line: the toolkit
is Apache-2.0, and the bundled assets are Inter (OFL), JetBrains Mono (OFL),
Lucide (ISC), and an OpenMoji derivative (CC BY-SA). That is correct as far as it
goes, but it is missing the larger half of the obligation.

**The native libraries are statically linked.** §3.2 links Blend2D, AsmJit, SDL3,
Yoga, HarfBuzz, and libxkbcommon into a single shared library. Their compiled
code is therefore present in every binary artifact Goldberry publishes — this is
redistribution in object form, and the MIT-licensed components among them require
their copyright and permission notices to travel with it. §15 did not mention
them at all.

**The OpenMoji derivative is the only share-alike obligation in the project.**
§6.2 ships the COLRv0 colour variant with its CPAL palette re-themed toward Nord.
That is a derivative work of a CC BY-SA 4.0 asset, and §15's "published with
attribution per license" understates what follows.

There is also a practical hazard specific to how this repository is being built:
licence texts are easy to approximate and dangerous to get wrong. A licence file
containing a plausible-but-inexact text is worse than one that is honestly absent.

## Decision

Four artefacts, and one rule about their contents.

- **`LICENSE`** — the Apache License 2.0, verbatim.
- **`NOTICE`** — the Apache-2.0 §4(d) notice file: what Goldberry bundles, under
  which licences, and the OpenMoji modification disclosure.
- **`THIRD-PARTY-NOTICES.md`** — the full disclosure, split by *how* a component
  is bundled, because that is what determines the obligation: statically linked
  into `libgoldberry`, embedded in the jars, or build-time only.
- **`licenses/<component>.txt`** — one file per component.

The rule: **licence texts are vendored verbatim from the revision actually
used**, never from a licence template and never from memory, because the
copyright lines are part of the licence. Until a component is vendored its file
carries a `NOT-VENDORED` marker and a reference text clearly labelled as
informational. Standard short licences (Zlib, MIT, ISC) carry a reference body;
HarfBuzz's non-standard "Old MIT", the OFL, and CC BY-SA 4.0 carry none, because
approximating those would misstate them.

`./gradlew checkLicenses` enforces the disclosure both ways: every file in
`licenses/` must be referenced by `THIRD-PARTY-NOTICES.md` and vice versa. It
warns about `NOT-VENDORED` markers by default and fails on them under
`-Pgoldberry.releaseCheck=true`, so an unvendored licence cannot reach a release.

Every jar carries `META-INF/LICENSE`, `META-INF/NOTICE`,
`META-INF/THIRD-PARTY-NOTICES.md`, and `META-INF/licenses/`.

## Alternatives considered

- **A single flat `THIRD-PARTY` file with all texts inlined.** Rejected: it
  cannot be checked mechanically, and a per-component file is what an audit or an
  SBOM tool actually wants.
- **A licence-scanning Gradle plugin.** Rejected for now: those tools report
  declared Maven metadata, and Goldberry's obligations come almost entirely from
  statically linked C libraries and embedded font assets, which such plugins do
  not see. Worth revisiting once there are real Maven dependencies to scan.
- **Reproducing every licence text now, from memory.** Rejected outright. This is
  the one place in the repository where a confident-looking approximation causes
  legal rather than technical harm.
- **Dropping OpenMoji to avoid share-alike entirely.** Not chosen, but recorded
  as the escape hatch: shipping only the unmodified monochrome variant, or
  swapping the emoji font, removes the obligation. §6.1 makes the emoji slot one
  of exactly two font slots, so it is replaceable by design.

## Consequences

- The obligations are enumerated and machine-checked rather than remembered, and
  adding a dependency without a licence entry fails the build.
- **Every licence file is currently a placeholder**, because nothing is bundled
  yet — no native library has been built and no font vendored. The framework is
  deliberately in place first so that vendoring an asset is an act with a licence
  entry attached, rather than something reconstructed at release time.
- Vendoring ten licence files is real work that must happen before the first
  publish, and `-Pgoldberry.releaseCheck=true` is what makes forgetting it
  impossible rather than merely unlikely.
- **The share-alike obligation on the re-themed OpenMoji font is permanent** for
  as long as Goldberry ships it. It reaches the font only — CC BY-SA has no
  linking or combination clause of the kind copyleft software licences use, so
  the Java and native code stay Apache-2.0 — but the font itself can never be
  relicensed, and any About dialog must carry the attribution and the statement
  that changes were made.
- Jars grow by a few kilobytes. Consumers get the disclosure without needing the
  repository, which is the point.
- Source-file licence headers are **not** added. Apache-2.0 recommends them but
  does not require them, and the `LICENSE` plus `NOTICE` files satisfy §4.
  Worth reconsidering before the first release, as a single mechanical pass.
