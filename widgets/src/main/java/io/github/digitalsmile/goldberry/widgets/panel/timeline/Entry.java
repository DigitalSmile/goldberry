package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// One event on a [Timeline]: a marker, a label, an optional timestamp and
/// optional body content.
///
/// ```kdl
/// entry time="09:12" icon="git-commit" "Pushed" { text "Three commits." }
/// entry colour="#a3be8c" "Built"
/// ```
///
/// ## The marker is a dot, an icon, or a widget
///
/// §10 lists "dot, icon or `badge`". A dot takes the entry's `colour`, or the
/// stylesheet's; an icon sits in a larger disc. Anything else — a `badge` is
/// the one §10 names — is written in a `marker` slot ([EntryMarker]) and drawn
/// on the axis in place of the dot; the rail keeps its width, so the axis does
/// not move under a wide one (ADR-0356). A widget marker wins over an icon.
///
/// ```kdl
/// entry "Released" { marker { badge class="success" "v2" }; text "Published." }
/// ```
///
/// ## Placed by the list
///
/// Which side of the axis it sits on, whether the line continues past it, and
/// its position are the list's to write on every build — a document cannot
/// put the second entry on the wrong side of an alternating timeline, and the
/// last line's absence is what "the story is over" looks like.
///
/// @param label      what happened
/// @param time       when, as the application spelled it, or null
/// @param icon       an icon in the marker, or null for a dot
/// @param colour     the dot's colour as `0xAARRGGBB`, or 0 for the
///                   stylesheet's
/// @param marker     a widget drawn on the axis instead of the dot, or null
/// @param body       the content shown under the label
/// @param placement  supplied by [Timeline] on every build; not an attribute
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("entry")
public record Entry(
        String label,
        @Nullable String time,
        @Nullable Icon icon,
        int colour,
        @Nullable Widget marker,
        List<Widget> body,
        Placement placement,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Entry>, Semantics {

    /// Which side of the axis an entry's words sit on.
    public enum Side {
        START,
        END
    }

    /// What the list wrote: where the entry is, and whether the line goes on.
    ///
    /// @param index     the entry's position, zero-based
    /// @param direction the list's direction
    /// @param side      which side of the axis the words are on
    /// @param twoSided  whether the list alternates, so every entry keeps a
    ///                  side on both sides of the axis and the axis stays put
    /// @param continues whether the line runs on past this marker
    /// @param pending   whether this is the trailing unfilled marker
    public record Placement(
            int index, Timeline.Direction direction, Side side, boolean twoSided, boolean continues, boolean pending) {

        /// Before the list has said anything.
        public static final Placement NONE =
                new Placement(0, Timeline.Direction.VERTICAL, Side.START, false, true, false);
    }

    public Entry {
        Objects.requireNonNull(label, "label");
        body = List.copyOf(body == null ? List.of() : body);
        placement = placement == null ? Placement.NONE : placement;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (label.isEmpty() && !placement.pending()) {
            throw new IllegalArgumentException(
                    "an entry needs a label: a timeline is read as a story, and an event with no name is"
                            + " a dot on a line (§13)");
        }
    }

    /// An event with a name, and whatever goes under it.
    public Entry(String label, Widget... body) {
        this(label, null, null, 0, null, List.of(body), Placement.NONE, Attributes.NONE);
    }

    /// The shape an entry had before it could hold a widget marker.
    public Entry(
            String label,
            @Nullable String time,
            @Nullable Icon icon,
            int colour,
            List<Widget> body,
            Placement placement,
            Attributes attributes) {
        this(label, time, icon, colour, null, body, placement, attributes);
    }

    /// This event with a timestamp beside its name.
    public Entry at(@Nullable String when) {
        return new Entry(label, when, icon, colour, marker, body, placement, attributes);
    }

    /// This event with an icon in its marker. The icon is **borrowed**
    /// (ADR-0043).
    public Entry withIcon(@Nullable Icon value) {
        return new Entry(label, time, value, colour, marker, body, placement, attributes);
    }

    /// This event's dot in `argb`, or 0 for the stylesheet's colour.
    public Entry colour(int argb) {
        return new Entry(label, time, icon, argb, marker, body, placement, attributes);
    }

    /// This event with `widget` on the axis in place of its dot — the Java
    /// spelling of a `marker` slot.
    public Entry withMarker(@Nullable Widget widget) {
        return new Entry(label, time, icon, colour, widget, body, placement, attributes);
    }

    /// Used by [Timeline] to say where this entry sits.
    Entry placed(int index, Timeline.Direction direction, Side side, boolean twoSided, boolean continues) {
        return new Entry(
                label,
                time,
                icon,
                colour,
                marker,
                body,
                new Placement(index, direction, side, twoSided, continues, false),
                attributes);
    }

    /// §10's trailing unfilled marker — an entry with no words, at the end.
    static Entry pending(Timeline.Direction direction, Side side, boolean twoSided) {
        return new Entry(
                "",
                null,
                null,
                0,
                null,
                List.of(),
                new Placement(-1, direction, side, twoSided, false, true),
                Attributes.NONE);
    }

    @Override
    public Entry withAttributes(Attributes value) {
        return new Entry(label, time, icon, colour, marker, body, placement, value);
    }

    @Override
    public String cssType() {
        return "entry";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    /// The document's classes, plus `end` for an entry across the axis and
    /// `pending` for the trailing marker.
    @Override
    public Set<String> classes() {
        var classes = new HashSet<>(attributes.classes());
        if (placement.side() == Side.END) {
            classes.add("end");
        }
        if (placement.pending()) {
            classes.add("pending");
        }
        return Set.copyOf(classes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The rail and a side — or, on an alternating timeline, a side, the rail
    /// and a side, with the words in one and the other empty, so the axis stays
    /// in the middle of every entry. A plain timeline leaves the far side out,
    /// which is what keeps it from being twice as wide as its words.
    @Override
    public List<Widget> children() {
        var rail = new TimelineRail(icon, colour, marker, placement);
        var words = placement.pending()
                ? new TimelineSide(List.of())
                : new TimelineSide(List.of(new TimelineBody(label, time, body)));
        var parts = new ArrayList<Widget>(3);
        if (!placement.twoSided()) {
            parts.add(rail);
            parts.add(words);
        } else if (placement.side() == Side.END) {
            parts.add(new TimelineSide(List.of()));
            parts.add(rail);
            parts.add(words);
        } else {
            parts.add(words);
            parts.add(rail);
            parts.add(new TimelineSide(List.of()));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// [Role#ROW] — one item of §10's "ordered list".
    @Override
    public Role role() {
        return Role.ROW;
    }

    /// The label, and the time after it when there is one — never the body,
    /// which is content of its own.
    @Override
    public @Nullable String accessibleName() {
        if (placement.pending()) {
            return null;
        }
        return time == null ? label : label + ", " + time;
    }

    /// Builds an `entry` from markup.
    ///
    /// No placement: the list writes it. A `marker` child is lifted out of the
    /// body onto the axis; two of them is a document describing two points for
    /// one event, and is refused.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        Widget marker = null;
        var body = new ArrayList<Widget>(children.size());
        for (var child : children) {
            if (child instanceof EntryMarker(var content, var _)) {
                if (marker != null) {
                    throw new IllegalArgumentException("an entry has one marker, and this one names two");
                }
                marker = content;
            } else {
                body.add(child);
            }
        }
        return new Entry(
                Wiring.label(node),
                node.stringProperty("time"),
                wiring.icon(node),
                Wiring.colour(node, "colour", "color"),
                marker,
                body,
                Placement.NONE,
                Attributes.of(node));
    }
}
