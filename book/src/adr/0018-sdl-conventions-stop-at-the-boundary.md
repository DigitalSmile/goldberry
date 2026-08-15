# ADR-0018: SDL's conventions stop at the boundary

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §4, [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0010](0010-hand-written-ffm-bindings.md)

## Context

SDL3 is the desktop backend (ADR-0003), and this is the first slice of it: the
lifecycle, error and version calls, chosen because they prove the symbols are
reachable and the calling conventions are right without needing a display.

SDL is a C library with C habits, and two of them travel badly into Java.

Errors are a `false` return plus a message from `SDL_GetError()`. The message is
per-thread and is overwritten by the next failing call, so it has to be read
immediately or not at all. Passing that convention through means every call site
in Goldberry checks a boolean and remembers to fetch the message before doing
anything else, and the failure mode for forgetting is not a crash — it is code
that carries on with a window that was never created.

Subsystems are a bit mask. `SDL_INIT_VIDEO | SDL_INIT_EVENTS` is fine in C and
is an invitation to typos in Java, where the alternative is a type the compiler
understands.

There is a third thing, less obviously a convention: `SDL_WasInit` reports what
SDL *actually* initialized, which is a superset of what was asked for — video
implies events, gamepad implies joystick — and a future SDL may report a
subsystem this code has never heard of.

## Decision

Neither convention survives the boundary.

A failing SDL call raises `SdlException`, carrying the C function name and
whatever `SDL_GetError()` said, captured at the point of failure while it is
still the right message. Callers cannot forget to check, because there is nothing
to check.

`SDL_InitFlags` becomes a `Set<SdlSubsystem>` in both directions. The bit values
are declared explicitly from `SDL_init.h` and asserted against those literals in
a test, so a mistyped bit fails the build rather than silently never initializing
a subsystem.

And decoding a flag mask **ignores bits it does not recognise**. This is the
opposite of `MeasureMode.of()`, which rejects an unknown `YGMeasureMode`, and the
asymmetry is deliberate: an unknown measure mode means the callback signature is
wrong, which is a bug in Goldberry, while an unknown init flag means SDL grew a
subsystem, which is a Tuesday. One should fail loudly; the other should not turn
a dependency bump into a crash.

## Alternatives considered

**Return `boolean` and let callers check.** Faithful to SDL, and it makes the
binding layer a pure translation with no policy in it — which is a real virtue
when the C library is the specification. Rejected because the error message is
the perishable part: by the time a caller decides it wants to know why, another
call on the same thread may have replaced it. Faithfulness that loses information
is not faithfulness.

**A checked exception.** The failures here are genuinely recoverable — no video
device, no audio — and a checked exception would force the decision to be made.
It would also put `throws SdlException` on every method of the backend SPI and
every implementation of it, for a condition that is fatal to a UI toolkit at
startup and impossible everywhere else. Rejected on ergonomics, and recorded here
because it is the kind of thing that looks like an oversight later.

**`EnumSet` in the signature rather than `Set`.** Marginally faster and more
precise about what is meant. Rejected because it forces callers to construct one;
`Set.of(...)` is what people write, and the mask is built by iteration either way.

**Wait for the backend SPI and bind SDL against it.** The SPI is the thing SDL
sits behind, so binding without it risks binding the wrong surface. But the
lifecycle calls are the part of SDL that no SPI shape can change — something has
to initialize the library and report which one is linked — and binding them first
answered a question about the *build* that the SPI could not have: whether an
upstream symbol can be exported at all.

## Consequences

SDL failures are impossible to ignore and arrive with their message intact.
Subsystems are a type. The version of SDL linked into `libgoldberry` is
reportable, so a mismatch between the pinned ref and what was actually built is
visible rather than assumed.

The cost is that the binding layer is no longer a pure translation of SDL. There
is policy in it now — which failures raise, which unknown values are tolerated —
and every future binding has to decide the same questions rather than inherit an
answer. The two rules above are the precedent: perishable information is captured
at the boundary, and unknown values are rejected when they mean *we* are wrong
and tolerated when they mean *the world moved on*.

Binding these nine symbols also found a real defect in the export machinery, which
had been invisible because every symbol exported until now came from
`goldberry_shim.c` — an object file, not an archive member. `--exclude-libs,ALL`
forces symbols from static archives to be local, and a version script cannot
promote a symbol the linker has already been told to hide. `SDL_Init` was linked
in, occupied four megabytes, and was not exported. The `local: *` clause in the
version script was always sufficient on its own; the extra flag was
belt-and-braces that cut the belt. It is gone, and the first upstream symbol on
the export list is what found it — which is an argument for binding something
real early rather than deferring until the design is settled.
