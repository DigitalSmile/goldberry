/// The last resort for a codec nothing else decodes: an `ffmpeg` program the
/// user installed, run once per track and fed through pipes.
///
/// [dev.goldberry.media.ffmpegcli.FfmpegCliProvider] is the one public type.
/// Nothing is linked, built or shipped: the provider runs whichever `ffmpeg`
/// it is given, writes the track's packets to it as a Matroska stream, and
/// reads the decoded pictures and samples back as another. Off unless the
/// application names a binary.
///
/// Exported to every module. Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.ffmpegcli;

import org.jspecify.annotations.NullMarked;
