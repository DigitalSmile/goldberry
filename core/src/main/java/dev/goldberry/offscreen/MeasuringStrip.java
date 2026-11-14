package dev.goldberry.offscreen;

import java.util.List;
import java.util.Objects;

import dev.goldberry.frame.FrameSequence;
import dev.goldberry.image.Image;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.PresentTimings;
import dev.goldberry.stats.FrameRing;
import dev.goldberry.stats.FrameStats;

/// A strip that paints only what changed, into one buffer, and says what each
/// frame cost.
///
/// ```java
/// try (var meter = Offscreen.of(2560, 1440)
///         .stylesheets(hud.stylesheets())
///         .measure(new Hud(model))) {
///     for (var i = 0; i < 600; i++) {
///         model.tick();
///         var frame = meter.frame();
///         log(frame.timings().paintNanos(), frame.damagedPixels());
///         meter.advance(16);
///     }
///     var p99 = meter.stats().percentile(FrameStage.PAINT, 99);
/// }
/// ```
///
/// A [Filmstrip] is for pictures: every frame is a full paint into a buffer of
/// its own, because an [Image] it hands back is a view over that buffer. This is
/// for the frame's cost. It paints into **one** buffer for its whole lifetime and,
/// like a window, repaints only the region the render tree reports as damaged,
/// so a frame in which nothing changed rasterizes nothing and a caret blinking
/// costs its own few pixels. What a widget tree costs per frame can be measured
/// without a window on anybody's screen.
///
/// The damaged region is cleared to the background before it is painted again,
/// so a tree with nothing opaque under it measures the same as one with a
/// background.
///
/// [#stats()] keeps the last frames as a window does, with the same means,
/// spans and percentiles. The present readings are zero: nothing is presented.
///
/// Confined to the thread it was opened on. Must be closed.
///
/// Read more: [The virtual clock](https://goldberry.dev/docs/guide/testing.html#the-virtual-clock).
public final class MeasuringStrip implements AutoCloseable {

    /// The strip underneath: the mounted tree, the renderer, the clock.
    private final Filmstrip strip;

    /// The one buffer every frame paints into.
    private final PixelBuffer buffer;

    private final FrameRing ring = new FrameRing();

    /// Opens a meter over `strip`, which it owns from here on.
    ///
    /// Package-private: [Offscreen#measure] is the door.
    MeasuringStrip(Filmstrip strip) {
        this.strip = Objects.requireNonNull(strip, "strip");
        var surface = strip.surface();
        this.buffer = PixelBuffer.allocate(surface.size(), surface.format());
    }

    /// Builds, lays out and paints the frame at the current clock, and says what
    /// it cost.
    ///
    /// The first frame paints the whole buffer. After that, only the damage:
    /// nothing, when nothing changed.
    ///
    /// The raster stage runs until the frame has ended, because a threaded
    /// rasterizer is still drawing until then and a reading that stopped earlier
    /// would leave its work out.
    ///
    /// @throws IllegalStateException if this strip has been closed
    public MeasuredFrame frame() {
        strip.requireOpen();
        var surface = strip.surface();
        var render = strip.render();
        var sequence = strip.sequence();
        var frame = surface.frameOver(buffer);
        var beganAt = System.nanoTime();
        FrameSequence.Stages stages;
        List<DamageRect> damage;
        try {
            stages = sequence.layOut(frame, strip.renderer(), beganAt);
            // Before the paint, as a window takes it: the clip has to be in
            // place before anything is drawn.
            damage = render.damage(frame);
            render.repaint(frame, damage, surface.background());
        } finally {
            frame.end();
        }
        var rasterizedAt = System.nanoTime();
        // After the frame, as a window and a strip capture them: a `Measured`
        // widget told about its region acts on the next frame.
        sequence.captureRegions(frame);
        surface.placed(frame);

        ring.stages(stages.buildNanos(), stages.styleNanos(), stages.layoutNanos(), rasterizedAt - stages.laidOutAt());
        ring.record(beganAt, rasterizedAt);
        ring.presented(PresentTimings.NONE);
        return new MeasuredFrame(ring.newest().copy(), damage);
    }

    /// The last frames, read as a window's are: means, spans and percentiles of
    /// each stage over up to [FrameRing#CAPACITY] frames.
    ///
    /// The interval and the rate are between this strip's frames as the caller
    /// paced them, which a test does not pace like a display; the paint and its
    /// stages are what to read.
    public FrameStats stats() {
        return ring;
    }

    /// What the buffer holds now, **copied**.
    ///
    /// A copy because the buffer is painted again by the next frame, and only the
    /// damaged part of it: an image over it would change under its holder. The
    /// copy is the one allocation this class makes per call.
    ///
    /// @throws IllegalStateException if this strip has been closed
    public Image picture() {
        strip.requireOpen();
        var copy = PixelBuffer.allocate(buffer.size(), buffer.format());
        copy.pixels().put(0, buffer.pixels(), 0, buffer.pixels().capacity());
        return Image.of(copy);
    }

    /// Moves the clock on by `millis` and returns this strip, as
    /// [Filmstrip#advance(int)] does.
    ///
    /// @throws IllegalArgumentException if negative
    /// @throws IllegalStateException if this strip has been closed
    public MeasuringStrip advance(int millis) {
        strip.advance(millis);
        return this;
    }

    /// Where the clock is, in milliseconds since the tree was mounted.
    public double nowMillis() {
        return strip.nowMillis();
    }

    /// How many frames have been measured.
    public long frames() {
        return ring.count();
    }

    /// Whether anything in the tree is still moving; see
    /// [Filmstrip#isAnimating()].
    public boolean isAnimating() {
        return strip.isAnimating();
    }

    /// Unmounts the tree and releases what the strip was keeping. Idempotent.
    /// Every [Image] from [#picture()] stays valid: each is a copy.
    @Override
    public void close() {
        strip.close();
    }

    @Override
    public String toString() {
        var surface = strip.surface();
        return "MeasuringStrip[" + surface.size() + " at " + surface.scale().factor() + "x, " + ring.count()
                + " frame(s), " + strip.nowMillis() + "ms" + (strip.isClosed() ? ", closed" : "") + "]";
    }
}
