package dev.goldberry.media.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import dev.goldberry.media.subtitle.Cue;

/// The cues of the subtitles showing, by when they start: filled by the demux
/// thread from a container's subtitle track as it reads, or all at once from an
/// external file, and asked by a view what shows now.
///
/// Cues are kept once read, rather than dropped behind the clock: a seek back
/// finds them without the demuxer having to read their packets again, and a
/// subtitle track is a few kilobytes an hour. A cue read twice, after a seek, is
/// kept once.
///
/// Thread-safe: the demux thread adds, and the UI thread asks.
final class SubtitleTimeline {

    private final TreeMap<Duration, List<Cue>> byStart = new TreeMap<>();
    /// The longest cue held: how far back a cue that still shows can start.
    private Duration longest = Duration.ZERO;

    /// Adds `cue`, unless it is held already.
    synchronized void add(Cue cue) {
        Objects.requireNonNull(cue, "cue");
        var starting = byStart.computeIfAbsent(cue.start(), _ -> new ArrayList<>(1));
        if (!starting.contains(cue)) {
            starting.add(cue);
            var length = cue.end().minus(cue.start());
            if (length.compareTo(longest) > 0) {
                longest = length;
            }
        }
    }

    /// Holds exactly `cues`, and nothing else.
    synchronized void replace(List<Cue> cues) {
        clear();
        cues.forEach(this::add);
    }

    /// Holds nothing.
    synchronized void clear() {
        byStart.clear();
        longest = Duration.ZERO;
    }

    /// The cues showing at `position`, in the order they start.
    synchronized List<Cue> showing(Duration position) {
        var showing = new ArrayList<Cue>(2);
        for (var starting :
                byStart.subMap(position.minus(longest), true, position, true).values()) {
            for (var cue : starting) {
                if (cue.showsAt(position)) {
                    showing.add(cue);
                }
            }
        }
        return List.copyOf(showing);
    }

    /// How many cues are held.
    synchronized int size() {
        return byStart.values().stream().mapToInt(List::size).sum();
    }
}
