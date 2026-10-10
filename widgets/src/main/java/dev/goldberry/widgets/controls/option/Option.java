package dev.goldberry.widgets.controls.option;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One choice in a [dev.goldberry.widgets.controls.segmented.Segmented]
/// or a [dev.goldberry.widgets.controls.select.Select]: a value, a label and,
/// sometimes, an icon.
///
/// ```kdl
/// segmented bind="view.mode" change="pickMode" {
///     option value="list" "List"
///     option value="grid" "Grid"
///     option value="map" icon="map" name="Map"
/// }
/// ```
///
/// In Java, `new Option("list", "List")`, or `new Option("list")` when the
/// label is the value, then [#withIcon] and [#disabled] as needed. `value=` is
/// what the control reports when this option is picked, the argument is the
/// label, `icon=` goes before it, and an icon-only option needs `name=` so a
/// screen reader has something to say.
///
/// ## One node, two controls, one package
///
/// `segmented` and `select` take the same child node — a value, a label, an
/// icon, and nothing else — so this is one widget, in a package of its own
/// because it has two callers and a package named after one of them would tell
/// the reader the wrong thing.
///
/// **The drawing is not shared, and does not need to be.** A segment is a cell in
/// a bar and a choice in a dropdown is a row; both are `option` in CSS, and which
/// is drawn is the ancestor's — `segmented option` against `popover option`. That
/// is a descendant selector telling one widget's two *surroundings* apart, which
/// is a different thing from using an ancestor to tell two different widgets
/// apart: the difference is whether the selector describes where a thing is or
/// what it is.
///
/// ## What it knows and what it is told
///
/// The same division [dev.goldberry.widgets.controls.radio.Radio]
/// makes, because a segmented control shares `radio-group`'s model exactly: an
/// option owns its [#value()], its label and its icon, and is told whether it is
/// selected, what picking it does, and whether the set as a whole is
/// unavailable. "Exactly one of these is on" is a fact about the set
/// ([dev.goldberry.widgets.controls.segmented.Segmented#children()]), so an
/// option inflated from markup starts unselected and unwired and the control
/// rewrites it on every build — which is also what keeps the Java-built and
/// KDL-built forms equal, since that is precisely the value a Java caller writes.
///
/// ## Why it is not a `radio`
///
/// A radio is a glyph beside a label and this is a filled cell in a bar: two
/// drawings that share a model. Sharing the *widget* would mean one CSS type for
/// both, and a stylesheet could then only tell them apart by their ancestor —
/// `segmented radio` — and a part's own CSS type is what lets an author style
/// it without that improvisation. It is named `option` rather than `segment`
/// because that is the node both controls write.
///
/// ## The content is boxes, not child widgets
///
/// [dev.goldberry.widgets.controls.button.Button]'s shape
/// rather than the radio's: the icon and the label are boxes on *this* node,
/// because neither is separately styleable — there is one background, one radius
/// and one colour across a segment, and the pair has no metrics of its own
/// beyond the gap. A radio needs a child element because its glyph carries a
/// second background; a segment does not.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#option).
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
        String value,
        String label,
        @Nullable Icon icon,
        boolean selected,
        @Nullable Runnable onSelect,
        boolean disabled,
        Attributes attributes,
        boolean roving)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Option>, Semantics {

    /// The canonical constructor, written out so that the parameters taking null for a default can say so.
    public Option(
            String value,
            String label,
            @Nullable Icon icon,
            boolean selected,
            @Nullable Runnable onSelect,
            boolean disabled,
            @Nullable Attributes attributes,
            boolean roving) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException(
                    "an option with neither a label nor an icon has nothing to click on" + " and nothing to read out");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.value = value;
        this.label = label;
        this.icon = icon;
        this.selected = selected;
        this.onSelect = onSelect;
        this.disabled = disabled;
        this.attributes = attributes;
        this.roving = roving;
    }

    /// The form every caller wrote before there were two keyboard models, and
    /// still the one to reach for: an option is [#roving()] unless a control says
    /// otherwise, because that is `segmented`'s and `radio-group`'s shape and
    /// they are two of the three callers.
    public Option(
            String value,
            String label,
            @Nullable Icon icon,
            boolean selected,
            @Nullable Runnable onSelect,
            boolean disabled,
            Attributes attributes) {
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

    /// This segment with an icon before its label — a segment is a label, an
    /// icon, or both.
    ///
    /// The icon is borrowed, for the reason
    /// [dev.goldberry.widgets.controls.button.Button#withIcon]
    /// spells out: the application builds it once and keeps it, exactly as it
    /// keeps a `Font`.
    public Option withIcon(Icon icon) {
        return new Option(
                value, label, Objects.requireNonNull(icon, "icon"), selected, onSelect, disabled, attributes, roving);
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
    /// does, and whether anything else makes it unavailable.
    ///
    /// Public because `select` calls it from another package, and the visibility
    /// costs nothing: **both controls rewrite every option on every build**, so a
    /// `selected` an application set here is discarded before it is ever drawn.
    /// What keeps a set from having two selected options is not this modifier —
    /// it is that "exactly one" is computed in one place from the bound value and
    /// stored nowhere.
    ///
    /// @param alsoDisabled a further reason this option cannot be picked, or false.
    ///        **Not the control's own flag**:
    ///        [dev.goldberry.widgets.controls.segmented.Segmented]
    ///        deliberately passes the option's *own* `disabled` here, because a
    ///        disabled bar already reaches its segments through the router walking
    ///        the ancestors, and pushing it down as well would match `:disabled`
    ///        twice and fade the segment twice over. A `select`'s list is the
    ///        caller that really does pass its own: a row of a disabled combobox is
    ///        not pickable and nothing above it is drawn to say so.
    public Option within(boolean isSelected, @Nullable Runnable select, boolean alsoDisabled) {
        return new Option(value, label, icon, isSelected, select, disabled || alsoDisabled, attributes, roving);
    }

    @Override
    public Option withAttributes(Attributes attributes) {
        return new Option(value, label, icon, selected, onSelect, disabled, attributes, roving);
    }

    /// This option as a **row in a list** rather than a cell in a bar: the
    /// keyboard moves over it without choosing it, and `Enter` is what chooses.
    ///
    /// The two controls that share this node have two different keyboards. In a
    /// `radio-group` — and therefore a `segmented` — the arrow keys move the
    /// selection with a roving focus, so an arrow *is* the choice. In a `select`
    /// an arrow moves and `Enter` commits, and `Esc` has something to leave
    /// alone. Both are what those controls do everywhere, and the difference is
    /// not cosmetic: an arrow in a dropdown that chose would also close the list,
    /// so the second press would have nothing to move.
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
    public @Nullable String id() {
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
    /// along [dev.goldberry.widgets.controls.segmented.Segmented#focusScope()]'s
    /// axis and this widget hears about it in
    /// [#onFocusChanged].
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        // `Enter` for a row in a list and not for a cell in a bar. The catalog's
        // rule is that `Enter` belongs to a dialog's default action, and it holds
        // where a control sits in a form — but a list is in a popup of its own,
        // over everything, and there is no default action behind it to take.
        // Choosing is the only thing `Enter` can mean there.
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
            // The flow off this node's own style, because the label is an
            // anonymous child box that `style` never reaches -- the same move a
            // menu row makes for the same reason. It is what lets the stylesheet
            // say `option { white-space: nowrap; text-overflow: ellipsis }` and
            // have a segment's label cut at its cell rather than wrap inside it.
            content.add(Box.text(context.paragraph(style, label), style.color(), style.textFlow()));
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
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Option(
                Wiring.requiredValue("option", node),
                Wiring.label(node),
                wiring.icon(node),
                false,
                null,
                Wiring.disabled(node),
                Attributes.of(node));
    }

    @Override
    public Role role() {
        return Role.OPTION;
    }

    /// The label, or the explicit `name=` when there is no label to read.
    ///
    /// **An icon-only segment has an empty label by construction** — the icon is the
    /// whole of what is on screen — so deriving a name from it produces the empty
    /// string, which is a control a reader cannot announce. `name=` is for
    /// exactly this case, and it lives on `Attributes`, where every widget gets
    /// it rather than each remembering its own.
    ///
    /// The label wins where there is one. An author who writes both has said the
    /// same thing twice, and the one on screen is the one a sighted user is
    /// reading aloud to somebody else.
    @Override
    public @Nullable String accessibleName() {
        return label.isEmpty() ? attributes.name() : label;
    }
}
