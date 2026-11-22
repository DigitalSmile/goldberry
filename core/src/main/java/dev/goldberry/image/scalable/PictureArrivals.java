package dev.goldberry.image.scalable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import dev.goldberry.bind.Subscription;

/// How many pictures have arrived since the process started, and who is told
/// when one more does: a picture a stylesheet named, decoded, or a raster of a
/// scalable image, drawn for a size.
///
/// A retained render tree remembers the number it last painted a box at, so a
/// box that draws a picture is painted again when the number moves although the
/// box itself has not. A window listens, and asks for a frame.
public final class PictureArrivals {

    private static final AtomicLong GENERATION = new AtomicLong();

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private PictureArrivals() {}

    /// The number of arrivals so far.
    public static long generation() {
        return GENERATION.get();
    }

    /// One more picture has arrived: the number moves and every listener runs,
    /// on the calling thread.
    public static void arrived() {
        GENERATION.incrementAndGet();
        for (var listener : LISTENERS) {
            listener.run();
        }
    }

    /// Runs `listener` on every arrival, until the answer is closed.
    public static Subscription onArrival(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        LISTENERS.add(listener);
        return () -> {
            var _ = LISTENERS.remove(listener);
        };
    }
}
