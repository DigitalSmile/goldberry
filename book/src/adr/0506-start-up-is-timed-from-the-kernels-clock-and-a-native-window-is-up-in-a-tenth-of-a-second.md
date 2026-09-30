# 506. Start-up is timed from the kernel's clock, and a native window is up in a tenth of a second

Date: 2026-09-30

## Status

Accepted. Answers the `book/src/TODO.md` entry "'Starts in milliseconds' is still
unproven", which asked for the example launched directly rather than under
`gradle run`. Corrects [ADR-0028](0028-the-start-up-timeline.md)'s timeline,
whose zero was wrong on Linux, and with it every Linux number that timeline has
printed. Measures, and qualifies, the claim `docs/ARCHITECTURE.md` §1 and
`README.md` open with.

## Context

ADR-0028 built `Startup`: named marks from process start to the first frame,
printed at trace. Its numbers were taken under `gradle run`, so the entry asked
for the showcase launched the way a user launches it. There are two such ways —
the JVM, through the start script `installDist` writes, and the GraalVM native
image, which is what a release ships (ADR-0340).

The first measurement looked like a finding and was a bug. A native image, whose
process has no JVM to start, reported 275–292 ms before the toolkit's first line.
Timed against a clock read just before `exec`, the same line came at 52–60 ms.
Under `strace` the process reached `libgoldberry` 66 ms after `execve`.

The timeline's zero was `ProcessHandle.current().info().startInstant()`. On Linux
the JDK builds that instant from `/proc/self/stat`'s `starttime`, in clock ticks
since boot, plus `/proc/stat`'s `btime` — the boot time, in whole **seconds**. The
fraction `btime` drops is an error of up to a second, the same for every process
on a boot. On this one it was about 218 ms, and every "runtime starting" row since
ADR-0028 carried it.

## Decision

### The zero comes from the kernel's clock at both ends

`ProcessAge` reads `starttime` from `/proc/self/stat` and the uptime from
`/proc/uptime`. Both count from the same boot on the same clock, so their
difference is the process's age with no wall clock in it, to the kernel's
10 ms tick. `USER_HZ` is the kernel's user ABI constant, 100, rather than a
native call from `:common`, which makes none. The command in `stat`'s second
field may contain spaces and parentheses, so fields are counted from the last
closing one.

Where `/proc` is absent or unreadable, `ProcessHandle` answers as before: macOS
and Windows report a start time to the microsecond, and the fault was Linux's.

After the change the timeline agrees with the external clock to within 10 ms on
every run below, where it had been 218 ms late.

### What the claim is, measured

The showcase, on this machine (8 cores, NVIDIA, GNOME on XWayland), a real X11
window, three frames, timed from `exec` by an outside clock and by the corrected
timeline, which agree:

| Launch | First frame, median | Range |
|---|---|---|
| JVM, cold | 1980 ms | 1948–2187 (5 runs) |
| JVM, JDK 25 AOT cache | 1340 ms | 1220–1406 (5 runs) |
| Native image, GPU on | 519 ms | 491–557 (7 runs) |
| Native image, GPU off | 365 ms | 351–460 (7 runs) |

A native run, phase by phase:

```text
   70.1ms   runtime starting            (about 50 ms of it an mDNS host lookup)
   86.6ms   SDL video subsystem up      (14.1ms)
  113.1ms   libgoldberry ABI 17 verified (26 ms of loading WebKitGTK before it)
  116.2ms   window "Goldberry — showcase" open
  365.3ms   GPU device created          (190.1ms on NVIDIA's driver)
  511.0ms   first frame presented
```

So **the window is up in a tenth of a second, and the first frame in half a
second**, in what a release ships. "Starts in milliseconds" is true of the window
and generous about the frame. On the JVM it is not true: two seconds cold, most
of it before the toolkit's first line and in building the showcase's first
frame. SDL's video subsystem, which dominated ADR-0028 at 99 ms, is 14 ms now.

### The JVM's answer is JDK 25's AOT cache, and it is the application's to train

`-XX:AOTCacheOutput=app.aot` on one training run and `-XX:AOTCache=app.aot`
afterwards (JEP 483, JEP 514, JEP 515) took a third off the JVM's first frame:
the classes arrive loaded and linked and the hot methods profiled. The cache is
37 MB, specific to the JDK build and the module path, and trained by running the
application. That is why it is **documented, not built in**: the toolkit is a
library, and the cache belongs to an application's own start-up and its own
screens. `book/src/applications.md` says how. The showcase ships as a native
image, so its start script gains nothing.

## Consequences

- `Startup`'s numbers are right on Linux, and ADR-0028's table should be read as
  about 200 ms early at every row; its deltas stand.
- `docs/ARCHITECTURE.md` §1 and `README.md` carry the numbers beside the claim.
  §1's "the device costs about 20 ms at the first frame, measured on macOS" is
  190–320 ms on NVIDIA's Vulkan driver here.
- Four things were found and are **not** fixed here; each is a `TODO.md` entry:
  - **A native image resolves a host name before `main`,** through
    `libnss_mdns4_minimal`, about 50 ms. The JVM does not: its only
    `nsswitch.conf` read is for the user's name. Logback resolves `HOSTNAME`
    lazily and this configuration never asks for it, so the caller is not
    logback's `ContextBase`. A stripped image did not say whose it is.
  - **`libgoldberry-webview` is opened at start-up,** to answer
    `Capability.WEB_VIEW`, and it brings WebKitGTK and GTK 3: 26 ms before a
    page is asked for. `docs/content-widgets.md` §11 calls the library "opened on
    demand"; the capability question is the demand.
  - **The native image cannot install the GLib log handler**: "no handle to bind
    a GLib callback to", so GLib's messages go to stderr there, which ADR-0443
    exists to prevent. An upcall the image does not register.
  - **The GPU device is a quarter of a native start** on this driver. That is
    ADR-0480's default meeting a slow driver; `goldberry.gpu=off` is the
    measured alternative.

## Alternatives considered

- **Correcting `ProcessHandle`'s instant by a measured offset.** The offset is a
  property of the boot, not of the machine, and there is nothing to measure it
  against inside the process except `/proc/uptime` — which, once read, makes the
  instant unnecessary.
- **Timing from the toolkit's first line.** It would have hidden the bug and the
  host lookup together, and ADR-0028's reason stands: a user waits for the
  process, not for the toolkit.
- **An AOT cache built into the showcase's start script.** A cache missing or
  trained on another JDK is a warning at every launch, and the start script is
  not what ships.
