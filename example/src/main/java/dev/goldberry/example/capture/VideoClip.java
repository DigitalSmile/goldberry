package dev.goldberry.example.capture;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import dev.goldberry.media.picture.Picture;

/// Recorded pictures, each with when it showed, counted from the first.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
///
/// @param pictures the pictures in the order they showed, the first at zero
/// @param bytes    the memory they hold
public record VideoClip(List<Shot> pictures, long bytes) {

    /// A clip with nothing in it.
    public static final VideoClip EMPTY = new VideoClip(List.of(), 0);

    /// One picture, and when it showed.
    ///
    /// @param atNanos nanoseconds after the clip's first picture
    /// @param picture the picture, which the clip owns
    public record Shot(long atNanos, Picture picture) {

        public Shot {
            Objects.requireNonNull(picture, "picture");
            if (atNanos < 0) {
                throw new IllegalArgumentException("a picture at " + atNanos + " ns, before the clip starts");
            }
        }
    }

    public VideoClip {
        pictures = List.copyOf(pictures);
        for (var at = 1; at < pictures.size(); at++) {
            if (pictures.get(at).atNanos() < pictures.get(at - 1).atNanos()) {
                throw new IllegalArgumentException("picture " + at + " shows before the one ahead of it");
            }
        }
    }

    /// Whether it holds no picture.
    public boolean isEmpty() {
        return pictures.isEmpty();
    }

    /// How long it plays: to its last picture, and that picture's own share,
    /// which is the average gap between them.
    public Duration duration() {
        if (pictures.isEmpty()) {
            return Duration.ZERO;
        }
        var last = pictures.getLast().atNanos();
        var share = pictures.size() > 1 ? last / (pictures.size() - 1) : 0;
        return Duration.ofNanos(last + share);
    }

    /// The index of the picture showing `nanos` into the clip: the last one at
    /// or before it, the first before the clip starts.
    ///
    /// @throws IllegalStateException when the clip is empty
    public int indexAt(long nanos) {
        if (pictures.isEmpty()) {
            throw new IllegalStateException("an empty clip shows nothing");
        }
        var low = 0;
        var high = pictures.size() - 1;
        while (low < high) {
            var middle = (low + high + 1) >>> 1;
            if (pictures.get(middle).atNanos() <= nanos) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }
}
