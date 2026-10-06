# Native image

A Goldberry application can be built as a GraalVM native image: no class loading,
no reflection on the binding path, and a start-up measured against the process
rather than against a JVM. The binding schema is designed for that.

> **Built and run in CI on linux-x64, macOS and Windows.** One 41 MiB file with nothing beside
> it, starting in well under a second and painting at about 1 ms a frame
> headless — faster than the JVM build over a short run, because there is nothing
> to warm up.
> The FFM downcalls, the six upcalls, the fonts, the icons, the stylesheets, the
> KDL, the `WidgetCatalog` service and `libgoldberry` itself all travel inside it.
>
> It logs, too, which takes one hand-written metadata entry — see
> [below](#two-metadata-directories-traced-and-written).

## Before anything else: the C toolchain

`native-image` links with the system `gcc`, so it needs a C toolchain **and the
development package for zlib** — not merely the runtime one, which is what a
desktop already has:

```
sudo apt install build-essential zlib1g-dev     # Debian / Ubuntu
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```

Without it the build runs to completion, spends a minute on analysis, and fails
at the last step with `cannot find -lz`. `zlib1g` alone is not enough: the linker
resolves `-lz` through the `libz.so` symlink that `zlib1g-dev` installs.

## The two commands

```
./gradlew :example:nativeImageMetadata -Pgraalvm.home=/path/to/graalvm
./gradlew :example:nativeImage         -Pgraalvm.home=/path/to/graalvm
```

`GRAALVM_HOME` works instead of the property. Either way it must be a **GraalVM**
and not a stock JDK — `native-image` and the tracing agent ship only with the
former, and the task says so if you point it at the wrong thing.

The result is **one file**: `example/build/native/goldberry-showcase-<target>`.
No launcher, no `lib/` directory, nothing to set. `libgoldberry` is carried inside
the binary as the same classifier-jar resource a released application would use,
and unpacked to a temporary file on first use — a shared object has to be a real
file to be `dlopen`ed, so it cannot be mapped straight out of the image.

That makes a **writable temp directory a requirement**, and
`-Dgoldberry.native.library` is the way out of one that is read-only or
`noexec`.

## Why there are two commands

A closed world has to know every foreign function the program will call before it
runs, and Goldberry's are not knowable from the source: a binding class takes a
`SymbolLookup` obtained at run time and builds its handles from it, which is what
lets an application choose which `libgoldberry` it loads. `libgoldberry` exports
**246 symbols** — SDL3 75, Yoga 69, Blend2D 56, HarfBuzz 25, libwebp 11, and 10
of the shim's own, md4c reaching Java through those rather than through exports
of its own — plus **six upcalls**, from five owners: `SdlEventWatch`,
`SdlFileDialogs`, `SdlTray`, Yoga's `MeasureCallback` and `SdlClipboard`.
The list and the bindings agree in both directions: an exported
symbol nothing binds is dead weight in every artifact, and a bound symbol nothing
exports is a link error at the first call.

So the first command **runs the showcase under GraalVM's tracing agent** and
records what it saw — the foreign descriptors, the resources, the reflection
Logback does — into

```
example/src/main/resources/META-INF/native-image/dev.goldberry/goldberry-example/
```

That path is under `src`, not `build`: the metadata is source. It is reviewed in a
diff, it changes when the application does, and being inside the jar is what lets
a downstream image build find it without being told.

**The trace is only as good as the run**, and that is why **resources are not
traced**. `:core`, `:widgets` and `:example` each ship a
`META-INF/native-image/…/reachability-metadata.json` declaring their own files by
glob, because that set is finite and a directory listing cannot be one screen
short. A stylesheet or a font the far side of a toggle the run never flipped is
not in a trace.

Because those declarations travel in the jars, an application building its own
image gets the toolkit's resources without knowing it needs them.

What is traced — the reflection, the services, the upcall stubs — really
does depend on what the code did, and the warning applies to it unchanged: a
screen the run never reaches contributes nothing. Re-run the metadata task after
adding one, and read the diff.

The **FFM descriptors are not traced**. A holder links its handle in its class
initializer, but a `…Calls` record binds when its wrapper is first *used*, so a
run that opened no Markdown would initialise no `MarkdownCalls` and a trace would
record none of its five functions. `:natives:foreignMetadata` initialises every
holder and every upcall owner and writes the `foreign` section itself, into the `goldberry-natives` jar under
`META-INF/native-image/`, where `native-image` reads it for any application.

## The image is woven, the jar is not

`nativeImage` depends on `weaveModels`, and the build orders it before `jar`.
That is not a detail: an image built from unwoven classes would bind its models
by reflection, which is the one thing an image must not do. Everything on
[the weaving page](weaving.md) about `-Pgoldberry.nativeImage=true` applies to
building the modules by hand; `nativeImage` arranges it for you.

## What the flags are for

| Flag | Why |
|---|---|
| `--module-path` / `--module` | The showcase runs modular, as it does everywhere else |
| `--enable-native-access=…natives` | JEP 472, naming the one module that touches native code |
| `--no-fallback` | Not passed. A fallback image is a JVM in a trench coat. GraalVM 25.3 builds none, and the flag only warns that it has no effect |
| `-H:+ReportExceptionStackTraces` | Names the class that could not be reached, rather than a stack in the builder |

Nothing about **class initialization** is passed here. `:natives` ships its own
`META-INF/native-image/dev.goldberry/goldberry-natives/native-image.properties`
naming the two classes that have an opinion, and they are opposite opinions:

| Class | When | Why |
|---|---|---|
| `NativeLibrary` | run time | It `dlopen`s in its initializer, which must not happen in the builder |
| `Downcalls` | **build time** | It holds the shared `Linker`, and every holder's initializer calls `Downcalls.link` |
| the `…calls` **packages** | **build time** | A downcall handle is only a call if it is a compile-time constant, and only a build-time initializer makes it one |

They are **packages** and they have to be. A holder is a nested class, and naming
its enclosing class does not reach it — measured at 4538 ns/call against 8, and
silently, because the image builds and runs. Naming a hundred and thirty-four
nested classes in a flag is not a list anyone can maintain, and naming the
*binding* packages instead would build-time initialize `Sdl` and `Blend2D`, whose
holder idiom `dlopen`s the library in the builder. So the holders live in
packages that contain nothing else.

All of them travel in the jar, so an application building its own image gets them
without knowing they exist, as it gets the resource declarations.

## The one flag the frame rate depends on

GraalVM's FFM downcalls are **not optimized** — [oracle/graal#8113](https://github.com/oracle/graal/issues/8113)
lists it as open work, and it costs a factor of 450 on the call itself. Goldberry
takes the workaround: a holder's `FD_<symbol>` handle is *unbound* (it takes the
address to call as an argument), so it can be linked while the image is being
built, which is what turns it into a constant the compiler can lower into a
direct call.

Sixty frames of the showcase, headless (`--resize=WxH` walks the
window a pixel a frame while it runs, and `--late-budget=N` fails the run past `N`
missed refreshes):

```
./example/build/native/goldberry-showcase-linux-x64     -Dgoldberry.backend.videoDriver=dummy --frames=60
```

| | 60 frames | per frame |
|---|---|---|
| without the `--initialize-at-build-time` lines | 2.55 s | 42.5 ms |
| with it | 0.061 s | **1.0 ms** |

The same rule applies one level down, and it is the trap to know before editing a
binding: **a downcall handle has to be read by the method that calls it.** Passing
one into a helper as an argument costs 810 ns a call in an image against 8.9 ns
when the helper names the constant itself — the JVM inlines and folds it, and
native-image does not. That is why a holder's `call` names its own
`FD_<symbol>` field rather than taking a handle, and why the handle is
`static final` on the holder rather than a component of it: an instance field is
a value read from an object, not a constant read from a class, and measures
4540 ns/call.

**And the list of packages is its own hazard.** `native-image.properties` is the
one file in `:natives` that nothing compiles, runs or reads — it is a hand-typed
package list consumed by a tool that is not part of this build — so a package
added to the module and not to the file is invisible in every way but the
measurement above. The **shipped resource** is held to
`ForeignSurface.holderClassNames()`, with one deliberate exception:
`desktop.calls` stays out, because `PortalSettings` binds a
dozen libdbus functions in a *static* initialiser and build-time initialising it
either fails the image with a `MemorySegment` in the image heap or bakes the
build machine's D-Bus into it.

**Nothing fails when it is missing.** The image builds, runs, paints correctly
and is forty times slower, which is why the number is written down here. (It is
silent only because the generated metadata registers the descriptors anyway; a
descriptor registered *nowhere* raises `MissingForeignRegistrationError` and
names itself.) To check it, move the properties file aside and rebuild passing
the run-time half by hand:

```
./gradlew :example:nativeImage -Pgraalvm.home=… \
    -Pgraalvm.args="--initialize-at-run-time=dev.goldberry.natives.NativeLibrary"
```

## Two metadata directories: traced, and written

The agent's output goes to
`META-INF/native-image/dev.goldberry/goldberry-example`, and **nothing
hand-written goes in there** — the next trace overwrites it. Anything a human has
to add lives in the sibling `…/goldberry-example-manual`. `native-image` reads
every `META-INF/native-image/**` it finds, so the two are merged for the tool and
kept apart for the diff.

It holds one entry:

```json
{ "module": "dev.goldberry.example", "glob": "logback.xml" }
```

Logback asks a `ClassLoader` for `logback.xml`, so the agent records it as a
**classpath** resource. The image runs on the module path, where that file is at
the root of a named module and has to be registered against that module or it is
not there at all. The symptom is the worst kind: with no configuration found,
logback ends with no appenders and prints nothing — not even its own status — so
an image that is working perfectly looks like an image that is doing nothing.

The general shape of that trap is worth remembering: **the agent records how a
lookup was made, not where the file will be.** A resource fetched through a
`ClassLoader` by a library that knows nothing of modules is recorded without one.
An application's own `logback.xml` is the same file with the same trap, so it
belongs in that application's hand-written directory too.

### What the trace does not record

The trace records no widget class, neither the toolkit's nor an application's.
The element tree asks the two widgets for their `restyle` answer and compares
them, so no widget class is reflected on, and every class in
`dev.goldberry.widget` is in a closed list, as a woven model is.

## The natives jar is a module

Each classifier jar names itself: `dev.goldberry.natives.linux_x64`,
`dev.goldberry.natives.linux_aarch64`, `dev.goldberry.natives.macos_aarch64`,
`dev.goldberry.natives.windows_x64`. A modular build keeps it on the module
path instead of splitting it onto the class path.

Nothing `requires` a platform's module, so the JVM does not resolve it, and
`NativeLibrary` reads the library from the module path anyway: this module, the
system class loader, then the jar on `--module-path` by its name. The library's
directory is not a package name, so nothing has to be opened.

**`native-image` is the exception.** An image takes only resolved modules, so
the natives jar either stays on `-cp`, which is what the showcase does, or
goes on the module path with `--add-modules dev.goldberry.natives.<target>`.

## Why the library is carried rather than linked

Statically linking the archives into the image is the obvious answer and it does
not work. The linking part is fine — `-Wl,-u,<symbol>` pulls the code in, given
`-lstdc++` and `-lm` which `native-image` does not pass. What fails is that
Goldberry resolves every native function **by name at run time**, so the symbols
have to be in the executable's dynamic symbol table, and `native-image` links with
its own `--version-script` that makes everything it does not list `local`.
`--export-dynamic-symbol` does not beat it, and a second version script is refused
outright — *"anonymous version tag cannot be combined with other version tags"*.

## What is not built

**No image task in `build`.** Every `showcase.yml` leg installs GraalVM Community,
builds the image, runs it for 300 frames with the window resized a pixel a
frame, and uploads it. On a `v*` tag the three go on the tag's GitHub Release.
The workflow runs only on a tag or by hand. Linux builds from the checked-in
trace. macOS and Windows trace first, and those traces are not reviewed. Neither
task is wired into `build`, because a local build has no GraalVM to count on.

**No image of the toolkit on its own.** `:core` and `:widgets` are libraries; an
image is a property of an application, and `:example` is the application here.
