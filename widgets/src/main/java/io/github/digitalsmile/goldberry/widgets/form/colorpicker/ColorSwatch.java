package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A block of colour — `color-swatch`, a **part**, and both of the two §2 gives
/// metrics for.
///
/// > `color-picker` | swatch 24, radius 4; … preset swatch 20, gap 4
///
/// The two differ in size, in what they are for, and in whether the keyboard can
/// reach them, so [Kind] is a class on one type rather than two records that
/// would be the same eight lines apart from a number.
///
/// ## Only the value swatch takes the focus
///
/// §4 calls the closed control "a swatch **button**", so it is one: focusable,
/// `Role.BUTTON`, and `Space` or `Enter` opens the popover. The presets are not,
/// and that is a decision rather than an omission — a palette of twelve colours
/// would be twelve Tab stops inside a popover, and §4 gives them no roving
/// mechanism to be one stop with. The keyboard's route to any colour is the hex
/// field, which is the source of truth anyway; the presets are a pointer's
/// shortcut past it.
///
/// The colour is a **`background`** written by [#restyle] rather than a fill this
/// draws, which is ADR-0099's seam: a value a stylesheet cannot know, written
/// where a transition can still see it. It is what lets the closed control's
/// swatch fade between colours rather than jump.
///
/// @param kind    which of §2's two swatches this is
/// @param argb    what to show
/// @param onPress told `argb` when it is chosen, or null when it does nothing
record ColorSwatch(Kind kind, int argb, @Nullable IntConsumer onPress)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// Which of §2's two swatches — see the class note.
    enum Kind {

        /// The closed control: 24 points, focusable, and what opens the popover.
        VALUE,

        /// One of the application's presets: 20 points, and a pointer's shortcut.
        PRESET
    }

    @Override
    public String cssType() {
        return "color-swatch";
    }

    @Override
    public Set<String> classes() {
        return kind == Kind.PRESET ? Set.of("preset") : Set.of();
    }

    @Override
    public boolean isFocusable() {
        return kind == Kind.VALUE && onPress != null;
    }

    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        return resolved.background(argb);
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (onPress != null && event.kind() == PointerEvent.Kind.CLICKED) {
            onPress.accept(argb);
            event.consume();
        }
    }

    /// `Space` and `Enter` on the value swatch, which is what makes "a swatch
    /// button" a button for a keyboard as well as for a pointer.
    @Override
    public void onKey(KeyEvent event) {
        if (!isFocusable()
                || event.kind() != KeyEvent.Kind.PRESSED
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            if (onPress != null) {
                onPress.accept(argb);
            }
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    /// The hex, which is §4's "the hex as its value text" — the one half of that
    /// sentence there is anywhere to put, since [Semantics] has no value channel.
    /// A swatch's *name* is what it is, and a colour has no other one the toolkit
    /// could honestly produce.
    @Override
    public String accessibleName() {
        return HsvColor.hex(argb);
    }
}
