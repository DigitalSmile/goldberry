package io.github.digitalsmile.goldberry.widgets.controls.chip;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The × that takes a [Chip] away — a **part**, and not a Tab stop.
///
/// [io.github.digitalsmile.goldberry.widgets.panel.tabs.TabClose]'s shape and
/// [io.github.digitalsmile.goldberry.widgets.controls.select.SelectChip.SelectChipRemove]'s
/// argument: a chip is **one** Tab stop, and a focusable × per chip would make a
/// row of five filters ten stops where a document wrote five controls.
///
/// The keyboard reaches it from the chip itself — `Delete` or `Backspace` — so
/// nothing here is unreachable without a pointer, which is the condition that
/// makes "not focusable" a design rather than an omission.
///
/// @param onDismiss what to ask, or null on a disabled chip — the chip passes
///                  null rather than wrapping a no-op, so a disabled × cannot
///                  fire by a route that forgot to check
record ChipDismiss(Runnable onDismiss) implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    @Override
    public String cssType() {
        return "chip-dismiss";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    /// **Consumed**, so taking a chip off is not also choosing it.
    ///
    /// Consumed even when there is nothing to run, which is the disabled case:
    /// an event that fell through to the chip would press a control the document
    /// said was disabled, by way of the one child that did not check.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            if (onDismiss != null) {
                onDismiss.run();
            }
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), 1.5));
    }

    /// [Role#BUTTON] and a name, even though nothing can focus it —
    /// [io.github.digitalsmile.goldberry.widgets.panel.tabs.TabClose]'s answer to
    /// the same question.
    ///
    /// `SemanticsSweepTest`'s rule is that a widget which makes focusability its
    /// business has to be able to say what it is, and "not focusable" is a
    /// decision as much as its opposite: this is a pointer target that does
    /// something, and a bridge enumerating the chip's children should find a
    /// button rather than an unnamed box with a cross in it.
    @Override
    public Role role() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return "Dismiss";
    }
}
