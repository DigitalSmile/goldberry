package dev.goldberry.media.container.mp4;

import java.util.List;

/// What of a track's `elst` box (ISO/IEC 14496-12 §8.6.6) is honoured: the
/// shift it puts on the track's timestamps.
///
/// Two edits are common and both are a shift. A leading empty edit — a
/// `media_time` of -1 — delays the track by its `segment_duration`, which is
/// how a muxer starts one track after another. A first edit whose
/// `media_time` is positive starts the track that far in, which is how an
/// encoder's priming samples (Opus's pre-skip, AAC's 1024) are kept for the
/// decoder and hidden from the listener: the samples before it are handed
/// over at negative times, as FFmpeg's reader hands them over, and the Engine
/// drops what is before zero. Anything else — a second edit, a rate other
/// than 1, an edit shorter than the track — is ignored: the samples play in
/// their stored order and the file's duration is the movie's.
///
/// @param entries the edits, in order
record EditList(List<Entry> entries) {

    /// One edit.
    ///
    /// @param segmentDuration how long the edit plays, in the movie's timescale
    /// @param mediaTime       where in the track it starts, in the track's
    ///                        timescale, or -1 for an empty edit
    record Entry(long segmentDuration, long mediaTime) {}

    /// No edits.
    static final EditList NONE = new EditList(List.of());

    EditList {
        entries = List.copyOf(entries);
    }

    /// What to add to every timestamp of the track, in the track's timescale:
    /// the leading empty edits' durations, rescaled from the movie's timescale
    /// to the nearest tick, less the first real edit's `media_time`.
    long shift(long trackTimescale, long movieTimescale) {
        var shift = 0L;
        var at = 0;
        while (at < entries.size() && entries.get(at).mediaTime() < 0) {
            var empty = entries.get(at).segmentDuration();
            shift += movieTimescale <= 0 ? 0 : (empty * trackTimescale + movieTimescale / 2) / movieTimescale;
            at++;
        }
        if (at < entries.size()) {
            shift -= entries.get(at).mediaTime();
        }
        return shift;
    }

    /// Whether there is more here than a shift: a later edit this demuxer does
    /// not play.
    boolean hasIgnoredEdits() {
        var at = 0;
        while (at < entries.size() && entries.get(at).mediaTime() < 0) {
            at++;
        }
        return at + 1 < entries.size();
    }
}
