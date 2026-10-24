package dev.goldberry.media.container.matroska;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// The seek index: where each cued time's cluster starts.
///
/// A cue point is a time and, per track, the position of the cluster holding
/// that track's keyframe at that time, relative to the Segment's payload. A
/// seek goes to the last cue at or before its target, for the track that has
/// cues; a file cues its video track, and an audio-only file its audio.
///
/// @param points every cue point, in the file's order
public record Cues(List<Point> points) {

    /// One `CueTrackPositions`: a time, a track and a cluster.
    ///
    /// @param timeTicks       `CueTime`, in the Segment's timestamp scale
    /// @param trackNumber     `CueTrack`
    /// @param clusterPosition `CueClusterPosition`, from the Segment's payload start
    public record Point(long timeTicks, long trackNumber, long clusterPosition) {}

    /// No cues at all.
    public static final Cues NONE = new Cues(List.of());

    public Cues {
        points = List.copyOf(points);
    }

    /// Whether there is anything to seek by.
    public boolean isEmpty() {
        return points.isEmpty();
    }

    /// The cluster to start reading at for `timeTicks`: the last cue at or
    /// before it for `trackNumber`, else for any track, else the first cue
    /// there is. Empty only when there are no cues.
    public Optional<Point> at(long timeTicks, long trackNumber) {
        if (points.isEmpty()) {
            return Optional.empty();
        }
        var own = new ArrayList<Point>();
        for (var point : points) {
            if (point.trackNumber() == trackNumber) {
                own.add(point);
            }
        }
        var candidates = own.isEmpty() ? points : own;
        Point best = null;
        for (var point : candidates) {
            if (point.timeTicks() <= timeTicks && (best == null || point.timeTicks() >= best.timeTicks())) {
                best = point;
            }
        }
        if (best == null) {
            best = candidates.getFirst();
        }
        return Optional.of(best);
    }
}
