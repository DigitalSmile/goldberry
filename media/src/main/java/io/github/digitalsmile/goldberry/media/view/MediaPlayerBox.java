package io.github.digitalsmile.goldberry.media.view;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet means by `media-player`: the picture, with the overlay
/// laid over it (`docs/goldberry-media.md` §6).
///
/// Laid out as `stack` is: the first child (the `video-view`) stays in flow and
/// sizes the box, and everything after it is taken out of flow, where
/// `media.css` pins the overlay to the bottom edge.
///
/// It hears the pointer moving over it, which is what wakes controls that have
/// hidden themselves, and the pointer leaving, which hides them again while the
/// player plays. Focusable, and where a media key arrives when nothing inside
/// wanted it.
///
/// @param parts      the picture, then the overlay
/// @param id         the widget's id
/// @param classes    the widget's classes, with its state
/// @param onActivity told `true` when the pointer moves over the player, `false`
///                   when it leaves
/// @param onKey      what a key does: the owner's [Transport]
record MediaPlayerBox(
        List<Widget> parts,
        @Nullable String id,
        Set<String> classes,
        Consumer<Boolean> onActivity,
        Consumer<KeyEvent> onKey)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    MediaPlayerBox {
        parts = List.copyOf(parts);
        classes = Set.copyOf(classes);
    }

    @Override
    public String cssType() {
        return "media-player";
    }

    @Override
    public List<Widget> children() {
        return parts;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onPointer(PointerEvent event) {
        switch (event.kind()) {
            case ENTERED, MOVED, PRESSED -> onActivity.accept(true);
            case EXITED -> onActivity.accept(false);
            default -> {}
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        onKey.accept(event);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var laid = new Box[boxes.size()];
        for (var i = 0; i < boxes.size(); i++) {
            laid[i] = i == 0 ? boxes.get(i) : boxes.get(i).position(Position.ABSOLUTE);
        }
        return Box.of().children(laid).style(style);
    }

    @Override
    public Role role() {
        return Role.GROUP;
    }

    @Override
    public String accessibleName() {
        return "Media player";
    }
}
