package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendExtendMode;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// A linear gradient — a fill style that is not a colour (ADR-0207).
///
/// Every drawing call on [BlendContext] until this one took its colour as an
/// `0xAARRGGBB` argument, which is what keeps a frame free of style state
/// somebody forgot to set back. A gradient cannot be an argument: it is an
/// object with a geometry and a list of stops, and it has to exist while the
/// fill happens. So it is a resource, like a [BlendPath], and
/// [BlendContext#fillPath(double, double, BlendPath, BlendGradient)] is the one
/// call that takes one.
///
/// ## Coordinates
///
/// The two points are in the **context's own space** — logical pixels for a
/// scaled context, the same as every coordinate a caller passes — and they are
/// *not* moved by the origin a path is filled at. A gradient placed from the top
/// of a plot to its baseline stays there whichever path is drawn through it,
/// which is what makes one gradient usable for a run of bands.
///
/// ## Colours
///
/// Straight `0xAARRGGBB`, not premultiplied, for [BlendContext]'s reason.
///
/// **A fade to transparent must repeat the colour.** `0x00000000` is transparent
/// *black*, and a ramp from a green to it passes through grey — the classic
/// wrong gradient. The transparent end of a fade is the same RGB with a zero
/// alpha: `argb & 0x00FFFFFF`. Blend2D interpolates in premultiplied space, so
/// stated that way the ramp is the one colour thinning out, which is what
/// [#fade] does and why it exists rather than being left to each caller.
///
/// Confined to the thread that created it, and must be closed. It may be closed
/// as soon as the fill has been issued: `bl_context_set_fill_style` retains what
/// it is given, so the context keeps its own reference.
public final class BlendGradient implements AutoCloseable {

    private static final long X0 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("x0");
    private static final long Y0 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("y0");
    private static final long X1 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("x1");
    private static final long Y1 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("y1");

    private final Blend2dGradient calls = Blend2dGradient.get();
    private final Arena arena;
    private final MemorySegment gradient;
    private final Thread owner = Thread.currentThread();

    private boolean closed;

    private BlendGradient(double x0, double y0, double x1, double y1) {
        this.arena = Arena.ofConfined();
        try {
            this.gradient = arena.allocate(Layouts.BL_GRADIENT_CORE.layout());
            // Blend2D copies the values into the gradient's own Impl, so this
            // allocation is only alive for the length of the call -- but it
            // lives in the same arena rather than a confined one of its own,
            // because a gradient is constructed once and this is four doubles.
            var values = arena.allocate(Layouts.BL_LINEAR_GRADIENT_VALUES.layout());
            values.set(ValueLayout.JAVA_DOUBLE, X0, x0);
            values.set(ValueLayout.JAVA_DOUBLE, Y0, y0);
            values.set(ValueLayout.JAVA_DOUBLE, X1, x1);
            values.set(ValueLayout.JAVA_DOUBLE, Y1, y1);
            // PAD, so the ends hold: a gradient covers the shape it was placed
            // over and anything the clip lets past beyond it is the last stop
            // rather than a second copy of the ramp.
            calls.gradientInitLinear(gradient, values, BlendExtendMode.PAD);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// A linear gradient from `(x0, y0)` to `(x1, y1)`, with no stops yet.
    ///
    /// A gradient with no stops fills with nothing at all, which is what makes
    /// [#addStop] the next call rather than an option.
    ///
    /// @throws IllegalArgumentException if any coordinate is not finite. Blend2D
    ///         accepts a NaN and fills the shape with nothing, which is
    ///         indistinguishable from a band whose arithmetic went wrong
    ///         upstream — the same trap [BlendContext#fillRect] guards.
    public static BlendGradient linear(double x0, double y0, double x1, double y1) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
            throw new IllegalArgumentException("a gradient runs between two finite points, and (" + x0 + "," + y0
                    + ") to (" + x1 + "," + y1 + ") is not a pair of them");
        }
        return new BlendGradient(x0, y0, x1, y1);
    }

    /// A gradient from `argb` at `(x0, y0)` to the same colour, fully
    /// transparent, at `(x1, y1)`.
    ///
    /// The two-stop fade, which is the shape every caller so far wants and the
    /// one that is easy to write wrongly: the far stop is the **same RGB** with
    /// a zero alpha, not `0x00000000`. Fading to transparent black takes a
    /// coloured band through grey on its way out, which is visible on any hue
    /// that is not already dark and is the reason this is a method rather than
    /// two lines at each call site.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied. Its own alpha is
    ///        kept at the near end, so a band already drawn at 85% fades from
    ///        85% rather than being promoted to opaque
    public static BlendGradient fade(double x0, double y0, double x1, double y1, int argb) {
        var gradient = linear(x0, y0, x1, y1);
        try {
            gradient.addStop(0, argb);
            gradient.addStop(1, argb & 0x00FFFFFF);
        } catch (RuntimeException | Error e) {
            gradient.close();
            throw e;
        }
        return gradient;
    }

    /// Adds a stop `offset` of the way along, in the colour `argb`.
    ///
    /// @param offset 0 at the start point, 1 at the end
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    /// @throws IllegalArgumentException if the offset is outside 0 to 1, which
    ///         Blend2D rejects with `BL_ERROR_INVALID_VALUE` — raised here
    ///         instead so the message names the offset
    public void addStop(double offset, int argb) {
        requireUsable();
        if (!(offset >= 0) || !(offset <= 1)) {
            throw new IllegalArgumentException(
                    "a gradient stop sits between 0 and 1 along the gradient, and " + offset + " does not");
        }
        calls.gradientAddStop(gradient, offset, argb);
    }

    /// Whether this gradient has been released.
    public boolean isReleased() {
        return closed;
    }

    /// Releases Blend2D's side of the gradient. Idempotent.
    ///
    /// Safe immediately after a fill: the context retained its own reference
    /// when the style was set.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            calls.gradientDestroy(gradient);
        } finally {
            arena.close();
        }
    }

    MemorySegment pointer() {
        requireUsable();
        return gradient;
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendGradient has been released");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendGradient belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendGradient[linear" + (closed ? ", released" : "") + "]";
    }
}
