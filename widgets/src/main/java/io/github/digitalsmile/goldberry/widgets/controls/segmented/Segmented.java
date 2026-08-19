package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// A row of mutually exclusive options drawn as one joined bar (§11,
/// `docs/core-widgets.md` §3).
///
/// ```kdl
/// segmented bind="view.mode" change="pickMode" {
///     option value="list" "List"
///     option value="grid" "Grid"
///     option value="map"  "Map"
/// }
/// ```
///
/// ## It is `radio-group`'s model and not `radio-group`
///
/// §3 is explicit that the two share the model and the invariant *exactly* — one
/// Tab stop, arrows rove, exactly one selected, `change` reports the value — and
/// equally explicit that they are two widgets: "a segmented control is a
/// fixed-width bar that belongs in a toolbar, a radio group is a list that
/// belongs in a form". They are not substitutable in a layout, so
/// `radio-group.segmented` would be a class that changes what a thing *is*.
///
/// The difference shows up in this class in exactly one line, and it is not a
/// drawing: [#focusScope()] is [FocusScope#HORIZONTAL] where a group's is
/// [FocusScope#BOTH]. A radio group has no axis because its direction is its
/// stylesheet's; **a segmented control's axis is its own** — it is a bar, in one
/// direction, and no class turns it into a column. That is
/// [ADR-0078](../../../../../../../../book/src/adr/0078-a-focus-scope-has-an-axis.md)'s
/// rule applied for the first time to something that is not a menu, and it is
/// the machine-checkable form of "these are two widgets".
///
/// ## Controlled, like every other value in this toolkit
///
/// The control **reads** its value through `bind` and reports what the user asked
/// for through `change`. It sets nothing ([ADR-0063]). A bar whose handler does
/// nothing does not move, which is the visible form of "the state did not change"
/// and is where the bug is when one will not.
///
/// @param value      the option selected when nothing is bound; ignored when
///                   `source` is set
/// @param children   the segments, as written. Non-[Option] children are laid out
///                   and left alone, so a bar can carry something else
/// @param source     §9's `bind` — read-only, so this control cannot write to the
///                   model even by accident ([ADR-0063])
/// @param onChange   what to tell the application, given the picked value
/// @param disabled   whether the whole bar refuses selection
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("segmented")
public record Segmented(
        String value, List<Widget> children, Observable<?> source, Consumer<String> onChange,
        boolean disabled, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Segmented>,
        Bindable<Segmented> {

    public Segmented {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A bar with a selected value and a handler, unbound — the Java spelling of
    /// `segmented value="…" change="…"`.
    public Segmented(String value, Consumer<String> onChange, Option... options) {
        this(value, List.of(options), null, onChange, false, Attributes.NONE);
    }

    /// A bar that is not wired yet — what a layout preview builds.
    public Segmented(String value, Option... options) {
        this(value, List.of(options), null, null, false, Attributes.NONE);
    }

    /// A bar that follows a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction ([ADR-0063])
    public static Segmented of(Observable<?> source, Consumer<String> onChange, Option... options) {
        return new Segmented(null, List.of(options),
                Objects.requireNonNull(source, "source"), onChange, false, Attributes.NONE);
    }

    /// This bar, disabled or not.
    public Segmented disabled(boolean value) {
        return new Segmented(this.value, children, source, onChange, value, attributes);
    }

    /// Which segment is selected **right now** — the bound value, or [#value()].
    ///
    /// The bound value is compared by its `toString`, so a property holding an
    /// enum, an `Integer` or a `String` all work against the strings a document
    /// wrote — the narrow coercion `radio-group` uses and for the same reason. A
    /// null, or a value no segment carries, selects nothing rather than the first
    /// one: a bar that guessed would report a value the user never picked.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return current == null ? null : String.valueOf(current);
    }

    @Override
    public Segmented bound(Observable<?> source) {
        return new Segmented(value, children, source, onChange, disabled, attributes);
    }

    @Override
    public Segmented withAttributes(Attributes attributes) {
        return new Segmented(value, children, source, onChange, disabled, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "segmented";
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
    public Object key() {
        return attributes.key();
    }

    /// One Tab stop, with `Left` and `Right` roving inside it (§7.2, [ADR-0073]).
    ///
    /// [FocusScope#HORIZONTAL] and not [FocusScope#BOTH], which is the whole of
    /// what separates this control from `radio-group` in Java. A bar has a
    /// direction; `Up` and `Down` are therefore not this widget's to consume, and
    /// a segmented control sitting in a scrollable form must let them past
    /// (ADR-0078).
    ///
    /// A scope even when the bar is disabled, for the reason `radio-group` gives:
    /// a disabled bar has no focusable segments left, so the traversal skips it
    /// either way, and making the answer depend on state would make the shape of
    /// the widget depend on it too.
    @Override
    public FocusScope focusScope() {
        return FocusScope.HORIZONTAL;
    }

    /// Not focusable itself. Focus lands on a segment, so the ring is drawn around
    /// the thing the user is about to pick.
    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// One child — the track — holding the segments and the indicator that runs
    /// along them.
    ///
    /// The segments are rewritten here, which is where the invariant is applied:
    /// nothing is stored, so there is no path by which two can be on at once. The
    /// **index** is computed in the same pass and handed to the track, because
    /// "which segment, counting from the left" is a fact about the set and the
    /// only thing an indicator needs to know ([ADR-0099]).
    ///
    /// The bar's own `disabled` is deliberately **not** pushed down — it
    /// propagates for input by itself, because the router walks up the ancestors
    /// (ADR-0077), and pushing it would make every segment match `:disabled` too,
    /// so the 45% would apply once for the bar and again for each segment and land
    /// at 20%, the trap `radio-group` had to undo by hand.
    @Override
    public List<Widget> children() {
        var selected = resolved();
        var out = new ArrayList<Widget>(children.size());
        var index = -1;
        var seen = 0;
        for (var child : children) {
            if (child instanceof Option option) {
                var isSelected = option.value().equals(selected);
                if (isSelected) {
                    index = seen;
                }
                seen++;
                out.add(option.within(
                        isSelected,
                        onChange == null ? null : () -> onChange.accept(option.value()),
                        option.disabled()));
            } else {
                // Left exactly as written: a bar that silently dropped what it did
                // not recognise would be a document whose content disappeared with
                // no error. It is not counted either -- a heading between two
                // segments is not a segment, and counting it would put the
                // indicator one cell along from the option it marks.
                out.add(child);
            }
        }
        return List.of(new SegmentedTrack(out, index));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        // A row, and the stylesheet says so rather than this method: `segmented`
        // is `flex-direction: row` in `controls.css` because that is where every
        // other §3 metric lives. Unlike `radio-group` there is no class that flips
        // it -- see focusScope(). What this box holds is one child, the track;
        // the segments are its.
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Builds a `segmented` from markup.
    ///
    /// The same valued action `radio-group` takes, because §3 says this control
    /// shares that model exactly — a set's handler is useless without the value
    /// picked (ADR-0073).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Segmented(node.stringProperty("value"), children,
                wiring.bound(node), wiring.valued(node, "change"),
                Wiring.disabled(node), Attributes.of(node));
    }
}
