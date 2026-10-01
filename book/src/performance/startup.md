# Starting fast

<p class="gb-lede">How long a user waits depends on how you launch, and this chapter says what each launch costs and what to keep off the path to the first frame.</p>

By the end of this chapter you can read the start-up timeline your own
application prints, choose a launch mode with its number in front of you,
train an AOT cache for the JVM, and keep your own work out of the first frame.

## From exec to the first frame

The user waits for the process, not for the toolkit. So the clock starts at
`exec`, before any Java runs. The showcase, on one Linux machine with eight
cores, an NVIDIA GPU and GNOME on XWayland, opening a real X11 window and
painting three frames
([ADR-0506](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md)):

| Launch | First frame, median | Range |
|---|---|---|
| JVM, cold | 1980 ms | 1948–2187 ms, 5 runs |
| JVM, JDK 25 AOT cache | 1340 ms | 1220–1406 ms, 5 runs |
| Native image, GPU on | 519 ms | 491–557 ms, 7 runs |
| Native image, GPU off | 365 ms | 351–460 ms, 7 runs |

A native image is what a release of the showcase ships, and it is the fastest
of the four. On the JVM, two seconds cold is honest and most of it is spent
before the toolkit's first line and in building the showcase's first frame.
The AOT cache takes a third off that.

## What the toolkit does in its time

A native run, phase by phase, from the same record. The first column is time
since `exec`. A duration in parentheses is how long that one phase took:

```text
   70.1ms   runtime starting            (about 50 ms of it an mDNS host lookup)
   86.6ms   SDL video subsystem up      (14.1ms)
  113.1ms   libgoldberry ABI 17 verified (26 ms of loading WebKitGTK before it)
  116.2ms   window "Goldberry — showcase" open
  365.3ms   GPU device created          (190.1ms on NVIDIA's driver)
  511.0ms   first frame presented
```

The window is open at 116.2 ms. Creating the GPU device takes 190.1 ms of
what follows, and the first frame comes after it. Three things in that listing are not the toolkit's own
work and each has an entry in the TODO: a host-name lookup before `main`,
WebKitGTK loaded to answer a capability question, and the driver's device
creation.

The JVM timeline that the landing page quotes comes from the first record,
taken under `gradle run`
([ADR-0028](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0028-the-start-up-timeline.md)):

```text
start-up timeline (866.6ms to here):
     533.8ms    +533.8ms  runtime starting
     559.6ms     +25.8ms  libgoldberry mapped (1.9ms)
     722.6ms    +162.9ms  SDL video subsystem up (99.2ms)
     728.0ms      +5.4ms  backend ready (185.5ms)
     742.9ms     +12.9ms  SDL window 4 created
     749.5ms      +6.6ms  window "Goldberry — showcase" open
     866.6ms    +117.1ms  first frame presented
```

The first row is the JVM and Gradle's launcher. Everything after it is the
toolkit, and the deltas sum to about 330 ms: the library is mapped in under
2 ms, SDL's video subsystem was 99 ms then, and the first frame 117 ms.
ADR-0506 later found that timeline's zero to be about 200 ms late on Linux.
Its deltas stand, and SDL's video subsystem is 14 ms today.

## The TRACE timeline and how to read it

Goldberry records a mark at each phase whether or not anybody listens, and
prints the table once, after the first frame reaches the screen. The
showcase's `logback.xml` reads one property for the level:

```sh
./gradlew run -Dgoldberry.log.level=TRACE
```

In your own application set the `dev.goldberry` logger to `trace` in whatever
SLF4J binding you ship. The [logging chapter](../guide/logging.md) has the
configuration.

Read the table as deltas. The second column is what each phase cost since the
one before it, and that is the column to look at when a start is slow. The
first row is what the runtime spent before Goldberry's first line, and it is
printed on purpose: leaving it out would flatter the number by the amount
nobody can change. `Startup.logModules()` prints which Goldberry modules the
JVM resolved, which is the first thing to check when a widget package seems
to be missing.

Four rules shape the timeline
([ADR-0028](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0028-the-start-up-timeline.md)):

- Timed from process start. On Linux the zero comes from the kernel's own
  clock through `/proc`, because `ProcessHandle`'s instant is up to a second
  late there
  ([ADR-0506](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md)).
- Recorded always, reported at trace. A mark costs a timestamp and a queue
  append.
- Summarised once, after the first frame. A second window is not a second
  start-up.
- Capped at 256 marks, so a mark left in a loop costs a counter and nothing
  else.

## The JDK 25 AOT cache

On the JVM the single largest saving is a cache the application trains for
itself. JEP 483, JEP 514 and JEP 515 together let the JDK load classes
pre-linked and keep the profiles of hot methods
([ADR-0506](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md)).

<div class="gb-steps">
<div><p>Run the application once with the cache output flag, and walk through the screens a user opens first.</p></div>
<div><p>Launch it with the cache flag from then on. Classes arrive loaded and linked, and the hot methods are already profiled.</p></div>
<div><p>Rebuild the cache when the JDK build or the module path changes. A stale cache is a warning at every launch.</p></div>
</div>

```sh
# Training run: open the screens a user sees first, then quit.
java -XX:AOTCacheOutput=app.aot --module-path lib -m my.app

# Every launch after it.
java -XX:AOTCache=app.aot --module-path lib -m my.app
```

The cache is 37 MB and belongs to the application. It is specific to the JDK
build and the module path and it is trained on your screens, which is why the
toolkit documents it and does not ship one. The
[Starting fast section of Applications](../applications.md#starting-fast)
says the same in fewer words.

## The native image, and why it is fast

A GraalVM native image has no JVM to start and no classes to load, so the
executable reaches its first frame after the toolkit's own work. The
[first native application](../getting-started/first-native-application.md)
chapter walks the build. The command is one Gradle task:

```sh
./gradlew :example:nativeImage -Pgraalvm.home=/path/to/graalvm
./example/build/native/goldberry-showcase-linux-x64
```

One flag decides whether the image is fast once it is running. GraalVM's FFM
downcalls are not optimised, so Goldberry links every downcall handle while
the image is built and holds it as a constant
([ADR-0161](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md),
[ADR-0173](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)).
Sixty frames of the showcase, headless:

| | 60 frames | Per frame |
|---|---|---|
| Without the `--initialize-at-build-time` lines | 2.55 s | 42.5 ms |
| With them | 0.061 s | 1.0 ms |

The image builds and paints correctly either way, so the number is written
down rather than caught. [Native image](../native.md#the-one-flag-the-frame-rate-depends-on)
explains the mechanism and the test that keeps the package list honest.

## The GPU device at the first frame

With `goldberry-gpu` on the module path every window presents through the GPU
by default
([ADR-0480](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md)).
The device is created at the first frame, and what it costs is the driver's
to decide:

| Machine | Device creation | Record |
|---|---|---|
| M1 Pro, macOS, Metal | 19.5–21.3 ms at the first frame | [ADR-0480](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md) |
| NVIDIA, Linux, Vulkan | 190.1 ms, a quarter of a native start | [ADR-0506](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md) |

The measured alternative is to keep every window on the CPU:

```sh
./example/build/native/goldberry-showcase-linux-x64 -Dgoldberry.gpu=off
```

On the NVIDIA machine that takes the native first frame from 519 ms to 365 ms.
An application that shows no `canvas3d` and no video loses nothing by it, and
the [GPU chapter](../components/gpu.md) says what the path adds.

## What an application keeps out of start

The toolkit's part of a start is fixed. Yours is not, and two habits keep it
small.

**Open what has to be closed in `start`, not in a build.** A widget is a value
that is rebuilt and thrown away, so an `Icon` built inside `build()` is parsed
from the bundled set and scaled to its size on every rebuild
([ADR-0043](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0043-icons-are-stroked-paths.md)). `Application.start`
runs once on the UI thread before the first frame and before `root()`, and that
is where an icon, a font source or an image belongs. Fonts open lazily: a face
is parsed the first time something asks for it, so an application that never
draws code text never pays for JetBrains Mono.

```java
public final class Notes implements Application {

    private Icon save;

    @Override
    public void start(Host host) {
        save = Icon.bundled("save", Icons.SLOT);   // once, before the first frame
        host.shortcut(Mod.CTRL.and(Key.S), model::save);
    }

    @Override
    public Widget root() {
        return new Button("Save", model::save).withIcon(save);   // the same value every build
    }
}
```

**Do slow work with `Goldberry.async`.** It runs the work off the UI thread and
delivers the result on it, so the callback may touch a window with no hand-off
to write
([ADR-0020](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)).
A file read, a network call or a database query in `start` holds the first
frame for exactly as long as it takes.

```java
@Override
public void start(Host host) {
    Goldberry.async(() -> Files.readString(path))
             .thenAccept(text -> model.setNote(text));   // on the UI thread
}
```

> [!TIP]
> Measure before guessing. The first timeline ever printed showed SDL's video
> subsystem at 99 ms and the four-megabyte native library at under 2 ms, which
> is the opposite of what most people would guess
> ([ADR-0028](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0028-the-start-up-timeline.md)).
