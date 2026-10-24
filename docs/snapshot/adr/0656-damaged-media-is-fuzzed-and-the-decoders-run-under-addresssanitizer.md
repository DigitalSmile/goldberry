# ADR-0656: Damaged media is fuzzed, and the decoders run under AddressSanitizer

- **Status:** Accepted
- **Date:** 2026-10-24
- **Relates to:** ADR-0651 (FFmpeg leaves; the reference codecs bound directly), ADR-0560 (no clock bounds in tests), ADR-0517 (a lane without the library is green)

## Context

Since ADR-0651 the bytes of every file a player opens go through demuxers
written here in Java and then, as packets, straight into seven C libraries
bound by hand: dav1d, libvpx, libopus, libvorbis, libFLAC, minimp3 and libyuv.
FFmpeg used to stand between the two, with its own parsers' checks and
decades of fuzzing. Now nothing does, and a media file is untrusted input:
a download, a stream, an attachment.

`ffmpeg-out.md` §8 named the risk ("allocator misuse = silent memory
corruption") and its mitigation ("allocator fuzz test + ASan lane in CI for
natives"), and neither was built. dav1d's picture allocator is left at its
default, so the risk that remains is not an allocator of ours; it is
damaged bitstreams reaching the native decoders, and our own FFM code
around them: struct offsets, plane sizes, FLAC's upcalls, buffers sized
from a header. The suite tested every decoder and container on good files
only. Memory corruption in native code is silent in an ordinary run: a read
past the end of a buffer returns whatever is there.

## Decision

**Every container and every built-in decoder is fed damaged input in
`:media:test`, deterministically; and a sanitizer lane runs the same tests,
with many more rounds, against a `libgoldberry-media` built with
AddressSanitizer.**

- **Robustness tests** (`dev.goldberry.media.robustness`, tagged
  `robustness`):
  - `ContainerFuzzTest`: 23 fixtures (every container, the cover-art
    variants, two formats this build names and refuses) under nine kinds of
    damage — header and body bit flips, overwritten runs, 32-bit size fields
    set to the values parsers choke on, inserted and removed bytes,
    truncation, noise behind the magic, and mixtures — read in short chunks,
    from a source of unknown size, or one that cannot seek; plus each file cut
    at every length of its first 512 bytes and at steps after. Open, request
    every track, read some packets, seek, read to the end: frames, or a
    `MediaException`, nothing else; a packet's memory readable, its stream
    selected; and the reading ends, no more than sixteen packets per byte of the file, with
    a `@Timeout` for a read that never returns.
  - `DecoderFuzzTest`: each decoder on its fixtures (dav1d from Matroska and
    MP4; libvpx in VP8, 8- and 10-bit VP9, 4:4:4 through the subsampling
    path, VP9 with alpha; Opus from Ogg and WebM; Vorbis; FLAC with each of its
    three header records; MP3; PCM), demuxed by the Java containers and then
    damaged: bit flips, overwrites, size fields, truncation, empty packets,
    noise, reordering, repetition, dropped keyframes, a damaged codec
    configuration at open, nonsense timing with flushes between packets, alpha
    attached where there is none, and mixtures; plus one keyframe and one later
    packet cut at every length. A decoder may decode, decode nothing, or throw
    a `MediaException`, after which it is closed, as the Engine closes it, and
    a fresh one takes the packets after; never another exception, never `Ended`
    before the end, never a frame whose planes cannot be read to the last row or
    whose shape no codec makes, never a packet refused or frames handed out
    forever, and `close()` twice always.
  - `EndToEndFuzzTest`: damaged files demuxed and every track a built-in
    decoder supports decoded from what the container read, so the decoders also
    see what a container makes of a broken header.
  - Seeded by fixture, kind and round, with no clock read; a failure names its
    seed. Eight rounds in `test` (about ten seconds for all three);
    `goldberry.media.fuzz.rounds` raises them, `goldberry.media.fuzz.seed` moves
    the start. `testWithoutGpu` leaves them out: they touch no GPU, and a second
    run finds nothing the first did not.
- **The sanitizer build.** `GOLDBERRY_MEDIA_SANITIZE=address` (or
  `address,undefined`) in the media superbuild compiles every upstream (their
  CMake builds, dav1d's meson, libvpx's configure), libyuv, minimp3 and our
  own file with `-fsanitize=… -fno-omit-frame-pointer -g`, links the library
  against GCC's shared runtime, keeps its symbols and drops the size gate. The
  release build's flags, archives and output are unchanged, and so is the
  ABI: the export check passes as it is, since instrumentation adds only
  undefined references and local symbols. Gradle builds it as
  `:media:nativesBuildAsan` into `media-natives/<target>-asan/` with a deps
  directory of its own, so no instrumented archive can reach the release link.
- **The sanitizer lane.** `:media:sanitizerTest` runs the robustness tests,
  the decoders' tests and the `ffm` tests against that library, 48 rounds,
  with libasan preloaded into the test JVM and
  `detect_leaks=0:handle_segv=0:handle_sigbus=0:handle_sigfpe=0:handle_sigill=0:allow_user_segv_handler=1:detect_stack_use_after_return=0:allocator_may_return_null=1:halt_on_error=1`
  — HotSpot keeps the signals it uses for null checks, safepoints and
  division, leak detection stays off for a process whose JVM never frees its
  own memory, and a codec's allocation failure path is exercised rather than
  aborting. A finding ends the JVM; its report goes under `build/sanitizer/`,
  which `sanitizerReport` prints and fails on. `SanitizerLaneTest` checks
  that libasan and the sanitized library are what the process mapped. It is
  `media.yml`'s `sanitizer` job on linux-x64, not part of `check`.
- **UBSan beside it, on request.** `-Pgoldberry.media.sanitize=address,undefined`
  builds into `<target>-asan-ubsan` and runs the same lane with UBSan's
  reports fatal (`halt_on_error=1`), except those in
  `media/src/test/sanitizer/ubsan.supp`. The checks stay recoverable at compile
  time, since only a recoverable report can be suppressed at run time. GCC's
  UBSan, beside ASan, ignores `log_path` and writes to the JVM's standard
  error, so its report is in the task's output rather than under
  `build/sanitizer/`. It is not the CI job's default: two upstreams have
  undefined behaviour of their own (below), and the suppressions are a list
  to keep, which the address lane is not.

## What it found, and what changed

- **EBML sizes over 31 bits** were accumulated in an `int`: a size of 2³¹ or
  more came out negative, and one over 32 bits was cut to its low 32, so a
  damaged size skipped backwards (an `IllegalArgumentException`) or misplaced
  the next element. The size is a `long` now.
- **Sizes read from a file were trusted with allocations**: a damaged
  SeekHead entry, CodecPrivate or FLAC picture asked `ByteReader.bytes` for two
  gigabytes, an `OutOfMemoryError` that took the test JVM down, and a
  negative one threw `NegativeArraySizeException`. `ByteReader` now refuses a
  count the stream cannot hold when its size is known, and on a stream of
  unknown size gathers the bytes as they arrive, so a lie fails at the end of
  the stream, not on the heap. Negative counts, skips and seek positions are
  `InvalidData` rather than `IllegalArgumentException`: only a damaged size or
  offset makes them.
- **An EBML element of unknown size** where a sized one must be threw
  `IllegalStateException` from `Element.dataEnd()` at any of twenty call sites;
  it is `InvalidData`.
- **A Matroska PixelWidth, PixelHeight, Channels or BitDepth wider than an
  `int`** was cast to a negative one, which `TrackParams` refused with an
  `IllegalArgumentException` while the file was opening; it reads as not
  stated (0).
- **An MP4 `stsz` with one size for every sample** had its count unchecked:
  two billion samples asked for the tables of two billion. The count is
  bounded by the file's size over the sample's.
- **libFLAC's 8- and 12-bit samples** were handed over in 16 bits unshifted,
  256 and 16 times too quiet; they are shifted to the top of the word as wider
  samples already were, as FFmpeg's decoder does. Found reading the write
  callback for the harness, not by a mutation.

Each has a focused regression test beside the code (`ByteReaderTest`,
`EbmlReaderTest`, `MatroskaContainerTest`, `Mp4ContainerTest`,
`FlacDecoderTest`). The native decoders themselves gave no finding: none in
`test`'s rounds, none in 200 rounds of every decoder and container kind with
the release library, and none under AddressSanitizer at the lane's 48 rounds
or at 200. A deliberate read past a 16-byte buffer through minimp3 was
reported by the lane, with its source line, which is what says the lane can
see one.

UBSan found two things, both upstream and both suppressed by name:
libvorbis 1.3.7 shifts a signed `int` past its range building the decode
table of every codebook (`sharedbook.c`, on a valid stream), and libFLAC 1.5.0's
32-bit LPC restore overflows on a damaged frame (`lpc.c`), which then fails its
CRC. Both compile to the two's-complement arithmetic the code means. Nothing
else under UBSan at 48 rounds; no alignment report either, so the alignment
check stays on.

## Alternatives

- **libFuzzer or Jazzer, coverage-guided.** Finds deeper bugs than seeded
  mutation, but it runs until stopped, which `check` cannot, and its corpus
  and dictionaries are a project of their own. The codecs are fuzzed upstream
  by OSS-Fuzz already; what is ours is the Java around them and the way we call
  them, which mutations of real fixtures reach. A Jazzer target over
  `DemuxHarness` remains open if the seeded rounds stop finding things the
  real world does.
- **Sanitizing only our own file.** Cheaper to build, and blind: everything
  that reads a packet is an upstream's. AddressSanitizer reports a bad access
  in the code that makes it, so the upstreams have to be instrumented.
- **The sanitizer lane in every test task.** A sanitized library is several
  times slower and needs the runtime preloaded into the JVM, which only Linux
  and GCC here support; the suite on every target keeps the release library.
- **Clang and its shared runtime.** Equivalent, but the superbuild, the CI
  runners and the manylinux images are GCC's, and one toolchain is one set of
  flags.

## Consequences

- `:media:test` runs about 650 more cases, in about ten seconds; the lane
  takes about two and a half minutes to build and as long to run.
- `media.yml` gains a third job. `publish.yml` needs every Media job, so a
  sanitizer finding holds a snapshot or a release back, as a failing decoder
  test already does.
- A container that reads a size from a file must not allocate from it before
  `ByteReader` has checked it; the robustness tests catch the ones that do.
- Not done here, and noted: dav1d's `frame_size_limit` is left at its
  default, no limit, and libvpx is built without `--size-limit`, so a damaged
  sequence header can ask for a picture of up to 65536 × 65536 and its memory.
  No fixture mutation reached it; bounding it is a decision about the largest
  video a player accepts, and is left to make on purpose.
