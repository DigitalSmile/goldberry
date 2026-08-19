package io.github.digitalsmile.goldberry.widgets.controls.option;

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
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// One choice in a [io.github.digitalsmile.goldberry.widgets.controls.segmented.Segmented]
/// or a [io.github.digitalsmile.goldberry.widgets.controls.select.Select]
/// (§11, `docs/core-widgets.md` §3).
///
/// ```kdl
/// segmented bind="view.mode" change="pickMode" {
///     option value="list" "List"
///     option value="grid" "Grid"
/// }
/// ```
///
/// ## One node, two controls, one package
///
/// §3 gives `segmented` and `select` the same child node — a value, a label, an
/// icon, and nothing else — so this is one widget by specification. It lived in
/// `…controls.segmented` while that was its only caller, under
/// [ADR-0092](../../../../../../../../book/src/adr/0092-a-primitive-is-a-widget-like-any-other.md)'s
/// rule about not generalising from one, and moved here when the second arrived:
/// a package named after one of two callers tells the reader the wrong thing
/// ([ADR-0141](../../../../../../../../book/src/adr/0141-a-select-is-a-closed-control-and-a-list.md)).
///
/// **The drawing is not shared, and does not need to be.** A segment is a cell in
/// a bar and a choice in a dropdown is a row; both are `option` in CSS, and which
/// is drawn is the ancestor's — `segmented option` against `popover option`. That
/// is a descendant selector telling one widget's two *surroundings* apart, which
/// is not what the improvisation below is: that one would be using an ancestor to
/// tell two different widgets apart, and the difference is whether the selector
/// is describing where a thing is or what it is.
///
/// ## What it knows and what it is told
///
/// The same division [io.github.digitalsmile.goldberry.widgets.controls.radio.Radio]
/// makes, because §3 says a segmented control shares `radio-group`'s model
/// *exactly*: an option owns its [#value()], its label and its icon, and is told
/// whether it is selected, what picking it does, and whether the set as a whole
/// is unavailable. "Exactly one of these is on" is a fact about the set
/// ([io.github.digitalsmile.goldberry.widgets.controls.segmented.Segmented#children()]), so an option inflated from markup starts unselected
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
/// the node `docs/core-widgets.md` §3 writes, in both that control and `select` —
/// which is what eventually put it in a package of its own.
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
/// @param roving     whether the keyboard landing on this option chooses it —
///                   true in a `radio-group` and a `segmented`, false in a
///                   `select`'s list. See [#inAList()], which is the argument
@Markup("option")
public record Option(
        String value, String label, Icon icon, boolean selected, Runnable onSelect,
        boolean disabled, Attributes attributes, boolean roving)
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

    /// The form every caller wrote before there were two keyboard models, and
    /// still the one to reach for: an option is [#roving()] unless a control says
    /// otherwise, because that is `segmented`'s and `radio-group`'s shape and
    /// they are two of the three callers.
    public Option(String value, String label, Icon icon, boolean selected, Runnable onSelect,
            boolean disabled, Attributes attributes) {
        this(value, label, icon, selected, onSelect, disabled, attributes, true);
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
                onSelect, disabled, attributes, roving);
    }

    /// This segment, disabled or not.
    ///
    /// One segment of a bar can be unavailable while the rest are not — a view
    /// this document has no data for — which is why this is here as well as on
    /// the control.
    public Option disabled(boolean value) {
        return new Option(this.value, label, icon, selected, onSelect, value, attributes, roving);
    }

    /// This option as its control sees it: told whether it is on, what picking it
    /// does, and whether the set as a whole is unavailable.
    ///
    /// Package-private until `select` needed it from another package, and the
    /// visibility costs nothing it was protecting: **both controls rewrite every
    /// option on every build**, so a `selected` an application set here is
    /// discarded before it is ever drawn. What keeps a set from having two
    /// selected options was never this modifier — it is that "exactly one" is
    /// computed in one place from the bound value and stored nowhere (ADR-0141).
    public Option within(boolean isSelected, Runnable select, boolean groupDisabled) {
        return new Option(value, label, icon, isSelected, select, disabled || groupDisabled,
                attributes, roving);
    }

    @Override
    public Option withAttributes(Attributes attributes) {
        return new Option(value, label, icon, selected, onSelect, disabled, attributes, roving);
    }

    /// This option as a **row in a list** rather than a cell in a bar: the
    /// keyboard moves over it without choosing it, and `Enter` is what chooses.
    ///
    /// §3 gives the two controls that share this node two different keyboards, in
    /// as many words. A `radio-group` — and therefore a `segmented` — has "arrow
    /// keys move selection (roving focus)", so an arrow *is* the choice. A
    /// `select` has "arrows, Enter/Esc", so an arrow moves and `Enter` commits,
    /// and `Esc` has something to leave alone. Both are what those controls do
    /// everywhere, and the difference is not cosmetic: an arrow in a dropdown
    /// that chose would also close the list, so the second press would have
    /// nothing to move ([ADR-0141]).
    ///
    /// One flag rather than two, because the two halves are one decision: a set
    /// where the keyboard chooses has no use for a separate commit, and a set
    /// with a commit must not choose before it.
    public Option inAList() {
        return new Option(value, label, icon, selected, onSelect, disabled, attributes, false);
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
    /// along [io.github.digitalsmile.goldberry.widgets.controls.segmented.Segmented#focusScope()]'s axis and this widget hears about it in
    /// [#onFocusChanged] ([ADR-0073]).
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat() || !event.modifiers().none()) {
            return;
        }
        // `Enter` for a row in a list and not for a cell in a bar. The catalog's
        // rule is that `Enter` belongs to a dialog's default action, and it holds
        // where a control sits in a form — but a list is in a popup of its own,
        // over everything, and there is no default action behind it to take.
        // Choosing is the only thing `Enter` can mean there, and §3 says so.
        if (event.key() == Key.SPACE || (!roving && event.key() == Key.ENTER)) {
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
        if (focused && fromKeyboard && roving) {
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

    /// Builds an `option` from markup.
    ///
    /// `selected` and the action are the control's to supply on every build,
    /// which is why neither is an attribute.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Option(Wiring.requiredValue("option", node), Wiring.label(node),
                wiring.icon(node), false, null,
                Wiring.disabled(node), Attributes.of(node));
    }
}
