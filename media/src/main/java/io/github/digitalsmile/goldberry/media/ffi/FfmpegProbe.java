package io.github.digitalsmile.goldberry.media.ffi;

import io.github.digitalsmile.goldberry.media.MediaError;
import io.github.digitalsmile.goldberry.media.MediaException;
import io.github.digitalsmile.goldberry.media.MediaInfo;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.Source;

/// Says what a [MediaIO] holds, decoding nothing: a [Demuxer] opened, asked, and
/// closed.
///
/// Phase 1's exit criterion ("Java probes a file through MediaIO and lists
/// Tracks"), and the first half of what the Engine's demux thread does when it
/// opens a source (`docs/goldberry-media.md` §3).
public final class FfmpegProbe {

    private FfmpegProbe() {}

    /// Probes `io`, which `source` was opened into. Does not close `io`.
    ///
    /// @throws MediaException [MediaError.InvalidData] for bytes no demuxer reads,
    ///                        [MediaError.Io] when a read fails, [MediaError.Aborted]
    ///                        when `io` was closed during the probe
    public static MediaInfo probe(Ffmpeg ffmpeg, Source source, MediaIO io) {
        try (var demuxer = Demuxer.open(ffmpeg, source, io)) {
            return demuxer.info();
        }
    }
}
