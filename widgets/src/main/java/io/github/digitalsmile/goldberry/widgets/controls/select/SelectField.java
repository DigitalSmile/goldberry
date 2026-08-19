package io.github.digitalsmile.goldberry.widgets.controls.select;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Located;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.TextEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
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
/// @param text        the chosen option's label, or the placeholder
/// @param placeholder whether `text` is the placeholder
/// @param open        whether the list is showing, which is `.open` to a
///                    stylesheet
/// @param disabled    whether it refuses to open and matches `:disabled`
/// @param attributes  the `id` and classes the document wrote on the `select`
/// @param onToggle    what a click, `Space` or `Alt+Down` does
/// @param onTypeahead what a printed character means — §3's typeahead
/// @param onLocated   where the last frame put this, and what clips it
record SelectField(
        String text, boolean placeholder, boolean open, boolean disabled, Attributes attributes,
        Runnable onToggle, Consumer<String> onTypeahead,
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

    /// The document's classes, plus `open` while the list is showing.
    ///
    /// A class and not a pseudo-class: §8's subset has none meaning "expanded",
    /// and one invented for a single widget would be a language nobody else can
    /// read. `.open` is also what an application can already write a rule
    /// against, which a private pseudo-class would not be (ADR-0141).
    @Override
    public Set<String> classes() {
        if (!open) {
            return attributes.classes();
        }
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add("open");
        return all;
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
    public void located(LogicalRect self, LogicalRect clip) {
        onLocated.accept(self, clip);
    }

    /// Opens or closes the list on a click anywhere in the field.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            toggle();
            event.consume();
        }
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
        var alt = event.modifiers().only(io.github.digitalsmile.goldberry.input.Mod.ALT);
        var opens = switch (event.key()) {
            case SPACE -> plain;
            case DOWN, UP -> (plain || alt) && !open;
            default -> false;
        };
        if (opens) {
            toggle();
            event.consume();
        }
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

    @Override
    public List<Widget> children() {
        return List.of(new SelectValue(text, placeholder), new SelectChevron());
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
