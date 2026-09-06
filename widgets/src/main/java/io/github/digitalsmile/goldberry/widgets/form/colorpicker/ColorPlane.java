package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
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
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// §4's "saturation/value plane" — `color-plane`, a **part**.
///
/// ## Three fills and no per-pixel loop
///
/// The plane is the pure hue, then white fading out to the right, then black
/// fading in downwards. That is the standard construction and it is here because
/// of what the alternative costs: a 200×160 plane is 32,000 pixels, and computing
/// each of them in Java once a frame is a colour picker that makes the frame
/// budget its problem. Three `fillPath`s with two linear gradients is Blend2D's
/// problem instead, which is the argument `content-widgets.md` §3 makes for
/// building charts on `canvas`.
///
/// The order matters and is not interchangeable: white *then* black. Saturation
/// is a wash towards white and value is a wash towards black, and black over a
/// half-washed white is the colour at that corner where white over black is grey.
///
/// ## It is focusable, and it is the one thing in the popover that is
///
/// §4 gives this control "arrows move the plane cursor by 1, `Shift`+arrows by
/// 10" and gives the popover nothing else to focus. The sliders are dragged and
/// the hex field is typed into; the plane is the only part with a keyboard of its
/// own, so it takes the focus rather than the panel around it having to route to
/// it.
///
/// **By 1 and by 10 of what**, since §4 does not say: of the plane's own 100
/// steps, so an arrow is one percent of saturation or value and `Shift` is ten.
/// Points would tie the step to §2's 200×160 and make a themed plane step
/// differently; percent is what the model is in.
///
/// @param colour   what is chosen, which the cursor sits on
/// @param onChange told a new saturation and value as the cursor moves
/// @param disabled whether it refuses everything
record ColorPlane(HsvColor colour, PlaneCursor onChange, boolean disabled)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// What an arrow moves, as a fraction of the plane. See the class note.
    static final double STEP = 0.01;

    /// What `Shift`+arrow moves.
    static final double COARSE_STEP = 0.10;

    /// How wide the cursor's ring is drawn, in logical pixels.
    private static final double CURSOR_RADIUS = 6;

    @Override
    public String cssType() {
        return "color-plane";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// A press places the cursor and a drag moves it — `text-input`'s rule and
    /// its reason: a drag has to start somewhere and the click has not happened
    /// yet when the drag does.
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
                // `dragX()` is NaN when no button is down, which is the router
                // reporting "no gesture" through the arithmetic (ADR-0075). The
                // button is not asked about here, because which one started the
                // gesture is the press's question.
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
        if (local == null || local.width() <= 0 || local.height() <= 0) {
            return;
        }
        // Value runs **up** the plane, so the y axis is inverted: the bright end
        // is the top, which is what every colour picker draws and what the
        // gradient below paints.
        onChange.at(Math.clamp(local.x() / local.width(), 0, 1), Math.clamp(1 - local.y() / local.height(), 0, 1));
    }

    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var modifiers = event.modifiers();
        if (modifiers.control() || modifiers.alt()) {
            return;
        }
        var step = modifiers.shift() ? COARSE_STEP : STEP;
        var handled = true;
        switch (event.key()) {
            case LEFT -> move(-step, 0);
            case RIGHT -> move(step, 0);
            case UP -> move(0, step);
            case DOWN -> move(0, -step);
            default -> handled = false;
        }
        if (handled) {
            // Consumed even at an edge, `calendar`'s reason unchanged: a control
            // with the keyboard owns its arrows, or `Left` would walk the focus
            // scope of the popover it is in.
            event.consume();
        }
    }

    private void move(double bySaturation, double byValue) {
        onChange.at(Math.clamp(colour.saturation() + bySaturation, 0, 1), Math.clamp(colour.value() + byValue, 0, 1));
    }

    /// A slider, which is the nearest true thing and not an exact one.
    ///
    /// This is a control whose value you move continuously, which is what
    /// [Role#SLIDER] means — and it has **two** axes, which no role in this
    /// enum or in ARIA has a word for. The alternatives are worse rather than
    /// less exact: [Role#GROUP] says "a boundary with content in it" and this has
    /// no content, and [Role#GRID] promises cells addressed by row and column,
    /// which is the one thing a continuous plane is not.
    ///
    /// The half that is missing is the same one three other widgets are waiting
    /// on: what it currently *holds*. `Semantics` has no value channel, so a
    /// plane cannot say which colour the cursor is on any more than a
    /// `code-input` can say what it holds. M5.
    @Override
    public Role role() {
        return Role.SLIDER;
    }

    /// No name of its own: the popover is the control, and `field` names that.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var hue = colour.hueArgb();
        var saturation = colour.saturation();
        var value = colour.value();
        return Box.of().style(style).painting((frame, size) -> paint(frame, size, hue, saturation, value));
    }

    private static void paint(Frame frame, LogicalSize size, int hue, double saturation, double value) {
        var width = size.width();
        var height = size.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        frame.fillRect(0, 0, width, height, hue);
        try (var path = BlendPath.create()) {
            rect(path, width, height);
            try (var toWhite = BlendGradient.linear(0, 0, width, 0)) {
                toWhite.addStop(0, 0xFFFFFFFF);
                toWhite.addStop(1, 0x00FFFFFF);
                frame.fillPath(0, 0, path, toWhite);
            }
            try (var toBlack = BlendGradient.linear(0, 0, 0, height)) {
                toBlack.addStop(0, 0x00000000);
                toBlack.addStop(1, 0xFF000000);
                frame.fillPath(0, 0, path, toBlack);
            }
        }
        cursor(frame, saturation * width, (1 - value) * height, value > 0.5 ? 0xFF000000 : 0xFFFFFFFF);
    }

    private static void rect(BlendPath path, double width, double height) {
        path.moveTo(0, 0);
        path.lineTo(width, 0);
        path.lineTo(width, height);
        path.lineTo(0, height);
        path.closeSubPath();
    }

    /// A ring, drawn in whichever of black and white the colour under it is
    /// furthest from — because a cursor is the one mark on this control that has
    /// to be visible over every colour it can be put on.
    private static void cursor(Frame frame, double x, double y, int argb) {
        try (var ring = BlendPath.create()) {
            ring.moveTo(x + CURSOR_RADIUS, y);
            ring.ellipticArcTo(CURSOR_RADIUS, CURSOR_RADIUS, 0, true, true, x - CURSOR_RADIUS, y);
            ring.ellipticArcTo(CURSOR_RADIUS, CURSOR_RADIUS, 0, true, true, x + CURSOR_RADIUS, y);
            frame.strokePath(0, 0, ring, 2, BlendStrokeCap.ROUND, BlendStrokeJoin.ROUND, argb);
        }
    }
}
