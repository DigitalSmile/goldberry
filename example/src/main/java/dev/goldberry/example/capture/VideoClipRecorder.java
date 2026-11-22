package dev.goldberry.example.capture;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PictureSource;

/// Keeps the pictures a source shows, copied, as a [VideoClip], up to a
/// length and an amount of memory.
///
/// Nothing is encoded, so a clip is as large as its pictures: a minute of a
/// call's 1280×720 is about 2.4 GB. Past either bound the recorder takes no
/// more and [#full()] says so.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
public final class VideoClipRecorder implements AutoCloseable {

    /// The longest clip the Capture screen keeps.
    public static final Duration LONGEST = Duration.ofSeconds(30);

    /// The most memory a clip the Capture screen keeps may take: half a minute
    /// of 640×480, or eight seconds of 1280×720.
    public static final long BUDGET = 256L << 20;

    private final PictureSource source;
    private final long longestNanos;
    private final long budget;
    private final List<VideoClip.Shot> shots = new ArrayList<>();
    private final AutoCloseable subscription;
    private @Nullable Picture last;
    private long firstPts;
    private long bytes;
    private boolean full;
    private boolean closed;

    /// Starts keeping what `source` shows, at most `longest` of it in at most
    /// `budget` bytes.
    public VideoClipRecorder(PictureSource source, Duration longest, long budget) {
        this.source = Objects.requireNonNull(source, "source");
        if (longest.isNegative() || longest.isZero() || budget <= 0) {
            throw new IllegalArgumentException("a clip of " + longest + " in " + budget + " bytes");
        }
        this.longestNanos = longest.toNanos();
        this.budget = budget;
        this.subscription = source.onPicture(this::take);
        take();
    }

    /// Takes the picture showing now, if it is a new one.
    private synchronized void take() {
        if (closed || full) {
            return;
        }
        var showing = source.shownPicture().orElse(null);
        if (showing == null || showing == last) {
            return;
        }
        last = showing;
        if (shots.isEmpty()) {
            firstPts = showing.ptsNanos();
        }
        var at = Math.max(
                showing.ptsNanos() - firstPts,
                shots.isEmpty() ? 0 : shots.getLast().atNanos());
        var size = PictureCopies.bytes(showing);
        if (at > longestNanos || bytes + size > budget) {
            full = true;
            return;
        }
        PictureCopies.copy(showing, at).ifPresent(copy -> {
            shots.add(new VideoClip.Shot(at, copy));
            bytes += size;
        });
    }

    /// Whether it has kept as much as it may.
    public synchronized boolean full() {
        return full;
    }

    /// How many pictures it has kept.
    public synchronized int pictures() {
        return shots.size();
    }

    /// What it has kept so far.
    public synchronized VideoClip clip() {
        return new VideoClip(shots, bytes);
    }

    /// Stops keeping pictures. What it kept stays readable.
    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        try {
            subscription.close();
        } catch (Exception e) {
            throw new IllegalStateException("the source refused to let go of a listener", e);
        }
    }
}
