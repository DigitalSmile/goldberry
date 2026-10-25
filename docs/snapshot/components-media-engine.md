# The media engine without FFmpeg: the guide's pieces

Parked until the release, as `docs/snapshot/README.md` says. Eight pieces,
each for one place in the guide; the record is ADR-0651. The prose follows
the guide's rule: it says what is, and cites no record.

## `book/src/components/media.md`, `## The module`, the first paragraph

Replace the paragraph that begins "The widgets are in `goldberry-media`" with:

> The widgets are in `goldberry-media`. Its engine is Java down to the codecs:
> the containers (Matroska and WebM, MP4, Ogg, MP3) are read in Java, every
> byte through a `MediaIO`, and the pictures and sound are decoded by the
> reference decoders — dav1d, libvpx, Opus, Vorbis, FLAC and minimp3 — linked
> into one native library per target, `libgoldberry-media`, and driven through
> the Foreign Function and Memory API with no JNI. The natives are published
> as classifier jars of the same artifact, one per target: `natives-linux-x64`,
> `natives-linux-aarch64`, `natives-windows-x64` and `natives-macos-aarch64`.
> An application names the ones it ships. Everything in them is BSD or CC0.

The paragraph "The published natives decode royalty-free codecs only" stays
as it is; it is still true. Under `### Bringing a codec`, replace the last
paragraph ("Hardware decoding is a rung of the built-in decoder…") with:

> The built-in decoders are software. Hardware decoding is what the system's
> own decoders do, for the codecs the system decodes, on by default.
> `setHardwareDecoding(HardwareDecoding.OFF)` keeps them in software too,
> applied from the next source opened: what an application's "disable
> hardware acceleration" switch sets. A provider an application brings is
> handed the same setting with each track it is offered.

## `book/src/overview/concept.md`, the natives table

Replace the row

> `| FFmpeg | audio and video decoding | `goldberry-media`, as separate shared libraries |`

with

> `| dav1d, libvpx, Opus, Vorbis, FLAC, minimp3, libyuv | audio and video decoding and conversion | `goldberry-media`, in one `libgoldberry-media` |`

## `book/src/overview/architecture.md`, under "The native boundary"

Replace

> `goldberry-media` is the one exception. FFmpeg is LGPL and must stay a set of
> replaceable shared libraries, so the media module binds it itself, under the
> same rules.

with

> `goldberry-media` is the one exception. Its decoders live in a library of
> their own, `libgoldberry-media`, so that an application which plays no media
> carries none of them, and the media module binds it itself, under the same
> rules.

and in the module table the row `| `:media` | `goldberry-media` and
`ffmpeg-<target>` classifiers | …` reads `natives-<target>`.

## `book/src/overview/limitations.md`, the Windows codecs row

Superseded: `components-media-foundation.md` replaces the whole row, this
sentence included.

The sentence "VP8, VP9, AV1, Opus, Vorbis, FLAC and MP3 play everywhere
through FFmpeg" becomes "VP8, VP9, AV1, Opus, Vorbis, FLAC and MP3 play
everywhere through the built-in decoders".

## `book/src/getting-started/installing.md`, the optional modules

The `goldberry-media` row's "plus `ffmpeg-<target>` classifier jars for each
platform" becomes "plus `natives-<target>` classifier jars for each
platform", and the snippet's last line becomes

```groovy
runtimeOnly 'dev.goldberry:goldberry-media::natives-linux-x64'
```

## `book/src/guide/logging.md`, the system properties table

Delete the `goldberry.media.ffmpegLog` row; there is no engine log to let
through. The `goldberry.media.libdir` row stays: "loads the media engine's
library from there, with `goldberry-media` on the path".

## `book/src/contributing/releasing.md`, "Where things go"

The row `| `goldberry-media` classifiers `ffmpeg-<target>`, one per target the
Media workflow built | …` reads `natives-<target>`. Delete the
`ffmpeg-sources` row and the paragraph "**The media engine's binaries do not
go without their source.**" There is no corresponding-source obligation left:
every upstream in the library is BSD or CC0, and what each asks for travels
in `licenses/` and in the jar's `media-NOTICE.txt`. The sentence "A release
needs the media engine for all four targets and refuses without it" stays.

## `book/src/contributing/repository.md`, the module table

The `:media` row's artifacts cell reads "`goldberry-media`, plus
`natives-<target>` classifiers" and its purpose cell "Audio and video: the
containers read in Java, the reference decoders in one native library, and the
operating system's own decoders behind the same SPI".

## `book/src/performance/measuring.md`, the benchmark table

Delete the row `| `ReadPacketBenchmark` | `:media` | One `read_packet` through
the upstream stub against a direct call |`: the benchmark measured FFmpeg's
read callback, and no native code reads a byte now. `PlaneCopyBenchmark` stays,
and a row joins it: `| `DemuxBenchmark` | `:media` | Every packet of a
one-second clip read out of memory by the Java containers, as packets a second |`.
