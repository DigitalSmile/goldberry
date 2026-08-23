package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The closed half of a [Select]: what is on screen when the list is not.
///
/// **This is the `select` a stylesheet selects.** [Select] itself is stateful and
/// styles nothing, so this node carries the CSS type and the `id` and classes the
/// document wrote — the shape `scroll` and `tabs` already use, and the reason
/// parity is checked against what a widget *describes* rather than against the
/// widget ([ADR-0116], [ADR-0109]).
///
/// A part in every other respect: it is not registered for markup, because
/// `select-field` is not a node anybody writes.
///
/// ## It reports where it is, and does not move
///
/// [Located], because the list opens **under this node** and a popup is placed
/// against a rectangle in the window's coordinates — which no widget can compute
/// and only the painted frame knows ([ADR-0119]). Anchoring by `id` was the other
/// way and it is worse here: a `select` that a document gave no `id` would have
/// to be given a generated one to be able to open itself, and two of them in one
/// window would then depend on that generation being unique.
///
/// The rule [Located] carries — a widget told where it is must not move itself —
/// holds trivially: this node does nothing at all with the rectangle, and hands
/// it to the state, which uses it only when something is clicked.
///
/// @param text        the chosen option's label, or the placeholder. Ignored
///                    when `chips` is non-empty — see [#children()]
/// @param placeholder whether `text` is the placeholder
/// @param chips       §3's "`badge` chips inside the closed control", one per
///                    value a `select multiple` holds, or empty for the ordinary
///                    single-valued control
/// @param editor      §3's `autocomplete=#true`: the editable `text-input` that
///                    replaces the value, or null for a control you cannot type
///                    in
/// @param open        whether the list is showing, which is `.open` to a
///                    stylesheet
/// @param disabled    whether it refuses to open and matches `:disabled`
/// @param attributes  the `id` and classes the document wrote on the `select`
/// @param onToggle    what a click, `Space` or `Alt+Down` does
/// @param onTypeahead what a printed character means — §3's typeahead
/// @param onRestore   §3's `Esc`: put the last committed value back
/// @param onSettle    the keyboard left — decide what a typed value meant
/// @param onLocated   where the last frame put this, and what clips it
record SelectField(
        String text, boolean placeholder, List<Widget> chips, Widget editor,
        boolean open, boolean disabled, Attributes attributes,
        Runnable onToggle, Consumer<String> onTypeahead,
        Runnable onRestore, Runnable onSettle,
        BiConsumer<LogicalRect, LogicalRect> onLocated)
        implements Widget.Leaf, Styled, Paints, Handles, Located {

    @Override
    public String cssType() {
        return "select";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    /// The document's classes, plus `open` while the list is showing and
    /// `multiple` while it is holding chips — both classes rather than
    /// pseudo-classes, see below.
    ///
    /// A class and not a pseudo-class: §8's subset has none meaning "expanded",
    /// and one invented for a single widget would be a language nobody else can
    /// read. `.open` is also what an application can already write a rule
    /// against, which a private pseudo-class would not be (ADR-0141).
    @Override
    public Set<String> classes() {
        if (!open && chips.isEmpty()) {
            return attributes.classes();
        }
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        if (open) {
            all.add("open");
        }
        // Only when it is *showing* chips, so the rule that lets a row of them
        // wrap costs nothing on the control that never has any.
        if (!chips.isEmpty()) {
            all.add("multiple");
        }
        return all;
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// **Not a Tab stop when it holds an editor**, which is the whole of §3's
    /// "one Tab stop": the `text-input` inside is the focusable thing, and a
    /// field that was also focusable would make a combobox two stops where a
    /// document wrote one control.
    @Override
    public boolean isFocusable() {
        return !disabled && editor == null;
    }

    /// ...and a press on the field's own chrome — its padding, its chevron —
    /// hands the keyboard to the editor inside, which is what makes the whole
    /// plate behave like the one control it looks like.
    ///
    /// `field`'s mechanism, reached for the same reason: the thing that takes the
    /// press is a *sibling* of the thing that should end up focused
    /// ([ADR-0170]).
    @Override
    public boolean delegatesFocus() {
        return editor != null;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        onLocated.accept(self, clip);
    }

    /// Opens or closes the list on a click anywhere in the field.
    ///
    /// **An editable field opens rather than toggling.** A click in a combobox is
    /// a user putting the caret somewhere, and closing the list under them
    /// because it happened to be open would take the choices away mid-gesture —
    /// where a plain `select` has nothing else a click could mean. Nor is it
    /// consumed: the editor underneath needs the same click to place its caret.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.CLICKED) {
            return;
        }
        if (editor != null) {
            // **Nothing.** The press that produced this click already focused the
            // editor, and focus is what opens an editable control — so a click
            // that also toggled would arrive *after* the list was open and read
            // `open` from the description built before it, see false, and shut it
            // again. Two paths opening one list is how a control opens and closes
            // in a single gesture ([ADR-0188]).
            return;
        }
        toggle();
        event.consume();
    }

    /// §3's "keyboard open (Space/Alt+Down)", with the bare arrows as well.
    ///
    /// `Down` and `Up` open too, because every dropdown on every desktop does and
    /// a user reaching for the list does not think of `Alt` as part of it. They
    /// are consumed only when the field is closed, so the arrows belong to the
    /// list once it is showing.
    ///
    /// **`Enter` deliberately does not open one** — the line every control in this
    /// catalog draws, for the same reason: `Enter` belongs to a dialog's default
    /// action, and a form where it opened a dropdown instead of submitting would
    /// be a form nobody can finish from the keyboard.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat() || disabled) {
            return;
        }
        var plain = event.modifiers().none();
        var alt = event.modifiers().only(io.github.digitalsmile.goldberry.input.key.Mod.ALT);
        var opens = switch (event.key()) {
            // **Not `Space` in an editable field**, where a space is a character.
            // §3 lists `Space` as a way to open a *closed* control, and a
            // combobox is not one.
            case SPACE -> plain && editor == null;
            case DOWN, UP -> (plain || alt) && !open;
            default -> false;
        };
        if (opens) {
            toggle();
            event.consume();
            return;
        }
        // §3: "`Esc` restores the last committed value rather than clearing" —
        // the sentence that tells a combobox apart from a search box. On the
        // **bubble** phase, so the editor inside keeps whatever it wanted first,
        // and only for an editable control: a plain `select`'s `Esc` belongs to
        // the popup, which is already watching for it.
        if (editor != null && event.key() == io.github.digitalsmile.goldberry.input.key.Key.ESCAPE
                && plain) {
            onRestore.run();
            event.consume();
        }
    }

    /// The keyboard left this control — the moment a half-typed value stops
    /// being an attempt and starts being an answer (§3's `free`).
    ///
    /// `onFocusWithin` and not `onFocusChanged`, because the thing that has the
    /// keyboard is the editor *inside* this node: focus moving from the editor to
    /// the next control is what has to be heard, and this node never had it to
    /// lose ([ADR-0169]'s notification, in its third consumer).
    @Override
    public void onFocusWithin(boolean within, boolean fromKeyboard) {
        if (editor == null) {
            return;
        }
        if (within) {
            // **Opened on focus, not on the click.** The editor consumes the
            // press to place its caret, so a click that reached the plate could
            // not be relied on to arrive — and a combobox the user is inside with
            // no options showing is a text box that has forgotten what it is
            // ([ADR-0185]). Focus is the honest signal: it is what the click, the
            // Tab and `Alt+Down` all produce.
            if (!open) {
                onToggle.run();
            }
            return;
        }
        onSettle.run();
    }

    /// §3's typeahead: what the user typed, handed up to the state, which is the
    /// only thing that knows what the options are.
    ///
    /// [TextEvent] and not [KeyEvent] because this wants what was *typed* rather
    /// than what was pressed — one character can take several keys, and a
    /// dropdown of French cities has to answer to a dead key like everything else
    /// (§7.1).
    @Override
    public void onText(TextEvent event) {
        if (disabled || event.text().isEmpty()) {
            return;
        }
        onTypeahead.accept(event.text());
        event.consume();
    }

    /// The value, or the chips, and then the chevron.
    ///
    /// **The chips replace the value rather than joining it.** A `select
    /// multiple` showing "Two selected" *and* two chips would be saying the same
    /// thing twice in a control §3 already calls narrow; and a multiple with
    /// nothing chosen falls back to the placeholder, which is the one thing the
    /// chips cannot say.
    @Override
    public List<Widget> children() {
        var parts = new java.util.ArrayList<Widget>(3);
        if (!chips.isEmpty()) {
            // One box holding all of them, rather than the chips as siblings of
            // the chevron. That box is what wraps -- a *field* that wrapped would
            // drop the chevron onto a second line under the chips, which is what
            // it did until a golden image showed it (ADR-0192) -- and it grows,
            // which is the job the spacer here used to do.
            parts.add(new SelectChips(chips));
        }
        if (editor != null) {
            // §3: "makes the closed control an editable `text-input`" — literally
            // one, rather than an editor this control grows of its own. The
            // editing model, the undo history, the clipboard and the caret are
            // `text-input`'s and stay there.
            parts.add(editor);
        } else if (chips.isEmpty()) {
            parts.add(new SelectValue(text, placeholder));
        }
        parts.add(new SelectChevron());
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        // A row, and the stylesheet says so: the value grows, the chevron does
        // not, and both are metrics that live in `controls.css` with §3's row.
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    private void toggle() {
        if (!disabled) {
            onToggle.run();
        }
    }
}
