# 493. The platform decoders are part of media

Date: 2026-09-30

## Status

Accepted. Supersedes the module boundary of
[ADR-0472](0472-the-platform-decoders-bind-the-system-frameworks.md), which put
the system decoders in a module of their own, `goldberry-media-platform`. What
ADR-0472 and [ADR-0489](0489-linux-and-windows-platform-decoders-are-gstreamer-and-media-foundation.md)
decided about the decoders themselves still stands.

## Context

`:media-platform` held the `DecoderProvider`s that hand H.264, HEVC, AAC, AC-3
and E-AC-3 to the operating system: VideoToolbox and AudioToolbox on macOS,
GStreamer on Linux, Media Foundation on Windows. ADR-0472 made it optional, like
every content module, so an application that did not want the system's decoders
would not get them.

It was optional in name only.

- **It ships no native code.** It binds libraries the system already has with
  FFM. An application that does not decode H.264 loses nothing by having it: a
  provider is asked only about a track in one of its codecs, and binds its
  system's libraries on that first question.
- **Each provider supports nothing off its own system.** On Linux the
  VideoToolbox provider answers "no" to everything and opens nothing. Being on
  the path is inert until a file in a patent-pool codec is opened, and then it
  is the difference between a picture and `UNSUPPORTED_CODEC`.
- **Nothing used `:media` without it.** The showcase depended on both. CI ran
  both in the same job, and `:media-platform`'s tests demuxed with `:media`'s
  FFmpeg through `:media`'s test classes.
- **It cost a module's worth of wiring.** It had its own build script, test
  task and coverage floor. It had a second `foreignMetadata` generator with a
  second copy of the metadata grammar, kept apart only because the first sat in
  an unexported package of another module (ADR-0280). It had its own
  `--enable-native-access` flag, its own CI step and its own paragraph in
  `NOTICE`. And it was one more thing for [ADR-0495](0495-media-is-published-and-snapshots-publish-again.md)
  to publish.

## Decision

**The system decoders are part of `:media`, in the packages they already had.**

- `dev.goldberry.media.platform` and its `bitstream`,
  `macos`, `linux` and `windows` packages move into `:media` unchanged. The
  descriptor exports `…media.platform` (for [PlatformDecoders]) and nothing
  under it. The `provides` lines for the six `DecoderProvider`s and CoreAudio's
  `OutputLatency` move with them.
- **One metadata file.** `…media.nativeimage` is new and unexported.
  `MetadataGrammar` is the tracing agent's grammar, written once, with the
  sealed switch over `ValueLayout` the platform copy had. `FfmpegDescriptors` (in
  `…media.ffi`) and `PlatformDescriptors` give each family's shapes.
  `MediaForeignMetadata` writes both into the module's single
  `reachability-metadata.json`. `:natives`' `ForeignMetadata` keeps its own
  copy, for ADR-0280's reason, which still holds across modules.
- **The coverage floor counts what runs.** `:media`'s floor stays at 88% of
  lines and 77% of branches. Another system's decoder package is never counted.
  This system's is counted only under `-Pgoldberry.platform.required=true`,
  which is when the job has its decoders installed. That is the rule
  `:media-platform`'s own floor kept.
- **One CI step.** `media.yml` runs `:media:check` with both `required` flags.
- The fixture script is `media/src/test/fixtures/make-platform-fixtures.sh`,
  beside the one that makes FFmpeg's fixtures.

`OutputLatencyTest` asserted that no latency provider was on `:media`'s path.
CoreAudio's now always is, so the test asserts what holds on every system but
macOS: the installed provider answers nothing.

## Consequences

- An application with `goldberry-media` plays the patent-pool codecs wherever
  the operating system can, with no second dependency and no second
  `--enable-native-access` flag. One that wants FFmpeg only lists its own
  providers with `MediaPlayer.builder().decoderProviders(…)`, which is how it
  always opted out of `ServiceLoader`'s list.
- `goldberry-media-platform` was never published, so no coordinates are
  withdrawn.
- The licence position does not change. The jar carries no copy of any system
  decoder, and `NOTICE` and `THIRD-PARTY-NOTICES.md` now say so for all three
  systems. Before, they named only macOS's frameworks.

## Alternatives considered

- **Keep the module and publish it too.** That means two artifacts, two
  natives-access flags and two metadata generators for code that is inert
  where it is not wanted. Being optional protected nothing.
- **Merge it and rename the packages to `…media.decode.*`.** The names are
  already role-shaped (ADR-0172): `platform` is "the system's decoders", and
  the three below it are split where each system's foreign memory stops.
  Renaming would churn seventy files and every ADR that cites them, and buy
  nothing.

[PlatformDecoders]: ../../../media/src/main/java/dev/goldberry/media/platform/PlatformDecoders.java
