package io.github.digitalsmile.goldberry.widgets.controls.button;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;

/// A [Button] lifted out of layout and pinned to a window corner — §3's
/// `float=#true`, the floating action button.
///
/// ```kdl
/// button float=#true corner="bottom-end" icon="plus" name="New note" press="app.new"
/// ```
///
/// ```java
/// new Floated(new Button("", plus, this::newNote), Corner.BOTTOM_END)
/// ```
///
/// ## A class of placement
///
/// "It is a class of *placement*, not of appearance, so it composes with
/// every variant and shape above; a floating button that is not `circle` is
/// legal and unusual." So the button is still a `button` — `primary`,
/// `circle`, whatever it was written with, plus `float` for the stylesheet's
/// elevation — and what this wrapper does is put it in the window's overlay
/// layer rather than in the tree it was described in
/// ([io.github.digitalsmile.goldberry.Host#overlay]). In the tree it takes no
/// space: it builds nothing.
///
/// Stateful because an overlay is a handle that has to be given back: the
/// state attaches it on the first build, re-attaches it when the button
/// changes, and takes it down when the element unmounts (ADR-0347). Taking it
/// down is an exit rather than a cut: the button is sent out with `leaving` and
/// the overlay removed once `--gb-motion-fast` has passed (ADR-0355).
///
/// @param button what floats
/// @param corner where — `bottom-end` by default, §3's own
public record Floated(Button button, Corner corner) implements Widget.Stateful {

    public Floated {
        Objects.requireNonNull(button, "button");
        corner = corner == null ? Corner.BOTTOM_END : corner;
    }

    /// The button in §3's default corner.
    public Floated(Button button) {
        this(button, Corner.BOTTOM_END);
    }

    @Override
    public @Nullable Object key() {
        return button.key();
    }

    @Override
    public State<?> createState() {
        return new FloatedState();
    }

    /// `corner="bottom-end"`, or §3's default for anything it does not name.
    static Corner corner(@Nullable String name) {
        if (name == null) {
            return Corner.BOTTOM_END;
        }
        try {
            return Corner.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return Corner.BOTTOM_END;
        }
    }
}
