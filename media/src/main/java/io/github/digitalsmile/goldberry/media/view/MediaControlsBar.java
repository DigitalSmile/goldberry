package io.github.digitalsmile.goldberry.media.view;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet means by `media-controls`: the row of transport
/// controls, and where the media keys arrive ([Transport]).
///
/// What [MediaControls] and [AudioPlayer] build, and [MediaPlayerView] lays over
/// its picture. A row, as `row` is, and **focusable**, so that clicking between
/// the controls, or tabbing to the group, gives the keyboard to the player; a
/// key pressed on a control inside bubbles here when the control does not want
/// it.
///
/// @param controls the play button, times, seek bar, mute and volume
/// @param id       the owning widget's id, when it is this node that carries it
/// @param classes  the classes the owning widget puts on it
/// @param onKey    what a key does: the owner's [Transport]
record MediaControlsBar(List<Widget> controls, @Nullable String id, Set<String> classes, Consumer<KeyEvent> onKey)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    MediaControlsBar {
        controls = List.copyOf(controls);
        classes = Set.copyOf(classes);
    }

    @Override
    public String cssType() {
        return "media-controls";
    }

    @Override
    public Set<String> classes() {
        return classes;
    }

    @Override
    public List<Widget> children() {
        return controls;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onKey(KeyEvent event) {
        onKey.accept(event);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style).direction(FlexDirection.ROW);
    }

    @Override
    public Role role() {
        return Role.GROUP;
    }

    @Override
    public String accessibleName() {
        return "Media controls";
    }
}
