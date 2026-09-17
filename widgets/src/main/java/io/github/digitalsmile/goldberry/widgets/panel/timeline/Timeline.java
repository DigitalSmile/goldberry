package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// An ordered list of events along an axis — `docs/core-widgets.md` §10's
/// `timeline`.
///
/// ```kdl
/// timeline pending=#true {
///     entry time="09:12" icon="git-commit" "Pushed" { text "Three commits to main." }
///     entry time="09:15" colour="#a3be8c" "Built"
///     entry time="09:20" "Deployed" { text "To staging, by the bot." }
/// }
/// timeline direction="horizontal" align="alternate" { … }
/// ```
///
/// ## What tells it from a list with dots
///
/// §10 answers that in one sentence: "`pending=#true` renders a trailing
/// unfilled marker for 'and then what happens next', which is what
/// distinguishes a timeline from a list with dots." So the line runs *past*
/// the last event when the story is not over, and stops at it when it is.
/// Everything else here — a marker, a label, a timestamp, a body — is what a
/// list row would carry too; the axis is the widget.
///
/// ## The line is a drawing
///
/// "Semantics: an ordered list — the connecting line is a drawing and is not
/// announced." Each [Entry] is a row of the list with its label and time as
/// its name; the rail beside it carries no semantics at all.
///
/// ## `alternate` is a third column
///
/// A vertical timeline with `align="alternate"` puts its events on both sides
/// of the axis, which is built as three columns: a side, the rail, a side —
/// and each entry fills one side and leaves the other empty, so the axis stays
/// in the middle whatever is on either side of it. A horizontal one does the
/// same with rows.
///
/// ## This node styles nothing
///
/// `timeline` as a **CSS type** is [TimelineList], the node this one builds
/// (ADR-0109).
///
/// @param children   the entries, as written; anything that is not an [Entry]
///                   is drawn in the list and left alone
/// @param direction  down the page, or along it
/// @param align      every entry on the same side of the axis, or alternating
/// @param pending    whether the story goes on: a trailing unfilled marker
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("timeline")
public record Timeline(List<Widget> children, Direction direction, Align align, boolean pending, Attributes attributes)
        implements Widget.Stateless, Attributed<Timeline> {

    /// §10's `direction="vertical|horizontal"`.
    public enum Direction {
        VERTICAL,
        HORIZONTAL;

        static Direction named(@Nullable String name) {
            return name != null && name.trim().toLowerCase(Locale.ROOT).equals("horizontal") ? HORIZONTAL : VERTICAL;
        }
    }

    /// §10's `align="start|alternate"`.
    public enum Align {
        START,
        ALTERNATE;

        static Align named(@Nullable String name) {
            return name != null && name.trim().toLowerCase(Locale.ROOT).equals("alternate") ? ALTERNATE : START;
        }
    }

    public Timeline {
        children = List.copyOf(children == null ? List.of() : children);
        direction = direction == null ? Direction.VERTICAL : direction;
        align = align == null ? Align.START : align;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A vertical timeline of these entries, every one on the same side.
    public Timeline(Widget... children) {
        this(List.of(children), Direction.VERTICAL, Align.START, false, Attributes.NONE);
    }

    /// This timeline running the other way.
    public Timeline direction(Direction value) {
        return new Timeline(children, value, align, pending, attributes);
    }

    /// This timeline with its entries on alternating sides of the axis.
    public Timeline align(Align value) {
        return new Timeline(children, direction, value, pending, attributes);
    }

    /// This timeline with, or without, a trailing unfilled marker.
    public Timeline pending(boolean value) {
        return new Timeline(children, direction, align, value, attributes);
    }

    @Override
    public Timeline withAttributes(Attributes value) {
        return new Timeline(children, direction, align, pending, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The entries as written, before the list placed them.
    public List<Widget> rawEntries() {
        return children;
    }

    @Override
    public Widget build(BuildContext context) {
        var entries = new ArrayList<Widget>(children.size() + 1);
        var count = 0;
        for (var child : children) {
            if (child instanceof Entry) {
                count++;
            }
        }
        var index = 0;
        for (var child : children) {
            if (!(child instanceof Entry entry)) {
                entries.add(child);
                continue;
            }
            // The line after the last entry is drawn only when something is
            // still to come: it is the "and then" the pending marker stands at.
            var last = index == count - 1;
            entries.add(entry.placed(index, direction, side(index), align == Align.ALTERNATE, !last || pending));
            index++;
        }
        if (pending) {
            entries.add(Entry.pending(direction, side(index), align == Align.ALTERNATE));
        }
        return new TimelineList(entries, direction, align, attributes);
    }

    /// Which side of the axis entry `index` sits on: the start side unless
    /// alternating, where the odd ones cross over.
    private Entry.Side side(int index) {
        return align == Align.ALTERNATE && index % 2 == 1 ? Entry.Side.END : Entry.Side.START;
    }

    /// Builds a `timeline` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        Objects.requireNonNull(node, "node");
        return new Timeline(
                children,
                Direction.named(node.stringProperty("direction")),
                Align.named(node.stringProperty("align")),
                node.booleanProperty("pending"),
                Attributes.of(node));
    }
}
