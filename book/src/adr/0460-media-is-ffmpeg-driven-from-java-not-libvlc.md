# 460. Media is FFmpeg driven from Java, not libVLC

Date: 2026-09-23

## Status

Accepted. Supersedes the engine choice in `docs/content-widgets.md` §8, which
named libVLC and gated the module on a media document. That document is
`docs/goldberry-media.md`. This record is its §11, made a decision. The work is
tracked in `docs/media-plan.md`.

## Context

`content-widgets.md` §8 chose libVLC 3.x over libmpv. libVLC is LGPL by default,
its C API has been stable for a decade, and vlcj has shipped VLC video inside Java
applications for fifteen years through the same `vmem` callback that would hand
frames to a `BLImage`. It never got further than that paragraph. The module was
gated on a document about codecs and patents, and writing that document
reopened the engine question.

Measured and read against libVLC:

- **Size.** A pruned libVLC with only the plugins a player needs is 32–56 MB per
  platform. Its avcodec plugin alone is about 20 MB and cannot be split.
- **Build.** VLC 3.x is autotools plus about a hundred contribs, builds on Windows
  only with mingw, and publishes no Linux binaries. It cannot be part of a superbuild
  the way everything else here is.
- **Duplication.** Its subtitle renderer bundles its own FreeType and HarfBuzz,
  and its TLS bundles gnutls. Goldberry already has a text stack, and the JDK
  already has TLS.
- **Determinism.** VLC owns its clocks and its threads. A golden test of video
  needs the presentation clock to be one a test can advance.
- **GPU.** `vmem` hands over CPU frames. The zero-copy output exists only in
  libVLC 4, which is unreleased.

## Decision

**FFmpeg's libraries, driven from Java.** `avformat`, `avcodec`, `avutil`,
`swresample` and `swscale` (plus dav1d for software AV1), built by a media
superbuild of their own as shared libraries. The Engine's threads, queues, clock
and state machine are Java. FFmpeg demuxes and decodes and does nothing else.

Two scope decisions go with it, and they are what keep FFmpeg small:

- **Royalty-free codecs only.** VP8, VP9, AV1, Opus, Vorbis, FLAC, MP3, PCM, and
  text subtitles. H.264, HEVC, AAC, AC-3 and the MPEG-TS demuxer are not built.
  An MP4 that carries them opens, and it reports `UNSUPPORTED_CODEC` naming the
  codec. A **Decoder SPI** exists from v1, so this is a default and not a
  ceiling. An application that needs a patented codec brings a provider, and the
  licence for it is the application's.
- **No FFmpeg network layer.** `--disable-network` and no protocols. Every byte
  arrives through a Java `MediaIO` over a custom `AVIOContext`. HTTPS, proxies,
  authentication and HTTP/2 come from the JDK, no TLS library ships anywhere,
  and the seek bar shows ranges that are really buffered, because the buffer is
  Java's.

Pinned at FFmpeg `n8.1.3` and dav1d `1.5.4` in `gradle/libs.versions.toml`.

## Alternatives considered

- **libVLC 3.x.** It is the context above. It would have been less code to write
  and more of everything else to ship.
- **libmpv.** Its builds are GPL unless built with `-Dgpl=false`, and that build
  would then be ours to own. It also owns its clock and its output, as VLC does.
- **FFmpeg with its own network layer.** Rejected for TLS. Each platform would
  need a TLS backend (schannel, SecureTransport, or mbedTLS on Linux), and
  mbedTLS takes FFmpeg to LGPL-3. It would also put HTTP retry and caching in C,
  where no test can fake a network.
- **Generated bindings (jextract).** They would be thousands of lines covering
  every struct and function, for an Engine that touches about sixty functions and
  nine structs. ADR-0010 dropped jextract for `libgoldberry` for the same reason.

## Consequences

- The Engine (`goldberry-media.md` §3) and the FFM bindings (§2) are this project's
  to write and maintain. That cost was accepted, not overlooked.
- The natives are at most 6 MB per platform. The superbuild fails above 7 MB. A
  scratch build on macOS without dav1d is 4.2 MB.
- Mainstream H.264/AAC content does not play out of the box. The error says so
  by name, and the SPI is the remedy.
- RTSP is out. HLS and DASH have to be written in Java (post-v1), which in return
  lets segment selection, and so adaptive bitrate, be ours.
- Moving the FFmpeg pin means re-reading the struct table. The layout probe fails
  the build until it has been re-read.
