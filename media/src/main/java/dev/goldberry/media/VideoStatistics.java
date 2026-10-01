package dev.goldberry.media;

/// What has happened to a source's pictures since it was opened
/// ([MediaPlayer#videoStatistics()]): what a player's smoothness is measured by
/// (`docs/gpu-plan.md`, phase 6).
///
/// A picture is decoded, then either dropped before it is prepared ([#late]),
/// or queued; a queued picture is either handed out to a view ([#shown]) or
/// passed over for a newer one when the clock has moved past both before a view
/// asked ([#passed]). The pictures an accurate seek decodes on its way to the
/// target, and the ones a seek flushes, are decoded and none of the others.
///
/// @param decoded pictures the decoder produced
/// @param late    pictures dropped before they were prepared, late by a whole
///                picture with more waiting
/// @param passed  pictures prepared and queued whose time came and went before
///                any view was handed them
/// @param shown   pictures handed out to a view, each counted once
public record VideoStatistics(long decoded, long late, long passed, long shown) {

    /// Nothing open.
    public static final VideoStatistics NONE = new VideoStatistics(0, 0, 0, 0);

    /// Checks no count is negative.
    public VideoStatistics {
        if (decoded < 0 || late < 0 || passed < 0 || shown < 0) {
            throw new IllegalArgumentException(
                    "counts are not negative: " + decoded + ", " + late + ", " + passed + ", " + shown);
        }
    }

    /// Pictures that played and were never seen: [#late] and [#passed].
    public long dropped() {
        return late + passed;
    }
}
