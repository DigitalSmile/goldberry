# 461. A media engine binds its own libraries

Date: 2026-09-23

## Status

Accepted. Amends `docs/ARCHITECTURE.md` §3.1's rule that raw `MemorySegment`
never escapes `:natives`, for one module and for stated reasons. Follows from
[ADR-0460](0460-media-is-ffmpeg-driven-from-java-not-libvlc.md).

## Context

Every native library Goldberry uses is bound in `:natives`, and every one but
`libgoldberry-webview` is linked statically into `libgoldberry`. `:natives`
exports its wrapper packages to named readers, and ADR-0280 and ADR-0290 sealed
them so that no type of `:natives` appears in a signature an application can
read. md4c (ADR-0294) and libwebp (ADR-0329) joined that library, because each is
a few permissive C files.

FFmpeg cannot join it:

- **The LGPL wants the library replaceable.** Linking FFmpeg statically into
  `libgoldberry` would make the whole toolkit's binary the thing a user has to
  be able to relink. FFmpeg stays five shared libraries, loaded from a directory
  that `-Dgoldberry.media.libdir` can replace.
- **It is optional.** An application that plays no media must not load FFmpeg,
  and must not need a toolchain that builds it.
- **Its surface belongs to one caller.** About sixty functions and nine structs,
  and every call site is the Engine. `:natives` would be a pass-through that
  exports them to `:media` alone, and struct views and upcalls whose whole
  meaning is the Engine's would live away from it.

## Decision

**`:media` binds FFmpeg itself**, in `io.github.digitalsmile.goldberry.media.ffi`.
That makes it the second module that holds a `MemorySegment`. The package is not
exported.

The rules `:natives` keeps are kept here, in the same shapes:

- **Holders.** Each function is a final class with a `static final FD_<symbol>`
  handle, grouped into `…Calls` records in a package of its own
  (`…media.ffi.calls`), so that a native image can initialise them at build time
  (ADR-0161, ADR-0173).
- **A layout probe.** `ffmpeg_layout.c` prints sizes, offsets, library majors
  and constants for every struct and field the Engine touches. The superbuild
  packages the output beside the libraries. `FfmpegStructs` is checked against
  it by a unit test (against a committed copy) and again at start-up (against
  the packaged one), **before any struct is read**. Constants that differ
  between platforms, `AVERROR(EAGAIN)` for one, are read from it rather than
  written down.
- **One stub per owner.** `read_packet` and `seek` are bound to one stream's
  callbacks. They do not dispatch on `opaque` (ADR-0017).
- **Nothing leaks out.** The exported packages (`…media`, `…media.codec`,
  `…media.io`) name no FFmpeg type. A codec is a `CodecId`, mapped by FFmpeg's
  codec *name*, not its enum number, which changes between majors.

The one deliberate exposure is the Decoder SPI (phase 2), whose `Packet` and
`Frame` wrap `MemorySegment`s, so that a provider can decode without copying.
That is `goldberry-media.md` §5's design, and it is a `java.lang.foreign` type
in an SPI signature. It is not an FFmpeg type, and not a struct layout.

## Alternatives considered

- **Bind FFmpeg in `:natives` and export it to `:media`.** This keeps the letter
  of §3.1 and loses the point of it. The wrappers would have one reader, and every
  Engine change would span two modules. `:natives` would also need FFmpeg's
  headers to build, which ties the toolkit's build to the media toolchain.
- **A C shim library over FFmpeg.** It would narrow the Java surface to a handful
  of calls. But it would be a third native artifact per platform, C code this
  project owns for no gain FFM does not already give, and it would still need
  the layout check for the frames it hands back.

## Consequences

- `ARCHITECTURE.md` §3.1 names the exception, and `module-info.java` for `:media`
  says it.
- Consumers pass `--enable-native-access=io.github.digitalsmile.goldberry.media`
  as well as the one for `:natives`.
- The GraalVM reachability metadata for this module (three upcall shapes, the
  downcall descriptors that `FfmpegDowncalls` records) is this module's to
  generate. It is an open item in `docs/media-plan.md`.
