package io.github.digitalsmile.goldberry.widgets.panel.statistic;

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
import java.util.Objects;
import java.util.Set;

/// A labelled number — `docs/core-widgets.md` §5's `statistic`.
///
/// ```kdl
/// statistic label="Active users" value="12,480" delta="+4.2%" direction="up"
/// statistic label="Latency" value="128" unit="ms" delta="-11 ms" direction="down"
/// ```
///
/// ## The number is a string, and that is deliberate
///
/// §5: "Formatting is the application's — the widget takes a string, for
/// `slider`'s reason: a locale-aware number formatted inside the toolkit makes a
/// golden image that cannot be reproduced on another machine." `12,480` is
/// `12.480` in half of Europe and `12 480` in the other half, and a golden that
/// depends on the runner's default locale is a golden that fails in CI for a
/// reason nobody can see in the diff. So the toolkit never formats: an
/// application already owns a `NumberFormat` and hands over the result.
///
/// ## A direction is a colour, not an arrow
///
/// `direction="up"` renders the delta in `--gb-success` and `down` in
/// `--gb-danger`. The direction says which way the number went; **whether that is
/// good is not something the widget can know** — latency going down is success —
/// so `direction` names the *sentiment* and the caller picks it, which is why the
/// second example above marks a fall as `down` for a metric where falling is
/// what you want. That reading is stated here because the alternative — mapping a
/// leading `-` to danger — would colour a latency improvement red.
///
/// A glyph is not drawn. §5 asks for the delta "rendered in
/// `--gb-success`/`--gb-danger`" and nothing more, and an arrow would be the only
/// thing in the catalog conveying meaning by colour *and* shape while the
/// accessible name conveys neither.
///
/// ## The sparkline is not built
///
/// §5's "optional `sparkline` from a `canvas`" waits on `canvas`, which is §12's
/// and is not in the catalog. Nothing here is shaped around its absence: a
/// sparkline is one more child at the end of the column.
///
/// @param label      what the number is of — always present, because §5 makes the
///                   label and the value one accessible name
/// @param value      the number, already formatted
/// @param unit       an optional suffix, set smaller and muted beside the value
/// @param delta      an optional change, or null
/// @param direction  which way [#delta] went, and therefore what colour it is
/// @param attributes the `id` and classes
@Markup("statistic")
public record Statistic(
        String label, String value, String unit, String delta, Direction direction,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Statistic> {

    /// Which way the delta went — the sentiment, not the arithmetic. See the
    /// class note.
    public enum Direction {

        /// `--gb-success`.
        UP,

        /// `--gb-danger`.
        DOWN,

        /// Neither: the delta is shown in the ordinary muted colour. The default,
        /// so a delta with no direction is legible rather than arbitrarily green.
        NONE;

        /// The class a stylesheet selects this by.
        String cssClass() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Direction of(String text) {
            if (text == null || text.isBlank()) {
                return NONE;
            }
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "up" -> UP;
                case "down" -> DOWN;
                case "none", "flat" -> NONE;
                default -> throw new IllegalArgumentException(
                        "a statistic's direction is \"up\", \"down\" or \"none\", not \""
                                + text + "\"");
            };
        }
    }

    public Statistic(String label, String value) {
        this(label, value, null, null, Direction.NONE, Attributes.NONE);
    }

    public Statistic {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        unit = unit == null || unit.isBlank() ? null : unit;
        delta = delta == null || delta.isBlank() ? null : delta;
        direction = direction == null ? Direction.NONE : direction;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// This statistic with a unit after its value.
    public Statistic unit(String value) {
        return new Statistic(label, this.value, value, delta, direction, attributes);
    }

    /// This statistic with a change under its value.
    public Statistic delta(String text, Direction which) {
        return new Statistic(label, value, unit, text, which, attributes);
    }

    @Override
    public String cssType() {
        return "statistic";
    }

    @Override
    public Statistic withAttributes(Attributes value) {
        return new Statistic(label, this.value, unit, delta, direction, value);
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    /// The label, the value row, and the delta when there is one.
    ///
    /// Parts rather than one `render`, for `slider`'s reason: three pieces of text
    /// at three ranks in §1.2's hierarchy are three things a stylesheet has to be
    /// able to reach separately, and a widget that drew them itself would have
    /// baked the hierarchy in.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(3);
        parts.add(new StatisticLabel(label));
        parts.add(new StatisticValue(value, unit));
        if (delta != null) {
            parts.add(new StatisticDelta(delta, direction));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Builds a `statistic` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Statistic(
                node.stringProperty("label"),
                node.stringProperty("value"),
                node.stringProperty("unit"),
                node.stringProperty("delta"),
                Direction.of(node.stringProperty("direction")),
                Attributes.of(node));
    }

    /// What the number is of.
    record StatisticLabel(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "statistic-label";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .children(Box.text(context.paragraph(style, text), style.color()));
        }
    }

    /// The number, with its unit beside it.
    ///
    /// One node holding both, because the unit is set against the value's
    /// baseline: `128 ms` is one reading and two boxes in a row, where a unit in
    /// its own top-level part would be a third line.
    record StatisticValue(String text, String unit) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "statistic-value";
        }

        @Override
        public List<Widget> children() {
            return unit == null ? List.of() : List.of(new StatisticUnit(unit));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            var content = new ArrayList<Box>(2);
            content.add(Box.text(context.paragraph(style, text), style.color()).shrink(0));
            content.addAll(children);
            return Box.of().style(style).children(content.toArray(Box[]::new));
        }
    }

    /// The unit after the number.
    record StatisticUnit(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "statistic-unit";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .children(Box.text(context.paragraph(style, text), style.color()));
        }
    }

    /// The change, coloured by its direction.
    record StatisticDelta(String text, Direction direction)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "statistic-delta";
        }

        /// `.up` and `.down`, so the colour is the stylesheet's decision and not a
        /// token this widget looked up — the rule every control here follows.
        @Override
        public Set<String> classes() {
            return direction == Direction.NONE ? Set.of() : Set.of(direction.cssClass());
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
