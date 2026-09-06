package io.github.digitalsmile.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
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

/// The styled node of §4's pickers: a field, an affordance, and a popover.
///
/// All three of them are the same control with different things inside the
/// popup — §4 writes `date-picker` and `time-picker` in one entry and gives
/// `color-picker` the next one with the same two sentences in it — so this is one
/// node rather than three that would have to stay alike by hand.
///
/// ## The CSS type is a field, which is the one unusual thing here
///
/// Everything else in the catalog answers [Styled#cssType] with a literal,
/// because a widget is one kind of thing. This answers with what it was given,
/// because the three pickers are one kind of thing that a **stylesheet** has to
/// be able to tell apart: §2 gives `date-picker`/`time-picker` one row and
/// `color-picker` another, and a shared `picker` type would make those two rows
/// unwriteable.
///
/// It is not a hole in ADR-0065's rule. A part is styleable and not
/// constructible, and this is neither a part nor constructible by an
/// application — `…form.parts` is not exported, so the only callers are the three
/// packages in this module that build one.
///
/// ## `Alt+Down` is taken on the way *down*
///
/// §4 gives these pickers `Alt+Down` to open, and the field underneath would
/// otherwise take it: `text-input` reads `Down` as "go to the end of the line"
/// and does not ask about the modifier, because a single-line field has nowhere
/// else for `Down` to mean anything. Rather than teach `text-input` about
/// pickers, this takes the key in [#onKeyCapture], which runs before the focused
/// node sees it.
///
/// **`Escape` is taken on the bubble** and only when nothing is open, which is
/// the other half of the same care: while the popover is showing, `Escape`
/// belongs to it and the launcher dismisses it before this is reached
/// (ADR-0233). With nothing open it means §4's "reverts", which is `select`'s
/// rule — the control holds a value, typing is a way of reaching one, and
/// abandoning the attempt must not throw away something nobody asked to lose.
///
/// ## It delegates focus rather than taking it
///
/// The field inside is the Tab stop, so a press anywhere in the box — including
/// on the padding, and including on the affordance — puts the caret in the field.
/// That is [Handles#delegatesFocus], the mechanism `field`'s click-to-focus is
/// built on.
///
/// @param cssType    what a stylesheet calls this picker
/// @param field      the `text-input` this is wrapped around
/// @param open       whether the popover is showing, which is `:checked`
/// @param disabled   whether it refuses everything and matches `:disabled`
/// @param attributes the `id` and classes the document wrote
/// @param picker     what to tell about a key, a toggle or a measurement
public record PickerField(
        String cssType, Widget field, boolean open, boolean disabled, Attributes attributes, PickerActions picker)
        implements Widget.Leaf, Styled, Paints, Handles, Located, Semantics {

    @Override
    public String cssType() {
        return cssType;
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
    /// while the popover is showing — `select`'s spelling for the same state.
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
    /// **box** and not the field, so a popover opens under the whole control
    /// including its affordance.
    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        picker.located(self);
    }

    @Override
    public List<Widget> children() {
        return List.of(field, new PickerToggle(picker::toggle));
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

    /// §4: "combobox owning a grid, with the formatted date as its value text",
    /// and the same sentence for the other two.
    ///
    /// The role is honest; the value text is the half with nowhere to go, because
    /// [Semantics] carries a role, a name and a liveness and nothing that means
    /// "what this currently holds". M5, with the AccessKit bridge.
    @Override
    public Role role() {
        return Role.COMBO_BOX;
    }

    /// No name of its own: `field` supplies the label.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }

    /// What a picker's styled node asks of the state that owns the popover.
    public interface PickerActions {

        /// Opens the popover if it is closed. Returns whether it did.
        boolean open();

        /// A click on the affordance: open, or close if it is already showing.
        void toggle();

        /// §4's `Esc`. Returns whether there was anything to revert.
        boolean revert();

        /// Where the last frame drew the control, for anchoring the popover.
        void located(LogicalRect self);
    }
}
