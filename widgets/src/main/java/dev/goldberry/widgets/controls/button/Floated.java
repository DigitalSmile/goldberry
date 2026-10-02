package dev.goldberry.widgets.controls.button;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Corner;

/// A [Button] lifted out of layout and pinned to a window corner: the floating
/// action button, which markup writes as `float=#true`.
///
/// ```kdl
/// button float=#true corner="bottom-end" icon="plus" name="New note" press="app.new"
/// ```
///
/// ```java
/// new Floated(new Button("", plus, this::newNote), Corner.BOTTOM_END)
/// ```
///
/// Floating is placement, not appearance, so it composes with every variant and
/// shape: the button is still a `button` — `primary`, `circle`, whatever it was
/// written with, plus `float` for the stylesheet's elevation — and what this
/// wrapper does is put it in the window's overlay layer
/// ([dev.goldberry.Host#overlay]) rather than in the tree it was described in.
/// In the tree it takes no space: it builds nothing.
///
/// Stateful because an overlay is a handle that has to be given back: the
/// state attaches it on the first build, re-attaches it when the button
/// changes, and takes it down when the element unmounts. Taking it down is an
/// exit rather than a cut: the button is sent out with `leaving` and the
/// overlay removed once `--gb-motion-fast` has passed.
///
/// Read more: [Buttons, badges and chips](https://goldberry.dev/docs/components/buttons.html#button).
///
/// @param button what floats
/// @param corner where — `bottom-end` by default; an unknown name falls back
///               to it
public record Floated(Button button, Corner corner) implements Widget.Stateful {

    /// Written out so that the parameters taking null for a default can say so.
    public Floated(Button button, @Nullable Corner corner) {
        Objects.requireNonNull(button, "button");
        corner = corner == null ? Corner.BOTTOM_END : corner;
        this.button = button;
        this.corner = corner;
    }

    /// The button in the default corner, `bottom-end`.
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

    /// `corner="bottom-end"`, or the default for anything it does not name.
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
