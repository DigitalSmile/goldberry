# Native image

A Goldberry application can be built as a GraalVM native image: no class loading,
no reflection on the binding path, and a start-up measured against the process
rather than against a JVM. That is what
[ADR-0127](adr/0127-the-binding-schema-fits-a-closed-world.md) designed the
binding schema for.

> **Built and run on linux-x64; not in CI.** One 41 MiB file with nothing beside
> it, starting in well under a second and painting at about 1 ms a frame
> headless — faster than the JVM build over a short run, because there is nothing
> to warm up ([ADR-0161](adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md)).
> The FFM downcalls, the two upcalls, the fonts, the icons, the stylesheets, the
> KDL, the `WidgetCatalog` service and `libgoldberry` itself all travel inside it.
>
> It logs, too, which took one hand-written metadata entry — see
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
file to be `dlopen`ed, so it cannot be mapped straight out of the image
([ADR-0159](adr/0159-a-native-image-carries-its-own-library.md)).

That makes a **writable temp directory a requirement**, and
`-Dgoldberry.native.library` is still the way out of one that is read-only or
`noexec`.

## Why there are two commands

A closed world has to know every foreign function the program will call before it
runs, and Goldberry's are not knowable from the source: a binding class takes a
`SymbolLookup` obtained at run time and builds its handles from it, which is what
lets an application choose which `libgoldberry` it loads. `libgoldberry` exports
184 symbols, plus one upcall for Yoga's measure callback.

So the first command **runs the showcase under GraalVM's tracing agent** and
records what it saw — the foreign descriptors, the resources, the reflection
Logback does — into

```
example/src/main/resources/META-INF/native-image/io.github.digitalsmile/goldberry-example/
```

That path is under `src`, not `build`: the metadata is source. It is reviewed in a
diff, it changes when the application does, and being inside the jar is what lets
a downstream image build find it without being told (ADR-0156).

**The trace is only as good as the run**, and that is why **resources are not
traced**. `:core`, `:widgets` and `:example` each ship a
`META-INF/native-image/…/reachability-metadata.json` declaring their own files by
glob, because that set is finite and a directory listing cannot be one screen
short. The first image built here proved the point by omitting `nord-light.css`,
`density-compact.css`, `JetBrainsMono.ttf` and `OpenMoji-black.ttf` — every one
the far side of a toggle the run never flipped
([ADR-0160](adr/0160-a-modules-own-resources-are-declared-not-traced.md)).

Because those declarations travel in the jars, an application building its own
image gets the toolkit's resources without knowing it needs them.

What is still traced — the reflection, the services, the upcall stubs — really
does depend on what the code did, and the warning applies to it unchanged: a
screen the run never reaches contributes nothing. Re-run the metadata task after
adding one, and read the diff.

The **FFM descriptors are the exception**, and stopped being run-dependent with
ADR-0161: they are linked in `Downcalls`' class initializer, which runs on any
JVM start, so the agent records all 56 of them whether or not the run reached the
screen that uses them.

## The image is woven, the jar is not

`nativeImage` depends on `weaveModels`, and the build orders it before `jar`.
That is not a detail: an image built from unwoven classes would bind its models
by reflection, which is the one thing an image must not do
([ADR-0155](adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)). Everything on
[the weaving page](weaving.md) about `-Pgoldberry.nativeImage=true` applies to
building the modules by hand; `nativeImage` arranges it for you.

## What the flags are for

| Flag | Why |
|---|---|
| `--module-path` / `--module` | The showcase runs modular, as it does everywhere else ([ADR-0007](adr/0007-jpms-modules-enforce-the-native-boundary.md)) |
| `--enable-native-access=…natives` | JEP 472, naming the one module that touches native code |
| `--no-fallback` | A fallback image is a JVM in a trench coat. Failing is the useful answer |
| `-H:+ReportExceptionStackTraces` | Names the class that could not be reached, rather than a stack in the builder |

Nothing about **class initialization** is passed here. `:natives` ships its own
`META-INF/native-image/io.github.digitalsmile/goldberry-natives/native-image.properties`
naming the two classes that have an opinion, and they are opposite opinions:

| Class | When | Why |
|---|---|---|
| `NativeLibrary` | run time | It `dlopen`s in its initializer, which must not happen in the builder |
| `Downcalls` | **build time** | A downcall handle is only a call if it is a compile-time constant, and only a build-time initializer makes it one ([ADR-0161](adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md)) |

Both travel in the jar, so an application building its own image gets them
without knowing they exist — the same argument ADR-0160 makes for resources.

## The one flag the frame rate depends on

GraalVM's FFM downcalls are **not optimized** — [oracle/graal#8113](https://github.com/oracle/graal/issues/8113)
lists it as open work, and it costs a factor of 450 on the call itself. Goldberry
takes the workaround: the handles in `Downcalls` are *unbound* (they take the
address to call as an argument), so the class can be initialized while the image
is being built, which is what turns each one into a constant the compiler can
lower into a direct call.

Sixty frames of the showcase, headless, on this machine:

```
./example/build/native/goldberry-showcase-linux-x64     -Dgoldberry.backend.videoDriver=dummy --frames=60
```

| | 60 frames | per frame |
|---|---|---|
| without `--initialize-at-build-time=…Downcalls` | 2.55 s | 42.5 ms |
| with it | 0.061 s | **1.0 ms** |

The same rule applies one level down, and it is the trap to know before editing a
binding: **a downcall handle has to be read by the method that calls it.** Passing
one into a helper as an argument costs 810 ns a call in an image against 8.9 ns
when the helper names the constant itself — the JVM inlines and folds it, and
native-image does not. That is why the constants are named for signatures rather
than for functions, and why the invocation helpers in the binding classes name
`Downcalls.INT__PTR` inside themselves rather than taking a handle.

**Nothing fails when it is missing.** The image builds, runs, paints correctly
and is forty times slower, which is why the number is written down here. (It is
silent only because the traced metadata registers the descriptors anyway; a
descriptor registered *nowhere* raises `MissingForeignRegistrationError` and
names itself.) To check it, move the properties file aside and rebuild passing
the run-time half by hand:

```
./gradlew :example:nativeImage -Pgraalvm.home=… \
    -Pgraalvm.args="--initialize-at-run-time=io.github.digitalsmile.goldberry.natives.NativeLibrary"
```

## Two metadata directories: traced, and written

The agent's output goes to
`META-INF/native-image/io.github.digitalsmile/goldberry-example`, and **nothing
hand-written goes in there** — the next trace overwrites it. Anything a human has
to add lives in the sibling `…/goldberry-example-manual`. `native-image` reads
every `META-INF/native-image/**` it finds, so the two are merged for the tool and
kept apart for the diff.

There is exactly one entry in it so far, and it is instructive:

```json
{ "module": "io.github.digitalsmile.goldberry.example", "glob": "logback.xml" }
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

## Why the library is carried rather than linked

Statically linking the archives into the image is the obvious answer and it does
not work. The linking part is fine — `-Wl,-u,<symbol>` pulls the code in, given
`-lstdc++` and `-lm` which `native-image` does not pass. What fails is that
Goldberry resolves every native function **by name at run time**, so the symbols
have to be in the executable's dynamic symbol table, and `native-image` links with
its own `--version-script` that makes everything it does not list `local`.
`--export-dynamic-symbol` does not beat it, and a second version script is refused
outright — *"anonymous version tag cannot be combined with other version tags"*.

ADR-0159 records the experiments. Revisit if `native-image` grows a way to extend
its export list.

## What is not built

**No CI job.** Neither task is wired into `build`, because a task that needs a
tool the build machines do not have would be a red build for a missing download.
Adding a GraalVM to the matrix is the natural next step and is not done.

**No image of the toolkit on its own.** `:core` and `:widgets` are libraries; an
image is a property of an application, and `:example` is the application here.
