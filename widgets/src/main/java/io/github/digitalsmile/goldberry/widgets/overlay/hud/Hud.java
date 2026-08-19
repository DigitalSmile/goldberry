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
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

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
@Markup("hud")
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

    /// The rate, **both totals**, and where the toolkit's went —
    /// `hud readings="stages"` and what the showcase turns on.
    ///
    /// Two totals because they answer different questions and a breakdown makes
    /// the difference matter: `frame` is the whole interval, the platform's half
    /// included, and `paint` is the toolkit's share of it. Four stages under a
    /// `paint` of 2 ms inside a `frame` of 16.7 is idle hardware; the same four
    /// under a `paint` of 2 ms inside a `frame` of 40 is something outside this
    /// toolkit.
    ///
    /// Four stages rather than "everything a frame does": the hit-test capture and
    /// the frame's own setup are in [Reading#PAINT] and not in any of these, so
    /// the four do not add up to the total and are not meant to. What they are for
    /// is telling *which* of the four moved, which is the question a total cannot
    /// answer — and which went unanswered for a month while the cascade was
    /// running uncached (ADR-0142,
    /// [ADR-0146](../../../../../../../../book/src/adr/0146-a-hud-shows-where-the-frame-went.md)).
    public static final List<Reading> STAGES = List.of(
            Reading.FPS, Reading.FRAME, Reading.PAINT,
            Reading.BUILD, Reading.STYLE, Reading.LAYOUT, Reading.RASTER);

    /// A HUD showing [#STAGES].
    public static Hud stages() {
        return new Hud(STAGES, Attributes.NONE);
    }

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
    /// One part per reading, plus the line that says what the numbers are.
    ///
    /// The caption exists because every number here is a **mean over the last
    /// sixty frames** and nothing said so: `paint 2.1 ms` reads as "this frame"
    /// and is not, which makes a spike look like a plateau and a plateau look
    /// like a spike
    /// ([ADR-0150](../../../../../../../../book/src/adr/0150-a-hud-reads-itself-against-a-budget.md)).
    ///
    @Override
    public List<Widget> children() {
        var parts = new java.util.ArrayList<Widget>(readings.size() + 1);
        for (var reading : readings) {
            parts.add(new HudReading(reading));
        }
        parts.add(new HudCaption());
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Builds a `hud` from markup.
    ///
    /// §7's first overlay, and the only widget in the catalog that reads
    /// something about the frame loop rather than about a model. Nothing to bind
    /// and nothing to resolve: what it shows arrives on the render context, so a
    /// document writes `hud` and is done.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Hud(readings(node.stringProperty("readings")), Attributes.of(node));
    }

    /// `readings="fps paint"` — a space-separated list, like `class`.
    ///
    /// Null and blank both mean [#DEFAULT] rather than an error: a bare `hud` is
    /// the form almost every document will write.
    private static List<Reading> readings(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        // One name for the whole breakdown, because `readings="fps paint build
        // style layout raster"` is the list nobody wants to type and the one
        // everybody wants when a frame has gone wrong (ADR-0146).
        if ("stages".equals(value.trim())) {
            return STAGES;
        }
        return List.of(value.trim().split("\\s+")).stream().map(Reading::parse).toList();
    }
}
