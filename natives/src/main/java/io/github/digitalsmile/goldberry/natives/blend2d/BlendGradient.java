package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendExtendMode;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// A gradient — a fill style that is not a colour (ADR-0207).
///
/// Three shapes, which are Blend2D's three: [#linear] along a line, [#radial]
/// between two circles and [#conic] around a point. The first is what a chart's
/// band wants; the other two arrived with the COLRv1 emoji face, whose glyphs are
/// filled with them (ADR-0456).
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

    private static final long LINEAR_X0 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("x0");
    private static final long LINEAR_Y0 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("y0");
    private static final long LINEAR_X1 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("x1");
    private static final long LINEAR_Y1 = Layouts.BL_LINEAR_GRADIENT_VALUES.offsetOf("y1");

    private static final long RADIAL_X0 = Layouts.BL_RADIAL_GRADIENT_VALUES.offsetOf("x0");
    private static final long RADIAL_Y0 = Layouts.BL_RADIAL_GRADIENT_VALUES.offsetOf("y0");
    private static final long RADIAL_X1 = Layouts.BL_RADIAL_GRADIENT_VALUES.offsetOf("x1");
    private static final long RADIAL_Y1 = Layouts.BL_RADIAL_GRADIENT_VALUES.offsetOf("y1");
    private static final long RADIAL_R0 = Layouts.BL_RADIAL_GRADIENT_VALUES.offsetOf("r0");
    private static final long RADIAL_R1 = Layouts.BL_RADIAL_GRADIENT_VALUES.offsetOf("r1");

    private static final long CONIC_X0 = Layouts.BL_CONIC_GRADIENT_VALUES.offsetOf("x0");
    private static final long CONIC_Y0 = Layouts.BL_CONIC_GRADIENT_VALUES.offsetOf("y0");
    private static final long CONIC_ANGLE = Layouts.BL_CONIC_GRADIENT_VALUES.offsetOf("angle");
    private static final long CONIC_REPEAT = Layouts.BL_CONIC_GRADIENT_VALUES.offsetOf("repeat");

    private final Blend2dGradient calls = Blend2dGradient.get();
    private final Arena arena;
    private final MemorySegment gradient;
    private final String shape;
    private final Thread owner = Thread.currentThread();

    private boolean closed;

    /// Writes one shape's values and constructs the gradient over them.
    ///
    /// Blend2D copies the values into the gradient's own Impl, so they are only
    /// alive for the length of the call — but they live in this gradient's arena
    /// rather than a confined one of their own, because a gradient is
    /// constructed once and this is a few doubles.
    @FunctionalInterface
    private interface Shape {
        void init(Blend2dGradient calls, Arena arena, MemorySegment gradient);
    }

    private BlendGradient(String shape, Shape init) {
        this.shape = shape;
        this.arena = Arena.ofConfined();
        try {
            this.gradient = arena.allocate(Layouts.BL_GRADIENT_CORE.layout());
            init.init(calls, arena, gradient);
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
    /// [BlendExtendMode#PAD], so the ends hold: a gradient covers the shape it
    /// was placed over and anything the clip lets past beyond it is the last
    /// stop rather than a second copy of the ramp.
    ///
    /// @throws IllegalArgumentException if any coordinate is not finite. Blend2D
    ///         accepts a NaN and fills the shape with nothing, which is
    ///         indistinguishable from a band whose arithmetic went wrong
    ///         upstream — the same trap [BlendContext#fillRect] guards.
    public static BlendGradient linear(double x0, double y0, double x1, double y1) {
        return linear(x0, y0, x1, y1, BlendExtendMode.PAD, BlendMatrix.IDENTITY);
    }

    /// A linear gradient from `(x0, y0)` to `(x1, y1)` in the space `transform`
    /// maps into the context's, extended beyond its ends by `extend`.
    ///
    /// @param transform where the gradient's own coordinates land in the
    ///        context's; [BlendMatrix#IDENTITY] for none
    /// @throws IllegalArgumentException if any coordinate is not finite
    public static BlendGradient linear(
            double x0, double y0, double x1, double y1, BlendExtendMode extend, BlendMatrix transform) {
        requireFinite("a linear gradient runs between two finite points", x0, y0, x1, y1);
        Objects.requireNonNull(extend, "extend");
        Objects.requireNonNull(transform, "transform");
        return new BlendGradient("linear", (calls, arena, gradient) -> {
            var values = arena.allocate(Layouts.BL_LINEAR_GRADIENT_VALUES.layout());
            values.set(ValueLayout.JAVA_DOUBLE, LINEAR_X0, x0);
            values.set(ValueLayout.JAVA_DOUBLE, LINEAR_Y0, y0);
            values.set(ValueLayout.JAVA_DOUBLE, LINEAR_X1, x1);
            values.set(ValueLayout.JAVA_DOUBLE, LINEAR_Y1, y1);
            calls.gradientInitLinear(gradient, values, extend, transform.toNative(arena));
        });
    }

    /// A gradient between two circles: the **first stop on the focal circle**
    /// `(focalX, focalY, focalRadius)` and the **last on the outer one**
    /// `(x, y, radius)`.
    ///
    /// Named rather than numbered, because Blend2D's struct calls the outer
    /// circle `0` and the focal one `1` while the first stop sits on circle `1`
    /// — so "circle 0" means opposite things on the two sides of a COLRv1
    /// conversion. This is SVG's `r`/`fr` pair, and the two-point conical
    /// gradient every COLRv1 renderer draws (ADR-0456).
    ///
    /// @throws IllegalArgumentException if any number is not finite, or a
    ///         radius is negative
    public static BlendGradient radial(
            double x,
            double y,
            double radius,
            double focalX,
            double focalY,
            double focalRadius,
            BlendExtendMode extend,
            BlendMatrix transform) {
        requireFinite("a radial gradient runs between two finite circles", x, y, radius, focalX, focalY, focalRadius);
        if (radius < 0 || focalRadius < 0) {
            throw new IllegalArgumentException(
                    "a circle has a radius of zero or more, and " + radius + " and " + focalRadius + " are not both");
        }
        Objects.requireNonNull(extend, "extend");
        Objects.requireNonNull(transform, "transform");
        return new BlendGradient("radial", (calls, arena, gradient) -> {
            var values = arena.allocate(Layouts.BL_RADIAL_GRADIENT_VALUES.layout());
            values.set(ValueLayout.JAVA_DOUBLE, RADIAL_X0, x);
            values.set(ValueLayout.JAVA_DOUBLE, RADIAL_Y0, y);
            values.set(ValueLayout.JAVA_DOUBLE, RADIAL_R0, radius);
            values.set(ValueLayout.JAVA_DOUBLE, RADIAL_X1, focalX);
            values.set(ValueLayout.JAVA_DOUBLE, RADIAL_Y1, focalY);
            values.set(ValueLayout.JAVA_DOUBLE, RADIAL_R1, focalRadius);
            calls.gradientInitRadial(gradient, values, extend, transform.toNative(arena));
        });
    }

    /// A ramp once around `(x, y)`, starting at `angle` radians and turning from
    /// the positive x axis towards the positive y axis.
    ///
    /// One full turn per ramp: a COLRv1 sweep over less than a turn is placed
    /// inside this one by where its stops fall, not by a second parameter
    /// (ADR-0456).
    ///
    /// @throws IllegalArgumentException if any number is not finite
    public static BlendGradient conic(double x, double y, double angle, BlendExtendMode extend, BlendMatrix transform) {
        requireFinite("a conic gradient turns around a finite point from a finite angle", x, y, angle);
        Objects.requireNonNull(extend, "extend");
        Objects.requireNonNull(transform, "transform");
        return new BlendGradient("conic", (calls, arena, gradient) -> {
            var values = arena.allocate(Layouts.BL_CONIC_GRADIENT_VALUES.layout());
            values.set(ValueLayout.JAVA_DOUBLE, CONIC_X0, x);
            values.set(ValueLayout.JAVA_DOUBLE, CONIC_Y0, y);
            values.set(ValueLayout.JAVA_DOUBLE, CONIC_ANGLE, angle);
            values.set(ValueLayout.JAVA_DOUBLE, CONIC_REPEAT, 1.0);
            calls.gradientInitConic(gradient, values, extend, transform.toNative(arena));
        });
    }

    private static void requireFinite(String what, double... numbers) {
        for (var number : numbers) {
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException(
                        what + ", and " + java.util.Arrays.toString(numbers) + " are not all finite");
            }
        }
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
        return "BlendGradient[" + shape + (closed ? ", released" : "") + "]";
    }
}
