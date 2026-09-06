package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendGradient;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// §4's hue slider and its optional alpha slider — `color-ramp`, a **part**.
///
/// ## One part for both, because they differ only in their stops
///
/// A hue ramp is six stops round the wheel and an alpha ramp is two over the
/// chosen colour. Everything else — the geometry, the drag, the thumb, the
/// metrics — is the same, and §2 gives them one line between them ("hue/alpha
/// sliders `slider` metrics"). Two records would be two copies of a drag.
///
/// ## Not a `slider`
///
/// §2 says *`slider` metrics*, not `slider`, and the difference is the track: a
/// `slider`'s is a groove in `--gb-border` and these are a picture of what the
/// value means. §8's subset has no gradient — [BlendGradient] is a fill style the
/// painter has, not something a stylesheet can ask for (ADR-0207) — so the track
/// is painted rather than styled, which makes this a `canvas` with a drag on it
/// and a `slider` in nothing but its height.
///
/// It is also **not focusable**, and that is §4's doing rather than an omission:
/// it gives this control one keyboard, "arrows move the plane cursor", and says
/// nothing about the sliders. A focusable ramp would add two Tab stops inside a
/// popover whose field is the source of truth.
///
/// ## The alpha ramp is drawn over a chequerboard
///
/// Otherwise the transparent end is the panel's surface, and a slider whose left
/// half is "the same colour as the popover" tells a user nothing about what
/// transparent looks like. The chequer is two `fillRect` loops rather than an
/// image, because it is eight squares.
///
/// @param kind     which ramp this is
/// @param colour   the colour the ramp is drawn from and the thumb sits on
/// @param onChange told the new position, `0..1`
/// @param disabled whether it refuses everything
record ColorRamp(Kind kind, HsvColor colour, DoubleConsumer onChange, boolean disabled)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// Which of §4's two sliders this is.
    enum Kind {

        /// Six stops round the wheel. Always shown.
        HUE,

        /// Transparent to opaque over the chosen colour. Shown only when the
        /// picker was built with `alpha`.
        ALPHA
    }

    /// How big a chequer square is, in logical pixels.
    private static final float CHEQUER = 6;

    private static final int CHEQUER_LIGHT = 0xFF9099A8;
    private static final int CHEQUER_DARK = 0xFF6C7686;

    @Override
    public String cssType() {
        return "color-ramp";
    }

    @Override
    public Set<String> classes() {
        return Set.of(kind == Kind.HUE ? "hue" : "alpha");
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (disabled) {
            return;
        }
        switch (event.kind()) {
            case PRESSED -> {
                if (event.button() == PointerEvent.Button.PRIMARY) {
                    at(event);
                    event.consume();
                }
            }
            case MOVED -> {
                if (!Double.isNaN(event.dragX())) {
                    at(event);
                    event.consume();
                }
            }
            default -> {}
        }
    }

    private void at(PointerEvent event) {
        var local = event.local();
        if (local == null || local.width() <= 0) {
            return;
        }
        onChange.accept(Math.clamp(local.x() / local.width(), 0, 1));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var ramp = kind;
        var argb = colour.opaque().toArgb();
        var position = ramp == Kind.HUE ? colour.hue() / 360.0 : colour.alpha();
        return Box.of().style(style).painting((frame, size) -> paint(frame, size, ramp, argb, position));
    }

    private static void paint(Frame frame, LogicalSize size, Kind kind, int argb, double position) {
        var width = size.width();
        var height = size.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (kind == Kind.ALPHA) {
            chequer(frame, width, height);
        }
        try (var path = BlendPath.create()) {
            path.moveTo(0, 0);
            path.lineTo(width, 0);
            path.lineTo(width, height);
            path.lineTo(0, height);
            path.closeSubPath();
            try (var ramp = BlendGradient.linear(0, 0, width, 0)) {
                if (kind == Kind.HUE) {
                    for (var stop = 0; stop <= 6; stop++) {
                        ramp.addStop(stop / 6.0, new HsvColor(stop * 60.0, 1, 1, 1).toArgb());
                    }
                } else {
                    ramp.addStop(0, argb & 0x00FFFFFF);
                    ramp.addStop(1, argb);
                }
                frame.fillPath(0, 0, path, ramp);
            }
        }
        thumb(frame, position * width, height);
    }

    /// What transparent looks like. Eight squares and a loop, not an image.
    private static void chequer(Frame frame, float width, float height) {
        for (var y = 0f; y < height; y += CHEQUER) {
            for (var x = 0f; x < width; x += CHEQUER) {
                var dark = ((int) (x / CHEQUER) + (int) (y / CHEQUER)) % 2 == 1;
                frame.fillRect(
                        x,
                        y,
                        Math.min(CHEQUER, width - x),
                        Math.min(CHEQUER, height - y),
                        dark ? CHEQUER_DARK : CHEQUER_LIGHT);
            }
        }
    }

    /// A vertical bar rather than a disc, because a ramp is one-dimensional and a
    /// disc would suggest the other axis meant something.
    ///
    /// Drawn in white with a black edge so it is visible over every stop of both
    /// ramps — the plane's cursor picks one or the other from the colour under
    /// it, and a ramp's thumb crosses colours it cannot choose between.
    private static void thumb(Frame frame, double x, double height) {
        try (var bar = BlendPath.create()) {
            bar.moveTo(x, 0);
            bar.lineTo(x, height);
            frame.strokePath(0, 0, bar, 4, BlendStrokeCap.BUTT, BlendStrokeJoin.MITER_CLIP, 0xFF000000);
            frame.strokePath(0, 0, bar, 2, BlendStrokeCap.BUTT, BlendStrokeJoin.MITER_CLIP, 0xFFFFFFFF);
        }
    }
}
