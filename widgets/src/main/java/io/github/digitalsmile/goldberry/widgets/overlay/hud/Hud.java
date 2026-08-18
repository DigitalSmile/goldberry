package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// What the frame loop is doing, on top of the window it is doing it to —
/// `docs/core-widgets.md` §7's `hud`.
///
/// ```kdl
/// hud
/// hud readings="fps frame paint"
/// ```
///
/// ```java
/// host.overlay(new Hud(), Corner.BOTTOM_END);
/// ```
///
/// ## It reports the loop; it does not drive it
///
/// A frame-rate display that asked for a frame so it could show a fresh number
/// would be measuring itself: the window would never idle, the rate would read a
/// steady 60 whatever the application was doing, and
/// `docs/design-system.md` §1.7's "the frame loop is fully idle when no animation
/// is active" would be false for every window with a HUD in the corner. So this
/// widget never calls `repaint`. It draws the numbers as of the frame it is being
/// drawn in, and when the loop goes quiet they stop moving with it — which is not
/// a stale reading but the correct one, because the rate of a loop drawing
/// nothing is a question about frames that are not happening.
///
/// The consequence to know: **a HUD only tells you about frames you were already
/// getting.** Watch it during a resize, a scroll or a transition, which is when
/// there is something to watch.
///
/// ## The numbers come down the render context
///
/// [io.github.digitalsmile.goldberry.FrameStats] arrives on
/// [Paints.Context#frames()], beside the frame clock and for the same reason: it
/// is a fact about the frame being rendered rather than about this node, and two
/// HUDs in one window must not disagree. Nothing is passed in when one is built,
/// which is what lets `hud` be a bare node in a document — and what lets a test
/// hand the renderer numbers it chose and get a golden image out.
///
/// A HUD rendered with no frame loop over it — inside a unit test, or into a
/// [io.github.digitalsmile.goldberry.Layer] — draws dashes rather than zeroes. A
/// zero is a measurement.
///
/// @param readings   which readings to show, in order. Never empty
/// @param attributes `id` and `class`, exactly as on the primitives
public record Hud(List<Reading> readings, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Hud> {

    /// The readings a bare `hud` shows: the rate, and how much of each frame the
    /// toolkit spent painting it.
    ///
    /// Two rather than three, and [Reading#FRAME] is the one left out on purpose:
    /// it is `1000 / fps` and a HUD that showed both would be spending a third of
    /// its width restating the first number. It is one attribute away for whoever
    /// is thinking in budgets rather than in rates.
    public static final List<Reading> DEFAULT = List.of(Reading.FPS, Reading.PAINT);

    public Hud(Reading... readings) {
        this(List.of(readings), Attributes.NONE);
    }

    public Hud() {
        this(DEFAULT, Attributes.NONE);
    }

    public Hud {
        readings = List.copyOf(readings == null || readings.isEmpty() ? DEFAULT : readings);
        attributes = attributes == null ? Attributes.NONE : attributes;
        Objects.requireNonNull(readings, "readings");
    }

    @Override
    public String cssType() {
        return "hud";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Hud withAttributes(Attributes value) {
        return new Hud(readings, value);
    }

    /// One part per reading.
    ///
    /// Parts rather than one text run with separators in it, because each is a
    /// different measurement and a stylesheet should be able to say so — dim the
    /// units, colour a paint time that has run out of budget. A single paragraph
    /// could carry none of that (ADR-0065).
    @Override
    public List<Widget> children() {
        return List.copyOf(readings.stream().map(HudReading::new).map(Widget.class::cast).toList());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
