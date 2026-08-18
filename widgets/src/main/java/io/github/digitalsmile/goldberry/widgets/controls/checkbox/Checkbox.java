package io.github.digitalsmile.goldberry.widgets.controls.checkbox;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// A checkbox — binary or tri-state (§11, `docs/core-widgets.md` §3).
///
/// The second control, and the first one whose *value* comes from outside it. A
/// button's description is what it does; a checkbox's description is what it does
/// **and what it currently is**, and those two arrive by different routes:
///
/// ```kdl
/// checkbox bind="prefs.frost" change="toggleFrost" "Frosted sidebar"
/// ```
///
/// `bind` is where the value is read from and `change` is what the user's click
/// runs. They are two attributes because data flows down and events flow up
/// ([ADR-0063]): this widget is handed the read-only [Observable] half of a
/// property and has no method with which to write to it, so the tick moves when
/// the application moves it and at no other time. A checkbox whose `change`
/// handler does nothing does not move — which looks like a bug and *is* one, in
/// the application, exactly where it should be.
///
/// ## Three states, and the two that are not the third
///
/// [Value#MIXED] is a real state and not a decoration: a "select all" over a
/// partial selection is neither on nor off, and drawing it as either is a lie
/// about the data. It matches `:indeterminate` rather than `:checked` so a
/// stylesheet that says `checkbox:checked` means the tick and nothing else.
///
/// **Toggling never produces MIXED.** Mixed is a state the application can
/// describe and the user cannot reach: clicking a mixed checkbox is a request for
/// "all of them", which is [Value#CHECKED]. Every desktop toolkit agrees on this
/// and the alternative is a control that cycles through a state nobody wants.
///
/// ## The label is part of the control
///
/// `docs/core-widgets.md` §3 asks for a click target that includes the label, so
/// this widget is one 32px-tall row — the design system's hit-target floor (§1.3)
/// — holding a [CheckIndicator] and the text. Clicking anywhere in it toggles,
/// which matters more than it sounds: a 16px square is a small target, and the
/// label is usually five times as wide.
///
/// @param label      the text beside the glyph; may be empty for a checkbox in a
///                   table cell, which is the one case with nothing to say
/// @param state      the value shown when nothing is bound
/// @param source     §9's `bind` — where the value is read from, when it is bound
/// @param onChange   §9's `change` — what a toggle asks the application to do;
///                   may be null for a checkbox that is not wired yet
/// @param disabled   whether it refuses to toggle and matches `:disabled`
/// @param attributes `id` and `class`, exactly as on the primitives
public record Checkbox(
        String label, Value state, Observable<?> source, Runnable onChange, boolean disabled,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Checkbox>, Bindable<Checkbox> {

    /// The three states of a tri-state checkbox.
    public enum Value {

        /// Off.
        UNCHECKED,

        /// On — `:checked`.
        CHECKED,

        /// Neither: some of what this checkbox summarises is on — `:indeterminate`.
        MIXED;

        /// The state a boolean means, for the ordinary binary case.
        public static Value of(boolean checked) {
            return checked ? CHECKED : UNCHECKED;
        }

        /// What clicking a checkbox in this state asks for.
        ///
        /// [#MIXED] goes to [#CHECKED], not to [#UNCHECKED]: the user is asking
        /// for "all of them", which is the only reading of a click on a partial
        /// selection that is ever what was meant.
        public Value toggled() {
            return this == CHECKED ? UNCHECKED : CHECKED;
        }
    }

    /// The stroke width of the tick, in logical pixels.
    ///
    /// 2px, which is the design system's icon stroke (§1.6) — the tick is drawn
    /// on the same 24×24 grid as every Lucide icon beside it, so it reads as the
    /// same hand. Not a CSS property because nothing in §8 spells one, and
    /// inventing `mark-thickness` to hold a constant nobody changes would be
    /// improvising a token (Principle 3).
    private static final double MARK_THICKNESS = 2;

    public Checkbox {
        Objects.requireNonNull(label, "label");
        state = state == null ? Value.UNCHECKED : state;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// An unbound checkbox with a label and a handler.
    ///
    /// The state is the caller's to supply on every rebuild, which is what makes
    /// it controlled: this is the Java spelling of `checkbox bind="…" change="…"`.
    public Checkbox(String label, Value state, Runnable onChange) {
        this(label, state, null, onChange, false, Attributes.NONE);
    }

    /// An unbound checkbox that is not wired yet — what a layout preview builds.
    public Checkbox(String label, Value state) {
        this(label, state, null, null, false, Attributes.NONE);
    }

    /// A checkbox that follows a property. The Java spelling of `bind=`.
    ///
    /// @param source read-only by construction, so this control cannot write to
    ///               the model even by accident ([ADR-0063])
    public static Checkbox of(String label, Observable<?> source, Runnable onChange) {
        return new Checkbox(label, Value.UNCHECKED,
                Objects.requireNonNull(source, "source"), onChange, false,
                Attributes.NONE);
    }

    /// This checkbox, disabled or not.
    public Checkbox disabled(boolean value) {
        return new Checkbox(label, state, source, onChange, value, attributes);
    }


    /// What this checkbox shows **right now** — the bound value, or [#state()].
    ///
    /// Read at render rather than captured at build, for the same reason
    /// [Text#resolved()] is: a change that lands between a build and a
    /// frame is shown by that frame rather than the one after it.
    ///
    /// A bound property may hold a [Value] or a [Boolean]; an application
    /// modelling a binary preference should not have to import a tri-state enum
    /// to bind one. Anything else — including a null, which is a property that has
    /// not loaded — reads as [#state()], because guessing that some other object
    /// means "ticked" would be worse than showing what the markup said.
    public Value resolved() {
        if (source == null) {
            return state;
        }
        return switch (source.get()) {
            case Value value -> value;
            case Boolean checked -> Value.of(checked);
            case null, default -> state;
        };
    }

    @Override
    public Checkbox bound(Observable<?> source) {
        return new Checkbox(label, state, source, onChange, disabled, attributes);
    }

    @Override
    public Checkbox withAttributes(Attributes attributes) {
        return new Checkbox(label, state, source, onChange, disabled, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "checkbox";
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
        return resolved() == Value.CHECKED;
    }

    @Override
    public boolean isIndeterminate() {
        return resolved() == Value.MIXED;
    }

    /// The glyph and the label, as child widgets rather than child boxes.
    ///
    /// Widgets, because a child widget becomes a child *element* and an element is
    /// what the cascade can reach: the glyph has its own background, radius and
    /// border, and one [ComputedStyle] cannot carry two of each. See
    /// [CheckIndicator] for why a part is CSS-selectable and not KDL-constructible.
    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(2);
        children.add(new CheckIndicator(resolved(), disabled, MARK_THICKNESS));
        if (!label.isEmpty()) {
            children.add(new Text(label));
        }
        return List.copyOf(children);
    }

    /// Toggles on a click anywhere in the control, label included.
    ///
    /// A click rather than a release, exactly as [io.github.digitalsmile.goldberry.widgets.controls.button.Button]: a press dragged off the
    /// control and let go is a cancelled click, and the router is what knows the
    /// difference ([ADR-0058]).
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            toggle();
            event.consume();
        }
    }

    /// `Space` toggles — and `Enter` deliberately does not.
    ///
    /// A button takes both because activating it is the only thing it does. Enter
    /// belongs to a dialog's default action (`docs/design-system.md` §2.3), and a
    /// checkbox that swallowed it would leave a form with no way to submit from
    /// the keyboard once focus was on one. Every desktop platform draws the line
    /// in the same place.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat() || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE) {
            toggle();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Asks the application to change the value. It does **not** change it here.
    ///
    /// The whole of ADR-0063 in one method: what the user did travels up as an
    /// event, the application decides, and the new value arrives back down through
    /// the binding. Nothing here reads [#resolved()], because nothing here needs
    /// to know the current value to report that the user asked for the other one —
    /// [Value#toggled()] is what an application applies, on the property it owns.
    private void toggle() {
        if (!disabled && onChange != null) {
            onChange.run();
        }
    }
}
