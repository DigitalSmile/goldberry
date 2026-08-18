package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// One segment of a [Segmented] (§11, `docs/core-widgets.md` §3).
///
/// ```kdl
/// segmented bind="view.mode" change="pickMode" {
///     option value="list" "List"
///     option value="grid" "Grid"
/// }
/// ```
///
/// ## What it knows and what it is told
///
/// The same division [io.github.digitalsmile.goldberry.widgets.controls.radio.Radio]
/// makes, because §3 says a segmented control shares `radio-group`'s model
/// *exactly*: an option owns its [#value()], its label and its icon, and is told
/// whether it is selected, what picking it does, and whether the set as a whole
/// is unavailable. "Exactly one of these is on" is a fact about the set
/// ([Segmented#children()]), so an option inflated from markup starts unselected
/// and unwired and the control rewrites it on every build — which is also what
/// keeps §11's parity invariant honest, since that is precisely the value a Java
/// caller writes.
///
/// ## Why it is not a `radio`
///
/// A radio is a glyph beside a label and this is a filled cell in a bar: two
/// drawings that share a model. Sharing the *widget* would mean one CSS type for
/// both, and a stylesheet could then only tell them apart by their ancestor —
/// `segmented radio` — which is the descendant-selector improvisation
/// [ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)
/// exists to avoid. It is named `option` rather than `segment` because that is
/// the node `docs/core-widgets.md` §3 writes, in both this control and `select`.
///
/// ## The content is boxes, not child widgets
///
/// [io.github.digitalsmile.goldberry.widgets.controls.button.Button]'s shape
/// rather than the radio's: the icon and the label are boxes on *this* node,
/// because neither is separately styleable — there is one background, one radius
/// and one colour across a segment, and §3 gives the pair no metrics of its own
/// beyond the gap. A radio needs a child element because its glyph carries a
/// second background; a segment does not.
///
/// @param value      what this segment means, reported to the control's `change`
///                   handler — the string the document wrote, uninterpreted
/// @param label      the text in the segment; empty for an icon-only segment
/// @param icon       the icon before the label, or null. **Borrowed**, exactly as
///                   a button's is: a widget is a value that is rebuilt every
///                   frame, so it must not own something with a `close()`
/// @param selected   whether this is the chosen segment. The control's to set,
///                   not the author's
/// @param onSelect   what asking for this segment does. Also the control's
/// @param disabled   whether it refuses selection and matches `:disabled`
/// @param attributes `id` and `class`, exactly as on the primitives
public record Option(
        String value, String label, Icon icon, boolean selected, Runnable onSelect,
        boolean disabled, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Option> {

    public Option {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException(
                    "an option with neither a label nor an icon has nothing to click on"
                            + " and nothing to read out (§13)");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A segment with a value and a label — what an author writes, in Java or in
    /// KDL, and the two produce equal values because neither can say more.
    public Option(String value, String label) {
        this(value, label, null, false, null, false, Attributes.NONE);
    }

    /// A segment whose label is its value, for the common case where they are the
    /// same word.
    public Option(String value) {
        this(value, value);
    }

    /// This segment with an icon before its label (§3: "a label, an icon, or
    /// both").
    ///
    /// The icon is borrowed, for the reason
    /// [io.github.digitalsmile.goldberry.widgets.controls.button.Button#withIcon]
    /// spells out: the application builds it once and keeps it, exactly as it
    /// keeps a `Font`.
    public Option withIcon(Icon icon) {
        return new Option(value, label, Objects.requireNonNull(icon, "icon"), selected,
                onSelect, disabled, attributes);
    }

    /// This segment, disabled or not.
    ///
    /// One segment of a bar can be unavailable while the rest are not — a view
    /// this document has no data for — which is why this is here as well as on
    /// the control.
    public Option disabled(boolean value) {
        return new Option(this.value, label, icon, selected, onSelect, value, attributes);
    }

    /// This segment as its control sees it: told whether it is on, what picking it
    /// does, and whether the bar as a whole is unavailable.
    ///
    /// Package-private, because there is exactly one caller and letting an
    /// application set `selected` itself is how a set ends up with two.
    Option within(boolean isSelected, Runnable select, boolean groupDisabled) {
        return new Option(value, label, icon, isSelected, select, disabled || groupDisabled,
                attributes);
    }

    @Override
    public Option withAttributes(Attributes attributes) {
        return new Option(value, label, icon, selected, onSelect, disabled, attributes);
    }

    @Override
    public String cssType() {
        return "option";
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
        // The value, not the position: a bar whose options are filtered or
        // reordered keeps each segment's element -- and with it the focus that is
        // sitting on one of them. `radio` keys itself the same way.
        return attributes.key() != null ? attributes.key() : value;
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public boolean isChecked() {
        return selected;
    }

    /// Picks this segment on a click anywhere in it.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            select();
            event.consume();
        }
    }

    /// `Space` picks this segment, and `Enter` deliberately does not — the line
    /// every control in the catalog draws, for the same reason: Enter belongs to
    /// a dialog's default action.
    ///
    /// Arrow keys are absent on purpose. Which segment is *next* is a fact about
    /// the bar, and an option cannot see its siblings; the router moves the focus
    /// along [Segmented#focusScope()]'s axis and this widget hears about it in
    /// [#onFocusChanged] ([ADR-0073]).
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat() || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE) {
            select();
            event.consume();
        }
    }

    /// Selection follows keyboard focus, which is what an arrow key inside a
    /// radio group — and therefore inside this control — actually does.
    ///
    /// A *mouse* focus deliberately does not select, or a click would select
    /// twice: once when the press moved focus and once for the click itself.
    @Override
    public void onFocusChanged(boolean focused, boolean fromKeyboard) {
        if (focused && fromKeyboard) {
            select();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The content is child boxes rather than text on this node's own box, for
        // the reason `Button` gives: a box with text is a measured leaf, and Yoga
        // never lays a measured node's children out -- so a segment that held its
        // own text could not also hold an icon.
        var content = new ArrayList<Box>(2);
        if (icon != null) {
            content.add(Box.icon(icon, style.color()));
        }
        if (!label.isEmpty()) {
            content.add(Box.text(context.paragraph(style, label), style.color()));
        }
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// Asks for this segment. It does **not** select it.
    ///
    /// Nothing here reads [#selected()]: re-picking the segment already on is not
    /// an error and not a toggle — it is a request for the state the control is
    /// already in, which the application's `Property.set` swallows as a no-op.
    private void select() {
        if (!disabled && onSelect != null) {
            onSelect.run();
        }
    }
}
