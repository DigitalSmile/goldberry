package io.github.digitalsmile.goldberry.widgets.panel.skeleton;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// The shape content will be — `docs/core-widgets.md` §5's `skeleton`.
///
/// ```kdl
/// skeleton shape="title"
/// skeleton shape="text" lines=3
/// skeleton shape="circle"
/// ```
///
/// ## It is a loop, and it is the only one allowed to be
///
/// §1.7 rule 4: nothing loops except explicit continuous indicators. §5 makes
/// this the one exception in the canon — "a shimmer is a loop, so §1.7 rule 4
/// applies: it is a `linear` opacity pulse on `--gb-motion-overlay`×5, it is the
/// only decoration in the canon allowed to loop, and under reduced motion it
/// holds still at its dimmest."
///
/// Which is why the pulse is computed here and not written as a CSS transition.
/// A transition runs between two states and a skeleton has one; the pulse is a
/// pure function of the clock — `Spinner`'s arrangement exactly, and for the same
/// reason ([ADR-0081](../../../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)):
/// no controller, no start, no stop, every skeleton on the screen in step by
/// construction, and one that unmounts leaves nothing behind.
///
/// **Reduced motion holds it at its dimmest**, not at its brightest and not at
/// the average. A placeholder that stops pulsing at full strength reads as
/// content that has arrived and is blank.
///
/// ## Sized from what it stands in for
///
/// §5: "sized from the typography token it stands in for so the layout does not
/// jump when the real content arrives". Every dimension is the stylesheet's —
/// `skeleton.title` is as tall as a title's line box, `skeleton.text` as tall as
/// body text — so a theme that changes its type scale moves the placeholders with
/// it, which is the whole point of sizing them from a token rather than from a
/// number written here.
///
/// The **last line of a paragraph is short**, at 60%, because a block of
/// identical full-width bars reads as a table rather than as prose. That is the
/// one piece of appearance this widget insists on, and it is a class on the last
/// child rather than a width set here.
///
/// @param shape      what is being stood in for
/// @param lines      how many bars, for [Shape#TEXT]; ignored by the others
/// @param attributes the `id` and classes
@Markup("skeleton")
public record Skeleton(Shape shape, int lines, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Skeleton> {

    /// §5's `shape="text|title|circle|rect"`.
    public enum Shape {

        /// Body text: [Skeleton#lines] bars, the last one short.
        TEXT,

        /// One bar at a heading's height.
        TITLE,

        /// An avatar.
        CIRCLE,

        /// Anything else — an image, a chart, a map. Sized by the stylesheet or
        /// by whatever the author puts round it, because a rectangle standing in
        /// for arbitrary content has no token to take its size from.
        RECT;

        String cssClass() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Shape of(String text) {
            if (text == null || text.isBlank()) {
                return TEXT;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "a skeleton's shape is \"text\", \"title\", \"circle\" or \"rect\","
                                + " not \"" + text + "\"", e);
            }
        }
    }

    /// §5's "`linear` opacity pulse on `--gb-motion-overlay`×5". `--gb-motion-overlay`
    /// is 200ms, so a full cycle is a second — and the period is **there and back**,
    /// which is what makes it a pulse rather than a sawtooth that snaps.
    private static final double PERIOD = 1000;

    /// The two ends of the pulse.
    ///
    /// Not 0 and 1: a placeholder that fades to nothing is a layout that flickers
    /// empty, and one at full strength is indistinguishable from content. The
    /// range is narrow on purpose — this is a decoration saying "still working",
    /// not an animation.
    private static final double DIMMEST = 0.45;
    private static final double BRIGHTEST = 1.0;

    public Skeleton() {
        this(Shape.TEXT, 3, Attributes.NONE);
    }

    public Skeleton(Shape shape) {
        this(shape, 3, Attributes.NONE);
    }

    public Skeleton {
        shape = shape == null ? Shape.TEXT : shape;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (lines < 1) {
            throw new IllegalArgumentException(
                    "a skeleton of " + lines + " lines would stand in for nothing;"
                            + " leave it out rather than asking for none");
        }
    }

    @Override
    public String cssType() {
        return "skeleton";
    }

    @Override
    public Skeleton withAttributes(Attributes value) {
        return new Skeleton(shape, lines, value);
    }

    @Override
    public String id() {
        return attributes.id();
    }

    /// The shape's own class beside the author's, so `skeleton.title` selects
    /// without anybody having to write the class in the document.
    @Override
    public Set<String> classes() {
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add(shape.cssClass());
        return Set.copyOf(all);
    }

    /// One bar per line for [Shape#TEXT]; one for everything else.
    ///
    /// A single bar is still a child rather than this node painting itself,
    /// because the pulse is on the **bars** and not on the box around them — a
    /// paragraph whose whole block faded would be one grey rectangle breathing,
    /// where three bars at the same opacity read as three lines.
    @Override
    public List<Widget> children() {
        var count = shape == Shape.TEXT ? lines : 1;
        var bars = new ArrayList<Widget>(count);
        for (var index = 0; index < count; index++) {
            var last = shape == Shape.TEXT && index == count - 1 && count > 1;
            bars.add(new SkeletonBar(shape, last));
        }
        return List.copyOf(bars);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Whether this is still asking for frames — it always is, which is what a
    /// perpetual loop means.
    ///
    /// A skeleton on screen keeps the frame loop awake, and that is the cost of
    /// the one looping decoration in the canon: §1.7's "the frame loop is fully
    /// idle when no animation is active" holds, because a skeleton *is* an active
    /// animation.
    ///
    /// **It stays true under reduced motion**, where the pulse holds still and
    /// the frames are therefore wasted. `isAnimating` is a property of the
    /// *description* and takes no `Context`, so it cannot see the preference;
    /// `spinner` has had the same shape since ADR-0081 and pays the same cost. It
    /// is a frame's worth of paint on a static image, not a correctness problem,
    /// and closing it means giving `isAnimating` the context — which is a change
    /// to the SPI for two widgets.
    @Override
    public boolean isAnimating() {
        return true;
    }

    /// How far round the pulse `now` is, `0..1`, there and back — the triangle
    /// wave §5's "linear pulse" describes. `Spinner#turnAt` is the same function
    /// without the fold.
    static double pulseAt(double now, boolean reducedMotion) {
        if (reducedMotion) {
            return DIMMEST;
        }
        var phase = (now % PERIOD) / PERIOD;
        if (phase < 0) {
            phase += 1;
        }
        // 0 -> 1 -> 0 across the period, so the two ends meet and there is no
        // snap back to the start.
        var triangle = phase < 0.5 ? phase * 2 : (1 - phase) * 2;
        return DIMMEST + (BRIGHTEST - DIMMEST) * triangle;
    }

    /// Builds a `skeleton` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var lines = (int) node.numberProperty("lines", 3);
        return new Skeleton(Shape.of(node.stringProperty("shape")), lines, Attributes.of(node));
    }

    /// One bar, pulsing.
    record SkeletonBar(Shape shape, boolean last) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "skeleton-bar";
        }

        /// The shape's class, so a circle's bar can be round and a title's tall;
        /// and `.last` on a paragraph's final line, which is the one that is
        /// short.
        @Override
        public Set<String> classes() {
            return last ? Set.of(shape.cssClass(), "last") : Set.of(shape.cssClass());
        }

        @Override
        public boolean isAnimating() {
            return true;
        }

        /// The opacity is **multiplied into** whatever the stylesheet set, rather
        /// than replacing it: a theme that dims its placeholders, or an author who
        /// puts a skeleton inside something faded, still gets what they asked for
        /// and this pulses within it.
        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            var pulse = pulseAt(context.nowMillis(), context.reducedMotion());
            return Box.of().style(style).opacity(style.opacity() * pulse);
        }
    }
}
