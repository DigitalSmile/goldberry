package dev.goldberry.image.scalable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.Goldberry;
import dev.goldberry.image.Image;
import dev.goldberry.image.ScalableImage;
import dev.goldberry.log.Logs;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;

/// The rasters of scalable images, by document and pixel size: what a frame
/// draws an [Image] with a [ScalableImage] behind it from, so the picture is
/// made for the device pixels it covers rather than stretched from its natural
/// size.
///
/// ## When a raster is drawn
///
/// Where it is cheap, at once: outside a running toolkit (a test, an offscreen
/// render), or when the document's last raster says this one takes under
/// [#SYNC_BUDGET_NANOS]. A document not drawn yet is cheap up to
/// [#UNMEASURED_SYNC_PIXELS] pixels. Otherwise on a virtual thread, and until it
/// arrives the frame draws the document's most recent raster of another size,
/// or its natural one, scaled; on arrival [PictureArrivals] moves and the
/// window paints again.
///
/// ## How much is kept
///
/// Bounded by bytes, least recently drawn first, as the image cache is. A
/// raster that cannot be drawn (larger than the document's implementation
/// draws, or of a document since released) is remembered as the natural one,
/// so it is not asked for on every frame.
public final class ScalableRasters {

    /// 64 MiB of premultiplied pixels.
    public static final long DEFAULT_CAPACITY = 64L * 1024 * 1024;

    /// The most a raster is drawn in while a frame waits for it: 2 ms.
    public static final long SYNC_BUDGET_NANOS = 2_000_000L;

    /// The largest raster of a document not drawn yet that is drawn at once: 256²
    /// pixels.
    public static final long UNMEASURED_SYNC_PIXELS = 256L * 256;

    private static final Logger LOG = Logs.of(ScalableRasters.class);

    private static final ScalableRasters SHARED = new ScalableRasters(DEFAULT_CAPACITY);

    /// One document at one size.
    private record Key(ScalableImage document, PhysicalSize size) {}

    /// What a frame blits: the raster, and the part of it to take.
    ///
    /// @param image  the pixels; the image the frame was given when no other
    ///               raster is better
    /// @param source the region of `image` that stands for what was asked for
    public record Drawn(Image image, PhysicalRect source) {
        public Drawn {
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(source, "source");
        }
    }

    private final long capacity;
    private final Map<Key, Image> rasters = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Key, Boolean> pending = new HashMap<>();
    private final Map<ScalableImage, Double> nanosPerPixel = new WeakHashMap<>();
    private long held;

    ScalableRasters(long capacity) {
        this.capacity = capacity;
    }

    /// The process's rasters.
    public static ScalableRasters shared() {
        return SHARED;
    }

    /// What to blit for `source` of `image`, whose document is `document`, drawn
    /// over `deviceWidth` by `deviceHeight` device pixels.
    ///
    /// The whole image is drawn from a raster of exactly the device size, which
    /// the document places itself in. A part of it, a crop or a slice, is drawn
    /// from a raster of the whole at the scale the part is drawn at, and the
    /// part's rectangle scaled with it.
    public Drawn draw(
            Image image, ScalableImage document, PhysicalRect source, double deviceWidth, double deviceHeight) {
        var whole = source.equals(image.bounds());
        PhysicalSize target;
        if (whole) {
            target = sizeOf(deviceWidth, deviceHeight);
        } else {
            var scale = Math.max(deviceWidth / source.width(), deviceHeight / source.height());
            target = sizeOf(image.width() * scale, image.height() * scale);
        }
        if (target == null || target.equals(image.size())) {
            return new Drawn(image, source);
        }
        var raster = raster(document, target, image);
        if (raster == image) {
            return new Drawn(image, source);
        }
        return new Drawn(raster, whole ? raster.bounds() : scaled(source, image.size(), raster.size()));
    }

    /// Records what drawing `pixels` pixels of `document` took, the estimate the
    /// next raster of it is decided on.
    public void measured(ScalableImage document, long nanos, long pixels) {
        if (pixels > 0 && nanos >= 0) {
            synchronized (this) {
                nanosPerPixel.put(document, (double) nanos / pixels);
            }
        }
    }

    /// How many bytes the rasters hold, for a test.
    synchronized long held() {
        return held;
    }

    /// How many rasters are kept, for a test.
    public synchronized int size() {
        return rasters.size();
    }

    /// Forgets every raster, for a test.
    public synchronized void clear() {
        rasters.clear();
        pending.clear();
        nanosPerPixel.clear();
        held = 0;
    }

    /// The raster of `document` at `target`, or the best there is while it is
    /// drawn elsewhere: `fallback` when there is nothing better.
    private Image raster(ScalableImage document, PhysicalSize target, Image fallback) {
        var key = new Key(document, target);
        boolean now;
        synchronized (this) {
            var kept = rasters.get(key);
            if (kept != null) {
                return kept;
            }
            if (pending.containsKey(key)) {
                return latest(document, fallback);
            }
            now = !Goldberry.isUiThread() || isCheap(document, target);
            if (!now) {
                pending.put(key, Boolean.TRUE);
            }
        }
        if (now) {
            var drawn = render(document, target, fallback);
            keep(key, drawn);
            return drawn;
        }
        // Completes on the UI thread, where a window may be told.
        var _ = Goldberry.async(() -> render(document, target, fallback)).whenComplete((drawn, failure) -> {
            synchronized (this) {
                pending.remove(key);
            }
            keep(key, drawn == null ? fallback : drawn);
            PictureArrivals.arrived();
        });
        return latest(document, fallback);
    }

    private boolean isCheap(ScalableImage document, PhysicalSize target) {
        var perPixel = nanosPerPixel.get(document);
        if (perPixel == null) {
            return target.pixelCount() <= UNMEASURED_SYNC_PIXELS;
        }
        return perPixel * target.pixelCount() <= SYNC_BUDGET_NANOS;
    }

    /// Draws `document` at `target`, or answers `fallback` when it cannot be.
    private Image render(ScalableImage document, PhysicalSize target, Image fallback) {
        var started = System.nanoTime();
        try {
            var drawn = document.render(target);
            measured(document, System.nanoTime() - started, target.pixelCount());
            return drawn;
        } catch (RuntimeException e) {
            LOG.debug("a scalable image was not drawn at {}, so its natural raster is: {}", target, e.toString());
            return fallback;
        }
    }

    private synchronized void keep(Key key, Image raster) {
        var previous = rasters.put(key, raster);
        if (previous != null) {
            held -= bytes(previous);
        }
        held += bytes(raster);
        var iterator = rasters.entrySet().iterator();
        while (held > capacity && iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().equals(key)) {
                continue;
            }
            held -= bytes(entry.getValue());
            iterator.remove();
        }
    }

    /// The raster of `document` drawn most recently, or `fallback`.
    private synchronized Image latest(ScalableImage document, Image fallback) {
        var latest = fallback;
        for (var entry : rasters.entrySet()) {
            if (entry.getKey().document() == document) {
                latest = entry.getValue();
            }
        }
        return latest;
    }

    /// The pixel size `width` by `height` device pixels round to, or null when
    /// either is not a positive, finite number.
    private static @Nullable PhysicalSize sizeOf(double width, double height) {
        if (!(width > 0) || !(height > 0) || !Double.isFinite(width) || !Double.isFinite(height)) {
            return null;
        }
        var w = Math.max(1, Math.round(width));
        var h = Math.max(1, Math.round(height));
        if (w > Integer.MAX_VALUE || h > Integer.MAX_VALUE) {
            return null;
        }
        return new PhysicalSize((int) w, (int) h);
    }

    /// `source`, a region of a raster of `from`, as the same region of one of
    /// `to`: outward to whole pixels, inside it, at least one pixel each way.
    static PhysicalRect scaled(PhysicalRect source, PhysicalSize from, PhysicalSize to) {
        var kx = (double) to.width() / from.width();
        var ky = (double) to.height() / from.height();
        var left = clamp((int) Math.floor(source.x() * kx + 1e-6), 0, to.width() - 1);
        var top = clamp((int) Math.floor(source.y() * ky + 1e-6), 0, to.height() - 1);
        var right = clamp((int) Math.ceil((source.x() + source.width()) * kx - 1e-6), left + 1, to.width());
        var bottom = clamp((int) Math.ceil((source.y() + source.height()) * ky - 1e-6), top + 1, to.height());
        return new PhysicalRect(left, top, right - left, bottom - top);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static long bytes(Image image) {
        return 4L * image.width() * image.height();
    }
}
