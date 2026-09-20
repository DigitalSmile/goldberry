# 451. A shape is declared where it can be reached, not where it is linked

Date: 2026-09-21

## Status

Accepted.

## Context

The Linux showcase's native image built, started, painted, and died on its first
frame:

```
MissingForeignRegistrationError: Cannot perform downcall with leaf type (long,int,long)long
  ...
  at ...natives.desktop.calls.Bindings.link(Bindings.java:30)
  at ...natives.desktop.calls.PortalSettings$Call2.<init>(PortalSettings.java:252)
  at ...natives.desktop.DesktopMotion.linux(DesktopMotion.java:89)
  at ...render.backend.sdl3.Sdl3Backend.reducedMotion(Sdl3Backend.java:1250)
  at ...Window.reducedMotion(Window.java:257)
  at ...Launcher.renderer(Launcher.java:615)
```

The missing shape is `dbus_bus_get`: `void* (jint, void*)`.

[ADR-0339] exists to make exactly this impossible. It replaced a traced run —
which records the screens the trace reached — with a generator that records
**every holder there is**: `Downcalls.link` remembers each descriptor it is
handed, `ForeignSurface` initialises every class in every `…calls` package, and
`ForeignMetadata` writes the lot. Its own commit message names the case: "a
`...Calls` record binds on first use, so the Markdown parser's holders were
never initialised and never recorded".

`PortalSettings` *is* in a `…calls` package and *was* initialised. It still went
unrecorded, for a reason ADR-0339 did not have to consider, because at the time
every binding was against `libgoldberry`.

[ADR-0383] added the first bindings that are not. `MacMotion`, `WindowsMotion`
and `PortalSettings` talk to `libobjc`, `user32` and `libdbus` — libraries the
toolkit does not ship and the machine may not have — so they bind through
`Bindings` rather than `Downcalls`, with the opposite failure policy: a missing
symbol is a missing feature, not a broken export list. That much is right and
stays. The ADR then wrote:

> …every descriptor it links is recorded for the native image's metadata.
> Neither applies to a system library that is `dlopen`ed by name and may not be
> there at all.

The second half does not follow. Whether a library is on the machine that
**builds** an image says nothing about whether it is on the machine that
**runs** it. An unrecorded shape is not a shape that will not be crossed; it is
a crash on the first desktop that has the library.

And there was a second layer, which is why simply recording in `Bindings.link`
would have been a half-fix. The descriptor was constructed *inside the
constructor that links it*:

```java
Call2(SymbolLookup lookup, String symbol) {
    this(Bindings.link(FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS)),
         Bindings.symbol(lookup, symbol));
}
```

`Calls.find()` returns null when there is no libdbus, so that constructor never
runs on a machine without one, so the descriptor is never *built*, let alone
recorded. The generator runs on the build machine. A build machine without
libdbus — which is the ordinary case, and is the machine this was developed on —
produces metadata with the shape missing, silently, and the image is wrong in a
way nothing on that machine can show.

That is why it survived: every gate passed. The JVM needs no metadata. The
native image *built*. The failure needs a machine that has libdbus and an image
built by a machine that might not — which is precisely CI, and the showcase is
the only job that runs an image.

## Decision

**A foreign shape is declared where it can always be reached, and linked where
the library is.** Two different places, and conflating them was the bug.

`Downcalls.describe(descriptor)` records a shape and hands it straight back,
without linking — the exact twin of [`Upcalls.describe`][ADR-0339], which exists
because an upcall's descriptor reaches `Linker.upcallStub` too late for an image
to hear about it. A downcall against an absent library is late for the same
reason, and now has the same answer.

Every descriptor in `…desktop.calls` is a constant declared through it:

```java
private record Call2(MethodHandle handle, MemorySegment address) {

    /// `DBusConnection *dbus_bus_get(DBusBusType, DBusError *)`.
    private static final FunctionDescriptor FD = Bindings.describe(
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    Call2(SymbolLookup lookup, String symbol) {
        this(Bindings.link(FD), Bindings.symbol(lookup, symbol));
    }
```

A `static final` on a holder runs when the class is initialised, and
`ForeignSurface` initialises every class in a `…calls` package — nested records
included, which they already were — on **any** machine. The link stays exactly
where it was and stays conditional.

`Downcalls.link` is now `LINKER.downcallHandle(describe(descriptor))`, which is
what it always did, spelled as the two steps it is.

### Why not record in `Bindings.link`

It would fix the reported crash on a build machine that has libdbus and leave it
in place on one that does not, which is the harder failure to find and the more
common machine. A guard that works only where the bug is already absent is not a
guard.

## Consequences

**The Linux showcase image can ask the desktop about reduced motion.** So can
any application built from a published `goldberry-natives` jar — the metadata
travels inside it, so this was every native image, not just the showcase's.

**macOS and Windows were equally broken and equally fixed.** Neither had reached
its own holder yet: `MacMotion`'s `objc_msgSend` shapes and `WindowsMotion`'s
`SystemParametersInfoW` were missing from the metadata for the same reason, and
would have failed the same way on the first image that asked. The macOS
showcase's failure is unrelated and is not this.

**The generated `reachability-metadata.json` goes from 77 downcall shapes to
82** — measured here, on a machine with no libdbus, by generating the file with
and without the fix. The three holders declare twelve shapes between them; the
other seven were already in the file by coincidence, having the same shape as
some `libgoldberry` holder. That coincidence is why four rather than five of
the shapes the new test names failed before the fix, and it is worth knowing
about: it means a shape can be missing from the metadata and still work, until
the day the holder it was borrowing from changes.

**Two tests, and both fail on the old code.**
`systemLibraryShapesAreDeclared` names the five shapes and asserts they are
reported *whatever this machine has installed*, which is the property that
matters and the one that cannot be checked by having the library.
`systemLibraryShapesAreConstants` reads the three sources and refuses a
descriptor written inline at a `Bindings.link(…)` call site, so the next holder
cannot reintroduce it. `everyHandleIsCovered`, the existing guard, cannot see
these: it looks for a `static final MethodHandle FD_…`, and a system-library
holder deliberately has none.

**A holder against a system library now says its C prototype in a doc comment**,
like every `libgoldberry` holder already does ([ADR-0173]) — a side effect of
having somewhere to write it.

[ADR-0173]: 0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md
[ADR-0339]: 0339-a-foreign-call-is-registered-because-it-exists-not-because-a-run-reached-it.md
[ADR-0383]: 0383-the-desktop-is-asked-whether-to-move-less.md
