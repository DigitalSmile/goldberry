package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet calls `date-picker`, and everything that needs a frame.
///
/// [DatePicker] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes — the arrangement every stateful widget in this catalog
/// uses.
///
/// ## `Alt+Down` is taken on the way *down*
///
/// §4 gives this widget `Alt+Down` to open, and the field underneath would
/// otherwise take it: `text-input` reads `Down` as "go to the end of the line"
/// and does not ask about the modifier, because a single-line field has nowhere
/// else for `Down` to mean anything. Rather than teach `text-input` about a
/// picker, this takes the key in [#onKeyCapture], which runs before the focused
/// node sees it.
///
/// **`Escape` is taken on the bubble** and only when nothing is open, which is
/// the other half of the same care: while the grid is showing, `Escape` belongs
/// to the popup and the launcher dismisses it before this is reached (ADR-0233).
/// With nothing open it means §4's "reverts", which is `select`'s rule — the
/// control holds a value, typing is a way of reaching one, and abandoning the
/// attempt must not throw away something nobody asked to lose.
///
/// ## It delegates focus rather than taking it
///
/// The field inside is the Tab stop, so a press anywhere in the box — including
/// on the padding, and including on the toggle — puts the caret in the field.
/// That is [Handles#delegatesFocus], the mechanism `field`'s click-to-focus is
/// built on.
///
/// @param field      the `text-input` this is wrapped around
/// @param open       whether the grid is showing, which is `:checked`
/// @param disabled   whether it refuses everything and matches `:disabled`
/// @param attributes the `id` and classes the document wrote
/// @param picker     what to tell about a key, a toggle or a measurement
record DatePickerBox(Widget field, boolean open, boolean disabled, Attributes attributes, PickerActions picker)
        implements Widget.Leaf, Styled, Paints, Handles, Located, Semantics {

    @Override
    public String cssType() {
        return "date-picker";
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
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// Mirrored to `:checked`, which is how a stylesheet marks the affordance
    /// while the grid is showing — `select`'s spelling for the same state.
    @Override
    public boolean isChecked() {
        return open;
    }

    /// The field is the Tab stop; this hands the keyboard down to it.
    @Override
    public boolean delegatesFocus() {
        return !disabled;
    }

    /// Where the frame put this, which is what the popover is anchored to. The
    /// **box** and not the field, so a grid opens under the whole control
    /// including its affordance.
    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        picker.located(self);
    }

    @Override
    public List<Widget> children() {
        return List.of(field, new DatePickerToggle(picker::toggle));
    }

    /// See the class note: before the field, because the field would take it.
    @Override
    public void onKeyCapture(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        if (event.key() == Key.DOWN && event.modifiers().alt() && picker.open()) {
            event.consume();
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        if (event.key() == Key.ESCAPE && event.modifiers().none() && picker.revert()) {
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// §4: "combobox owning a grid, with the formatted date as its value text".
    ///
    /// The role is honest; the value text is the half with nowhere to go, because
    /// [Semantics] carries a role, a name and a liveness and nothing that means
    /// "what this currently holds". M5, with the AccessKit bridge — the entry
    /// `code-input` opened and `calendar` joined.
    @Override
    public Role role() {
        return Role.COMBO_BOX;
    }

    /// No name of its own: `field` supplies the label.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }

    /// The affordance that opens the grid — `date-picker-toggle`, a **part**.
    ///
    /// Not focusable, and not a `button`: §4 gives this control one Tab stop, and
    /// the keyboard's way in is `Alt+Down`. `select-chevron` is the same shape for
    /// the same reason.
    record DatePickerToggle(Runnable onPress) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "date-picker-toggle";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                onPress.run();
                event.consume();
            }
        }

        /// A calendar has no mark in `Box.Mark`, and adding one would be a glyph
        /// the painter has to keep — so it is a chevron, which is what the control
        /// does rather than what it holds. §2 gives this row `field = text-input`
        /// and says nothing about an affordance; `select`'s is a chevron and a
        /// picker reads as the same kind of thing.
        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHEVRON_DOWN, style.color(), 1.5));
        }
    }

    /// What this node asks of the state that owns the popover.
    interface PickerActions {

        /// Opens the grid if it is closed. Returns whether it did.
        boolean open();

        /// A click on the affordance: open, or close if it is already showing.
        void toggle();

        /// §4's `Esc`. Returns whether there was anything to revert.
        boolean revert();

        /// Where the last frame drew the control, for anchoring the popover.
        void located(LogicalRect self);
    }
}
