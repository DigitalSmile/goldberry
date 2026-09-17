# 339. A foreign call is registered because it exists, not because a run reached it

Date: 2026-09-17

## Status

Accepted. Amends [ADR-0156](0156-the-image-s-metadata-is-traced-not-written.md),
whose trace still supplies the reflection and resource metadata but no longer the
foreign calls, and corrects `native.md`'s claim that the descriptors were never
run-dependent. Applies [ADR-0160](0160-a-modules-own-resources-are-declared-not-traced.md)'s
rule — a module ships its own metadata — to the FFM registrations.

## Context

The first green Showcase ([ADR-0338](0338-a-red-run-says-why-in-public.md)) built
and ran the native image on all three platforms, three frames each. Run on Windows
by hand, the image opened, painted, and died on the first click into the html,
canvas or Markdown screen with `MissingForeignRegistrationError`.

GraalVM has to know every `FunctionDescriptor` a `Linker.downcallHandle` or
`upcallStub` will be asked for while the image is being built. ADR-0156 got that
list from GraalVM's tracing agent watching the showcase run for 120 headless
frames, and `native.md` asserted the list was complete anyway, because "the
holders of a library are all reached when its `…Calls` record binds, which happens
on any JVM start". That was wrong. A `…Calls` record binds when the wrapper that
owns it is first used: `MarkdownCalls` when the first document is parsed,
`PathCalls`' arc and cubic holders when a canvas first draws one. A run that opens
no Markdown initialises no `MarkdownCalls$Parse`, links no descriptor, and the
agent records none. The checked-in trace held 61 downcall shapes out of the
holders' 216 handles, and the difference was every screen the run never opened.

The macOS and Windows legs re-trace headlessly before building, for "a call only
that platform's code path reaches" (ADR-0337). They reach the same 120 frames, so
they fix nothing here, and they cannot be reviewed.

## Decision

**The foreign registrations are generated from the bindings, at build time, and
ship in the `goldberry-natives` jar.** No trace has to reach a screen for its
calls to be registered, because the registration comes from the holder existing.

- `Downcalls.link`, which every holder's `FD_…` initialiser already calls,
  records the descriptor it was handed. `Downcalls.linked()` is the downcall
  half of the surface.
- `Upcalls.describe` is the new choke point for the other half. The five classes
  that make a stub — `SdlEventWatch`, `SdlFileDialogs`, `SdlTray`,
  `MeasureCallback`, `SdlClipboard` — declare their `DESCRIPTOR` through it, so
  the shape is recorded when the class initialises rather than when the first
  tray or dialog exists.
- `ForeignSurface`, in a new `natives.metadata` package, lists the module's
  classes through its `ModuleReader` (or its code source, off the module path),
  initialises every class in a `…calls` package and every upcall owner, and
  returns both lists. Initialising a holder needs no `libgoldberry`, which is
  ADR-0173's point: an unbound handle is linked from a descriptor and names no
  address.
- `ForeignMetadata` writes them as the `foreign` section of a
  `reachability-metadata.json`, in the agent's own spelling: `jint`, `void*`,
  `struct(jfloat,jfloat)`, `padding(n)`, `sequence(n, …)`, `union(…)`.
- `:natives:foreignMetadata` is a `JavaExec` on the module path that runs it, and
  `jar` copies the result under `META-INF/native-image/io.github.digitalsmile/goldberry-natives/`.
  `native-image` reads that path from every jar on its module path, so an
  application building its own image gets the registrations without knowing
  they exist — ADR-0160's arrangement, again.

**The trace stays for what it is good at.** Reflection, services and resources are
still what the run saw, and ADR-0156's warning still applies to them. Its `foreign`
section is now a subset of the generated one and is harmless where it overlaps.

**The tests hold the surface to the tree.** `ForeignSurfaceTest` checks that
every holder's handle shape is among the reported descriptors, that the Markdown
parser's is there without any Markdown having been parsed, and that the list of
upcall owners equals the set of source files that call `upcallStub`.
`ForeignMetadataTest` checks the spelling against the agent's: every descriptor
the checked-in trace ever recorded must appear among the generated ones, which is
the one comparison against the reader that matters.

## Consequences

- An image built from this commit registers all of the holders' distinct
  descriptors and six upcall shapes. Whether the html, canvas and Markdown screens
  now open in the Windows image is verified by the next Showcase run's image and a
  hand test, since no machine here has GraalVM.
- The registrations are exact rather than derived from a `MethodHandle`'s type: a
  struct passed by value keeps its layout, which a `MethodType` would have
  flattened to `MemorySegment`.
- A holder added tomorrow is registered tomorrow, because it lives in a `…calls`
  package. A new upcall owner is not, until it is added to `UPCALL_OWNERS` — and
  the test that scans the sources fails the build until it is.
- `Downcalls` and the holder packages are initialised at image build time
  (ADR-0161), so the registry list is in the image heap: a few hundred references,
  nothing more.
