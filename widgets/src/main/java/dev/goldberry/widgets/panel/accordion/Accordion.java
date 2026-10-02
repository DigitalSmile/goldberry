package dev.goldberry.widgets.panel.accordion;

import java.util.List;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;

/// A column of [dev.goldberry.widgets.panel.collapse.Collapse]
/// sections of which one is open at a time. Opening a section closes the one
/// that was open.
///
/// ```kdl
/// column accordion=#true {
///     collapse title="General"  { text "…" }
///     collapse title="Advanced" { text "…" }
/// }
/// ```
///
/// ```java
/// new Accordion(
///         new Collapse("General", new Text("…")),
///         new Collapse("Advanced", new Text("…")));
/// ```
///
/// ## Why it is written as a `column`
///
/// "One at a time" is a rule about siblings, which no section can enforce about
/// the others, so the flag goes on the container. But a `column` is the
/// most-used container in the toolkit and it is a plain record; giving it state
/// so that one flag can be honoured would give every column in every document a
/// `State` object it never uses. So `column accordion=#true` inflates to this
/// widget, and this widget reports its CSS type as `column` with an `accordion`
/// class. A stylesheet still sees a column, and an ordinary column pays nothing.
///
/// ## How it works
///
/// The sections become controlled: each is re-issued with the `open` this widget
/// decides and an `onToggle` that reports back, the way a `radio-group` controls
/// its `radio` children, so a section stays a value. A section the application
/// already controls is left alone: two things deciding one boolean is a bug, and
/// the application asked first.
///
/// Anything that is not a `collapse` passes through untouched, so a heading or a
/// rule between the sections is an ordinary child.
///
/// @param open       which section is open, or `-1` for none; with [#onOpen] this
///                   is which one *is* open, and without it which one starts
/// @param onOpen     what opening a section asks for, or null to keep it here
/// @param children   the sections, and whatever else is between them
/// @param attributes the `id` and classes, which land on the `column` node
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#collapse).
public record Accordion(int open, @Nullable IntConsumer onOpen, List<Widget> children, Attributes attributes)
        implements Widget.Stateful, Attributed<Accordion> {

    /// Nothing open, which is what an accordion of shut sections starts as.
    public static final int NONE = -1;

    public Accordion(Widget... sections) {
        this(NONE, null, List.of(sections), Attributes.NONE);
    }

    /// Written out so that the parameters taking null for a default can say so.
    public Accordion(
            int open, @Nullable IntConsumer onOpen, @Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.open = open;
        this.onOpen = onOpen;
        this.children = children;
        this.attributes = attributes;
    }

    /// Whether the application is deciding, rather than this widget.
    public boolean isControlled() {
        return onOpen != null;
    }

    @Override
    public Accordion withAttributes(Attributes value) {
        return new Accordion(open, onOpen, children, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new AccordionState();
    }
}
