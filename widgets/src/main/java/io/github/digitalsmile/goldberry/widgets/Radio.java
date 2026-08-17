package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// One option of a [RadioGroup] (§11, `docs/core-widgets.md` §3).
///
/// ```kdl
/// radio-group bind="prefs.theme" change="pickTheme" {
///     radio value="light" "Light"
///     radio value="dark"  "Dark"
/// }
/// ```
///
/// ## What it knows and what it is told
///
/// A radio owns its [#value()] and its label, and **nothing else**. Whether it is
/// selected, what happens when it is picked, and whether it is disabled are all
/// handed to it by the group in [RadioGroup#children()], because "exactly one of
/// these is on" is a fact about the set and cannot be held by any member of it.
/// A radio inflated from markup therefore starts unselected and unwired, and the
/// group rewrites it on every build — which is also what keeps the parity
/// invariant honest, since that is precisely the value a Java caller writes.
///
/// A `radio` outside a group is inert rather than an error: it draws, it takes no
/// focus worth having, and picking it does nothing. That is the same choice
/// [Button] makes for a button with no `press=` — a control being styled before
/// it is wired is a normal stage of building a screen, not a mistake.
///
/// ## Selection follows focus
///
/// Arrow keys inside a group move focus, and a radio raises its change the moment
/// keyboard focus lands on it ([ADR-0073]). It does **not** move its own tick:
/// the value goes up as an event, the application sets the property, and the tick
/// comes back down through the group's binding ([ADR-0063]). So an arrow key on a
/// group whose handler does nothing moves the focus ring and leaves the selection
/// where it was, which is the visible form of "the state did not change".
///
/// A *mouse* focus deliberately does not select, or a click would select twice:
/// once when the press moved focus and once for the click itself.
///
/// @param value      what this option means, reported to the group's `change`
///                   handler — the string the document wrote, uninterpreted
/// @param label      the text beside the glyph; the click target includes it
/// @param selected   whether the dot is showing. The group's to set, not the
///                   author's
/// @param onSelect   what asking for this option does. Also the group's
/// @param disabled   whether it refuses selection and matches `:disabled`
/// @param attributes `id` and `class`, exactly as on the primitives
public record Radio(
        String value, String label, boolean selected, Runnable onSelect, boolean disabled,
        Widgets.Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

    public Radio {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        attributes = attributes == null ? Widgets.Attributes.NONE : attributes;
    }

    /// An option with a value and a label — what an author writes, in Java or in
    /// KDL, and the two produce equal values because neither can say more.
    public Radio(String value, String label) {
        this(value, label, false, null, false, Widgets.Attributes.NONE);
    }

    /// An option whose label is its value, for the common case where they are the
    /// same word.
    public Radio(String value) {
        this(value, value);
    }

    /// This option, disabled or not.
    ///
    /// One option of a group can be unavailable while the rest are not — a
    /// licence tier that this account cannot pick — which is why this is here as
    /// well as on the group.
    public Radio disabled(boolean value) {
        return new Radio(this.value, label, selected, onSelect, value, attributes);
    }

    /// This option with classes added — `radio.compact` from Java.
    public Radio styled(String... classes) {
        return new Radio(value, label, selected, onSelect, disabled,
                new Widgets.Attributes(attributes.id(), Set.of(classes), attributes.key()));
    }

    /// This option as its group sees it: told whether it is on, what picking it
    /// does, and whether the group as a whole is unavailable.
    ///
    /// Package-private, because there is exactly one caller and letting an
    /// application set `selected` itself is how a group ends up with two.
    Radio within(boolean isSelected, Runnable select, boolean groupDisabled) {
        return new Radio(value, label, isSelected, select, disabled || groupDisabled, attributes);
    }

    @Override
    public String cssType() {
        return "radio";
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
        // The value, not the position: a group whose options are filtered or
        // reordered keeps each option's element -- and with it the focus that is
        // sitting on one of them.
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

    /// Picks this option on a click anywhere in the row, label included — the
    /// same target [Checkbox] uses, and for the same reason: a 16px glyph is a
    /// poor thing to aim at when the label beside it is five times as wide.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            select();
            event.consume();
        }
    }

    /// `Space` picks this option, and `Enter` deliberately does not — the line
    /// [Checkbox] draws, for the same reason: Enter belongs to a dialog's default
    /// action.
    ///
    /// Arrow keys are absent from this method on purpose. Which option is *next*
    /// is a fact about the group, and a radio cannot see its siblings; the router
    /// moves the focus and this widget hears about it in [#onFocusChanged]
    /// ([ADR-0073]).
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
    /// group actually does.
    @Override
    public void onFocusChanged(boolean focused, boolean fromKeyboard) {
        if (focused && fromKeyboard) {
            select();
        }
    }

    /// The glyph and the label, as child widgets — the shape [Checkbox] uses, and
    /// for the same reason: the glyph has its own background, border and radius,
    /// and one [ComputedStyle] cannot carry two.
    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(2);
        children.add(new RadioIndicator(selected, disabled));
        if (!label.isEmpty()) {
            children.add(new Widgets.Text(label));
        }
        return List.copyOf(children);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Asks for this option. It does **not** select it.
    ///
    /// Nothing here reads [#selected()]: re-picking the option already on is not
    /// an error and not a toggle — it is a request for the state the group is
    /// already in, which the application's `Property.set` swallows as a no-op.
    /// That is what makes Tab returning into a group harmless.
    private void select() {
        if (!disabled && onSelect != null) {
            onSelect.run();
        }
    }
}
