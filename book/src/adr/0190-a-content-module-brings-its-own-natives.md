# 190. A content module brings its own natives, and links against the export list

Date: 2026-08-23

## Status

Accepted, as the plan for `docs/content-widgets.md`. None of the modules it
describes is built; what is agreed here is the shape each of them has to take
and the one native rule they all share.

## Context

`docs/content-widgets.md` describes eleven optional modules — HTML/markdown,
PDF, charts, scientific plotting, code, terminal, vector, media, camera,
microphone, and a parked web engine. Until now it was a document the rest of the
plan did not reference: `docs/ARCHITECTURE.md` named charts and nothing else,
`status.md` had no place to say "none of this is built", and `TODO.md` had no
entry saying what each is waiting on. A design document nothing links to is one
that gets re-argued rather than read.

Two things in it are load-bearing enough to need a record of their own.

**The licence shape.** `goldberry-core` is Apache-2.0 with notice-only
obligations: Blend2D, SDL3, Yoga, HarfBuzz, Inter, JetBrains Mono, Lucide
([ADR-0015](0015-licensing-and-third-party-disclosure.md)). An application that
depends on it owes a notice file and nothing else. Half the content modules
break that if they land in core — libVLC is LGPL and wants dynamic linking with
a relink guarantee, OpenMoji is CC BY-SA and wants *visible* attribution in an
about box, PDFium is tens of megabytes of prebuilt binary. Each of those is a
cost some applications will happily pay and no application should pay by
accident.

**The native shape, which is the part `content-widgets.md` does not settle.**
`libgoldberry` is one statically linked library with hidden visibility and an
explicit export list — 203 symbols: 69 `YG*`, 59 `SDL_*`, 46 `bl_*`, 25 `hb_*`
and the shim's own four `goldberry_*`, with every static upstream excluded from
re-export
([ADR-0007](0007-jpms-modules-enforce-the-native-boundary.md),
[ADR-0010](0010-hand-written-ffm-bindings.md)). Three of the proposed modules
need a native library of their own, and each of them needs to *paint*:
litehtml's `document_container` is C++ callbacks that draw text and boxes,
ThorVG rasterizes into a buffer, libvterm hands over a cell grid. The obvious
build — statically link Blend2D into `libgoldberry-html` as well — produces two
copies of Blend2D in one process, each with its own runtime, allocator and JIT
state. A `BLContextCore` created by the toolkit's copy and handed to the
module's copy is undefined behaviour, and the failure would be a corrupt frame
or a crash, not a link error.

## Decision

### One module, one artifact, one notice file

Every content module is its own Gradle subproject, its own published artifact,
its own `module-info`, and — where it has native code — its own
`goldberry-<name>-natives-{platform}-{arch}` classifier jars, with its own
`THIRD-PARTY-NOTICES` covering only what it links. Nothing in
`content-widgets.md` becomes a dependency of `:core` or `:widgets`. An
application opts in per module, and the obligation it takes on is listed in that
module's README before its first line of code exists.

### A content module's native library links against `libgoldberry`

It never statically links Blend2D, HarfBuzz, SDL3 or Yoga a second time.
`libgoldberry.so` is a shared library with a curated C surface; a module's
native library becomes an ordinary consumer of that surface, and the symbols it
needs are **added to `goldberry.symbols`** like any other binding. The export
list stays what it already claims to be: the complete native surface, in one
file, reviewed as a whole.

That has a consequence worth stating plainly rather than discovering: the export
list is sized for what Java binds, and Java binds the paint calls the toolkit
itself makes. The twenty `bl_context_*` entries have no gradient, no rounded
geometry and no `bl_context_save` — the file says why in its own comment, that
there is only ever one clip depth here. §1.5 of `content-widgets.md` promises
litehtml's linear and radial gradients and its `border-radius`, and a native
`document_container` needs a nested state stack because CSS has one. So
`goldberry-html` is not a module that adds a dependency — it is a module that
first *widens the toolkit's own native surface*, and that widening is reviewable
before any of litehtml is compiled.

### The two upcalls, generalized

`content-widgets.md` §1.1 gives litehtml exactly two Java upcalls — `fetch` and
`anchorClicked` — and keeps the thousands of per-page draw calls native. That is
the rule for every content module, not a fact about HTML: **the hot path does
not cross FFM, and everything that touches the network, the filesystem or a
policy decision does.** libVLC's frame delivery, PDFium's page raster and
ThorVG's scene traversal each get the same treatment.

### What already departs from the document, and stays departed

- **Charts are not `goldberry-charts`.** The table lists it as a module;
  [ADR-0014](0014-single-widgets-module.md) merged it into `:widgets` before
  this document was written, on the grounds that five canvas-based widgets with
  no dependencies of their own do not justify an artifact. §3's actual argument
  — no third-party chart engine, borrow the algorithms — is unaffected and is
  what matters. `goldberry-plot` may still want its own artifact; it is the
  bigger vocabulary and the one with colormap data to disclose.
- **Emoji is not `goldberry-emoji`.** `ARCHITECTURE.md` §6.2 puts OpenMoji in
  core's text stack, which means core already carries the CC BY-SA visible-
  attribution obligation the table wanted quarantined. That is a real
  disagreement and it is recorded in §17.1 rather than settled here: moving the
  font out is a change to the text stack's fallback chain, not a packaging edit.
- **Camera and microphone claim "zero new natives" and are not free.** SDL3 is
  linked in, but no `SDL_*` audio or camera symbol is on the export list, and
  none was a tray call either — which is the case M3's own `tray-icon` met first
  and has since fixed ([ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)):
  eleven symbols added, the list at 203. `SDL_OpenAudioDevice` and
  `SDL_OpenCamera` are still not among the 59 SDL entries. "Already in the
  binary" means the code is there; reaching it is still an export-list entry and
  a binding apiece, which is now a measured claim rather than a predicted one.

## Consequences

- **The publishing matrix grows by an artifact plus four classifier jars per
  native module.** That is the cost ADR-0014 refused to pay for five chart
  widgets, paid deliberately here because the thing being bought is different:
  not code separation but a licence boundary and tens of megabytes.
- **`goldberry-media` is the first module that cannot be statically linked.**
  LGPL relinkability requires libVLC to stay a separate shared object, plus its
  plugin tree. Every packaging assumption in `:natives` — one library, one
  export list, hidden visibility — is a static-linking assumption, so that
  module needs a loader that sets a plugin path and a native layout nothing else
  in the toolkit has. It is correctly the last one in the ladder.
- **Golden-image testing survives the split**, which is the quiet win. Every
  engine here rasterizes on the CPU into a buffer the toolkit already knows how
  to compare, so a full HTML document, a PDF page and a terminal grid are all
  golden-testable in CI on three OSes with no hardware — and camera and
  microphone ship synthetic sources precisely so their widgets are too.
- **A module that widens the export list widens it for everyone.** There is one
  `libgoldberry` and one symbol file; the paint surface `goldberry-html` needs
  is then available to any binding, whether or not that was intended. The
  discipline the file already documents — a symbol nothing binds is dead weight
  — is what keeps that honest, and it is a review rule, not a mechanism.
- **Nothing here is scheduled.** M3 owes tray, client-side decorations, charts
  and the rest of §4; M4 and M5 are untouched. The ladder in
  `ARCHITECTURE.md` §16 now names where each module would attach, which is a
  different claim from saying when.
