package io.github.digitalsmile.goldberry.widgets.panel.accordion;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.function.IntConsumer;

/// A column of [io.github.digitalsmile.goldberry.widgets.panel.collapse.Collapse]
/// sections of which **one is open at a time** — `docs/core-widgets.md` §5's
/// "`accordion=#true` on a containing `column` makes siblings mutually
/// exclusive".
///
/// ```kdl
/// column accordion=#true {
///     collapse title="General"  { text "…" }
///     collapse title="Advanced" { text "…" }
/// }
/// ```
///
/// ## Why a `column` becomes one of these
///
/// §5 puts the flag on the container, and it is right to: "one at a time" is a
/// rule about *siblings*, which no section can enforce about the others. But a
/// `column` is the most-used container in the toolkit and it is a plain record —
/// making it stateful so that one flag can be honoured would give every column in
/// every document a `State` object it never uses.
///
/// So `column accordion=#true` **inflates to this**, and this reports its
/// `cssType` as `column` with an `accordion` class. A document writes what §5
/// says, a stylesheet still sees a column, and an ordinary column pays nothing
/// ([ADR-0166]).
///
/// ## It is a composite, like every other one here
///
/// The sections become **controlled**: each is re-issued with the `open` this
/// widget decides and an `onToggle` that reports back — which is exactly what
/// `radio-group` does to its `radio` children
/// ([ADR-0073](../../../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)),
/// and it is why a section can stay a value. A section that was already
/// controlled by the *application* is left alone: two things deciding one boolean
/// is a bug, and the application asked first.
///
/// Anything that is not a `collapse` passes through untouched, so a heading or a
/// rule between the sections is an ordinary child.
///
/// @param open       which section is open, or `-1` for none; with [#onOpen] this
///                   is which one *is* open, and without it which one starts
/// @param onOpen     what opening a section asks for, or null to keep it here
/// @param children   the sections, and whatever else is between them
/// @param attributes the `id` and classes, which land on the `column` node
public record Accordion(int open, IntConsumer onOpen, List<Widget> children,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Accordion> {

    /// Nothing open, which is what an accordion of shut sections starts as.
    public static final int NONE = -1;

    public Accordion(Widget... sections) {
        this(NONE, null, List.of(sections), Attributes.NONE);
    }

    public Accordion {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
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
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new AccordionState();
    }
}
