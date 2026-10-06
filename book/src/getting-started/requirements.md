# Requirements

<p class="gb-lede">A JDK 25 and a desktop. Everything else is inside the jars.</p>

## To run an application

| Need | Detail |
|---|---|
| Java | JDK 25 or newer, any vendor. The toolkit uses the Foreign Function & Memory API, which is final in 25, and the Vector API for the blur path |
| A desktop | Linux on Wayland or X11, Windows, or macOS. Native libraries are built for `linux-x64`, `linux-aarch64`, `macos-aarch64` and `windows-x64` |
| On Linux | glibc 2.28 or newer. The Linux libraries are built in a `manylinux_2_28` container, so RHEL 8 and anything younger loads them |
| A writable temporary directory | `libgoldberry` travels inside a jar and is unpacked to a temporary file on first use, because a shared object has to be a real file to be loaded. `-Dgoldberry.native.library=<path>` points at one already on disk, for a read-only or `noexec` temp |
| No GPU | Frames are rasterized on the CPU. With `goldberry-gpu` on the module path a window presents through the GPU where a device exists and on the CPU where it does not |
| No third-party Java libraries | The toolkit depends on nothing outside the JDK. SLF4J is the one API it logs through, and it binds no implementation |

> [!IMPORTANT]
> **On macOS the UI thread must be the process's first thread.** Launch with
> `-XstartOnFirstThread`, exactly as for LWJGL or SWT. Without it `SDL_Init`
> fails with *No available video device*, which says nothing about threads.

### What the desktop provides at run time

SDL opens the platform's own libraries when it needs them, so a desktop that
can show a window already has what a window needs. Two integrations are worth
knowing about on Linux:

- **Audio** comes through PulseAudio, PipeWire's PulseAudio layer included, or
  ALSA. A machine with no audio device plays silently rather than failing.
- **The desktop's theme, file dialogs and input method** are asked over D-Bus,
  IBus and udev. A build of the toolkit says what it can ask through
  `Goldberry.capabilities()`, so an application can tell *this desktop has no
  such setting* from *this build cannot ask*.

A web view needs the desktop's own engine, WebKitGTK, WebView2 or WKWebView,
through a separate optional library. It is never a load-time dependency of the
toolkit.

## To build a native binary

| Need | Detail |
|---|---|
| GraalVM | GraalVM Community for JDK 25, the 25.3 line. A stock JDK has no `native-image` |
| A C toolchain | `native-image` links with the system compiler. On Linux it also needs zlib's development package: `build-essential zlib1g-dev` on Debian and Ubuntu, `gcc glibc-devel zlib-devel libstdc++-static` on Fedora. Without it the build runs for a minute and fails at the last step with `cannot find -lz` |
| The weaver | A build step over your compiled classes, one Gradle plugin or one Maven execution. [Model weaving](../weaving.md) explains why an image needs it and a jar does not |

## To build the toolkit itself

Only if you are working on Goldberry. A JDK 25, CMake 3.28 or newer, Ninja
and a C and C++ toolchain. The first native build downloads about 330 MB of
upstream sources. [Building from source](../contributing/building.md) has the
details and the Java-only build that skips all of it.

## Read next

<div class="gb-cards">
<a class="gb-card" href="installing.html"><strong>Installing</strong><span>The BOM, the umbrella artifact and the natives jar for your platform.</span></a>
<a class="gb-card" href="first-java-application.html"><strong>Your first Java application</strong><span>A model, a document, a stylesheet and a window.</span></a>
</div>
