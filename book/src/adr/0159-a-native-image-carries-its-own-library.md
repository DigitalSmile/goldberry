# 159. A native image carries its own library

Date: 2026-08-20

## Status

Accepted. Finishes what [ADR-0156](0156-the-image-s-metadata-is-traced-not-written.md)
started, and narrows [ADR-0019](0019-the-backend-spis-first-cut.md)'s "the
library is a file the application chooses" for the image case only.

## Context

The first working image shipped as two files: the binary, and
`lib/libgoldberry.so` beside it, with a launcher script setting
`-Dgoldberry.native.library` to point one at the other. Run the binary on its
own and it failed — which is what a person does, because a native image is
supposed to be *a program*.

So: can the native libraries go inside it?

`libgoldberry` is already one shared object with Blend2D, AsmJit, Yoga, HarfBuzz
and SDL3 linked in statically (`BUILD_SHARED_LIBS OFF`), exporting exactly the
185 symbols `exports/goldberry.symbols` lists. There is no "several libraries"
problem — only the question of whether that one is linked into the image or
loaded beside it.

## Decision

**The image embeds the classifier jar's library resource and unpacks it on first
use.**

`:natives` already packages `libgoldberry` at exactly the path
`NativeLibrary.resourcePath()` looks for — that is the released-application path,
and an image is an application. So `nativeImage` puts that jar on the image's
**class path** (not its module path: nothing `requires` it, so module resolution
would drop it), registers the resource, and `NativeLibrary` takes the branch it
already had.

One file. No launcher script, no `lib/` directory, no property to set.

## Alternatives considered

**Statically link the archives into the image.** The obvious answer, tried
first, and **it does not work** — the evidence is worth recording because the
failure is not where one would guess.

Linking is fine: `-H:NativeLinkerOption=<archive>` plus `-Wl,-u,<symbol>` pulls
the code in, verified by the binary growing 2.7 MB for `libblend2d.a` alone. It
also needs `-lstdc++` and `-lm`, which native-image does not pass and Blend2D
being C++ requires.

What fails is **symbol visibility**. Goldberry resolves every native function by
*name at runtime* through `SymbolLookup`, so the symbols have to be in the
executable's dynamic symbol table for `dlsym` to find them. They are not:
`native-image` links with its own `--version-script`, which makes every symbol it
does not list `local`. Three ways round it were tried and all three failed:

- `-Wl,--export-dynamic-symbol=<name>` — the version script still wins;
- a second `--version-script`, anonymous tag — *"anonymous version tag cannot be
  combined with other version tags"*;
- the same with a named tag — same error, because SVM's own tag is the anonymous
  one.

There is no `-H:` option to add to the exported set. So on Linux with GNU ld, a
statically linked archive's symbols cannot be reached by name from Java in a
native image. Revisit if native-image grows a way to extend its export list, or
if the bindings ever stop resolving by name.

**Ship the library beside the binary, as before.** Works, and is what the jlink
image does ([ADR-0048](0048-the-showcase-ships-as-a-runtime-image.md)) — but a jlink
image is a *directory* by nature and a native image is a file. Two files with a
launcher between them gives up most of what the format is for.

## Consequences

**A 9 MB temporary file per run, and a writable temp directory becomes a
requirement.** The library is unpacked because a shared object must be a real
file to be `dlopen`ed — it cannot be mapped out of the binary. Measured at ~5 ms
on this machine, once, at start-up. A deployment with a read-only or `noexec`
`/tmp` cannot run the image, and `-Dgoldberry.native.library` remains the way
out.

**The image is 41 MB where it was 32.** The library is carried rather than
compressed away.

**Two bugs surfaced that were not about images at all.**

*A named module cannot see a class-path resource.* `NativeLibrary` lives in
`io.github.digitalsmile.goldberry.natives`, and `Class.getResourceAsStream` on a
class in a named module searches *that module* and never the class path. So the
classifier jar — the mechanism a released application is supposed to use — could
never have worked for a **module-path** deployment. It went unnoticed because
every module-path run in this repository points at a locally built library with
`-Dgoldberry.native.library`, which takes the other branch. There is now a
`ClassLoader.getSystemResourceAsStream` fallback.

*`deleteOnExit` runs in reverse.* The unpacked library registered itself for
deletion before its parent directory, and the queue is drained in reverse
registration order — so the directory was attempted first, failed because it was
not empty, and every run left one behind, on the JVM as much as in an image. The
two registrations are now the other way round.

**Neither has a test, and that is the honest position.** Both bugs live where
this repository does not look: the first only manifests on the module path, and
tests run on the class path — which is precisely why it survived. The second
needs a process to exit to observe. What caught them was building the artifact
and running it with nothing beside it, and the lasting lesson is that the
artifact is the test.

**`:example:nativeImage` now depends on `:natives:stageHostArtifact`.** An image
built from a source tree needs the local library staged into the artifacts
directory before the classifier jar can package it. The ordering is declared from
`:example` rather than by making `nativeJar` depend on staging, because that
dependency is wrong in CI: the Linux legs build inside the manylinux container
and download the result, and a `cmakeBuild` on the runner would link against a
different glibc ([ADR-0012](0012-native-ci-runners-with-a-pinned-glibc.md)).
