# Native image

A Goldberry application can be built as a GraalVM native image: no class loading,
no reflection on the binding path, and a start-up measured against the process
rather than against a JVM. That is what
[ADR-0127](adr/0127-the-binding-schema-fits-a-closed-world.md) designed the
binding schema for.

> **Built and run in CI on linux-x64, macOS and Windows** ([ADR-0337](adr/0337-the-native-showcase-is-built-on-every-platform.md)) — *wired, not yet run there*. One 41 MiB file with nothing beside
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

The **FFM descriptors are no longer traced at all**
([ADR-0339](adr/0339-a-foreign-call-is-registered-because-it-exists-not-because-a-run-reached-it.md)).
This page used to say they were never run-dependent, because a holder links its
handle in its class initializer; it forgot that a `…Calls` record binds when its
wrapper is first *used*, so a run that opened no Markdown initialised no
`MarkdownCalls` and the agent recorded none of its five functions — which is how
the Windows image died on the Markdown screen with `MissingForeignRegistrationError`.
Now `:natives:foreignMetadata` initialises every holder and every upcall owner
and writes the `foreign` section itself, into the `goldberry-natives` jar under
`META-INF/native-image/`, where `native-image` reads it for any application.

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
| ~~`--no-fallback`~~ | Removed. A fallback image was a JVM in a trench coat; GraalVM 25.3 no longer builds them, and the flag only warned that it had no effect ([ADR-0337](adr/0337-the-native-showcase-is-built-on-every-platform.md)) |
| `-H:+ReportExceptionStackTraces` | Names the class that could not be reached, rather than a stack in the builder |

Nothing about **class initialization** is passed here. `:natives` ships its own
`META-INF/native-image/io.github.digitalsmile/goldberry-natives/native-image.properties`
naming the two classes that have an opinion, and they are opposite opinions:

| Class | When | Why |
|---|---|---|
| `NativeLibrary` | run time | It `dlopen`s in its initializer, which must not happen in the builder |
| `Downcalls` | **build time** | It holds the shared `Linker`, and every holder's initializer calls `Downcalls.link` |
| the `…calls` **packages** | **build time** | A downcall handle is only a call if it is a compile-time constant, and only a build-time initializer makes it one ([ADR-0161](adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md)) |

They are **packages** and they have to be. A holder is a nested class, and naming
its enclosing class does not reach it — measured at 4538 ns/call against 8, and
silently, because the image builds and runs. Naming a hundred and thirty-four
nested classes in a flag is not a list anyone can maintain, and naming the
*binding* packages instead would build-time initialize `Sdl` and `Blend2D`, whose
holder idiom `dlopen`s the library in the builder. So the holders live in
packages that contain nothing else
([ADR-0173](adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)).

All of them travel in the jar, so an application building its own image gets them
without knowing they exist — the same argument ADR-0160 makes for resources.

## The one flag the frame rate depends on

GraalVM's FFM downcalls are **not optimized** — [oracle/graal#8113](https://github.com/oracle/graal/issues/8113)
lists it as open work, and it costs a factor of 450 on the call itself. Goldberry
takes the workaround: a holder's `FD_<symbol>` handle is *unbound* (it takes the
address to call as an argument), so it can be linked while the image is being
built, which is what turns it into a constant the compiler can lower into a
direct call.

Sixty frames of the showcase, headless, on this machine:

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
4540 ns/call ([ADR-0173](adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)).

**Nothing fails when it is missing.** The image builds, runs, paints correctly
and is forty times slower, which is why the number is written down here. (It is
silent only because the generated metadata registers the descriptors anyway; a
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

~~**No CI job.**~~ **Built** ([ADR-0337](adr/0337-the-native-showcase-is-built-on-every-platform.md)):
every `showcase.yml` leg installs GraalVM Community, builds the image, runs it for
three frames and uploads it; on a `v*` tag the three go on the tag's GitHub
Release ([ADR-0340](adr/0340-the-showcase-is-a-release-artifact-not-a-package.md)),
and the workflow runs only on a tag or by hand. Linux builds from the checked-in
trace; macOS and Windows trace first, and those traces are not reviewed. Neither
task is wired into `build`, still, because a local build has no GraalVM to count on.

**No image of the toolkit on its own.** `:core` and `:widgets` are libraries; an
image is a property of an application, and `:example` is the application here.
