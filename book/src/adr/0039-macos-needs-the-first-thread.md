# ADR-0039: macOS needs the first thread

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §4, [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md), [ADR-0026](0026-sdl-picks-the-video-driver.md)

## Context

`./gradlew run` on macOS failed:

```text
BackendException: SDL could not initialize its video subsystem
Caused by: SdlException: SDL_Init failed: No available video device
```

That message is wrong in the way that costs the most time: it points at the
build. "No available video device" reads as a missing driver, a library linked
without Cocoa, a headless session — and the superbuild had just been changed, so
the superbuild was where everyone looked. It was not the superbuild.

Three probes settled it, and are worth recording because each one eliminated a
whole class of explanation:

1. A plain C program linked against the same `libSDL3.a` reported three
   compiled-in drivers — `cocoa`, `offscreen`, `dummy` — and initialized `cocoa`.
   So SDL 3.2.0 builds correctly on macOS 26, and the window server was
   reachable.
2. The same C program `dlopen`ing `libgoldberry.dylib` and calling `SDL_Init`
   through it also succeeded. So the packaging — static archives, hidden
   visibility, `-dead_strip`, a 30-symbol export list — is sound, and
   `_COCOA_bootstrap` really is in the binary.
3. The JVM with `-XstartOnFirstThread` succeeded, and without it failed.

macOS requires AppKit to be driven from the process's first thread. The `java`
launcher runs `main` on a secondary thread unless given `-XstartOnFirstThread`,
so SDL's Cocoa driver refuses to create a device; no other driver is usable; and
SDL reports the only thing it knows, which is that nothing was available. Nothing
in that chain mentions threads.

This had not been caught because the macOS CI leg builds and links the library
and runs the `:natives` tests, but does not run the showcase — the leg that opens
a window runs on Linux under Xvfb. macOS was verified as far as "it links",
which is exactly as far as the failure was invisible.

## Decision

Two changes, at two levels.

**The showcase passes the flag.** `example/build.gradle` appends
`-XstartOnFirstThread` to `applicationDefaultJvmArgs` when the host is macOS.
Conditional rather than unconditional: no other platform has the flag, and a
build file that hands every platform a macOS-only argument invites the question
of why.

**The toolkit explains itself.** When `SDL_Init` fails, `Sdl3Backend` checks
whether it is on macOS without the flag and, if so, appends what the flag is and
why it is needed. The check reads `JAVA_STARTED_ON_FIRST_THREAD_<pid>`, which the
macOS launcher sets to `1` when it honours the flag — the only way to ask this
question from Java.

Deliberately a *diagnosis appended to a failure*, not a precondition checked up
front. The environment variable is set by the launcher, so a JVM embedded through
`JNI_CreateJavaVM` on the real main thread would lack it and would nonetheless
work. Refusing to start on that evidence would break a working configuration to
protect against a broken one. Everything is therefore phrased as "this is
probably why", and is only ever said after SDL has already said no.

## Alternatives considered

**Fail fast, before touching SDL.** A clearer failure, one step earlier, in the
style of `:natives:checkToolchain`. Rejected for the embedded-JVM false positive
above: `checkToolchain` tests for tools that are genuinely absent, whereas this
would test for a launcher flag that is only a proxy for the thing that matters.
A proxy is fine for explaining a failure and not fine for causing one.

**Re-exec the JVM with the flag when it is missing.** Some toolkits do this.
It means a library deciding to restart the application's process, which is a
larger power than a UI toolkit should take, and it interacts badly with anything
that already owns the process — a test runner, an IDE, an embedder.

**Run SDL on a thread we control and pretend.** There is no such thread. AppKit's
requirement is the process's first thread specifically, not "one consistent
thread", so no amount of confinement inside Goldberry can satisfy it. ADR-0020's
one-UI-thread model is compatible with this and does not replace it: the UI
thread must *be* the first thread on macOS.

**Document it in the README and stop.** The README now does say it, and that
helps the person setting up. It does not help the person who already has the
stack trace, which is everyone who hits this — and the stack trace was actively
misleading. The message is where the fix belongs.

## Consequences

`./gradlew run` works on macOS. That is new; it had never worked, and the status
table's claim that the showcase opens a window was true only on Linux.

Any application embedding Goldberry on macOS must pass `-XstartOnFirstThread`
itself. Goldberry cannot do it for them — a JVM flag is fixed at launch. This is
the same requirement LWJGL, GLFW and SWT place on their users, so it is at least
a familiar one, but it is a real constraint on the "just add the dependency"
story and it belongs in the getting-started documentation for as long as macOS is
a supported target.

`-XstartOnFirstThread` has a further consequence not explored here: it makes the
first thread the AppKit thread, which is what AWT/Swing also want. An application
mixing Goldberry with Swing on macOS may find they contend. Nothing tests that,
and nothing should be claimed about it.

The macOS CI leg still does not run the showcase, so this exact failure could
return without CI noticing. Closing that hole means running the example on the
macOS runner — worth doing, and not done here.
