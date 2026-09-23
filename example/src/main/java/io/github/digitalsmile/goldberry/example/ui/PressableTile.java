package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;

/// A tile on a sheet that **opens** something when it is pressed — the icon
/// and emoji tiles, which open a dialog of the glyph at five sizes.
///
/// The whole of "pressable" in one place, because the two tiles press exactly
/// alike and are different records for a reason that has nothing to do with
/// pressing (see [EmojiTile]). A default method per event is the contract
/// `button` keeps, written once:
///
/// - a **click** opens, and is consumed so nothing under the tile also acts;
/// - `Space` and `Enter` open, §3's rule for everything you press — which is
///   what makes the sheet usable without a pointer at all;
/// - the tile is **focusable**, so Tab walks the tiles a reader can see, and it
///   announces itself as a [Role#BUTTON] under its name.
///
/// A record implements this by having an `onOpen` component; the accessor is
/// the method below.
interface PressableTile extends Handles, Semantics {

    /// What pressing the tile does.
    Runnable onOpen();

    @Override
    default void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            onOpen().run();
            event.consume();
        }
    }

    @Override
    default void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            onOpen().run();
            event.consume();
        }
    }

    @Override
    default boolean isFocusable() {
        return true;
    }

    /// A button: pressing it makes something happen, and it has no state of its
    /// own to report afterwards.
    @Override
    default Role role() {
        return Role.BUTTON;
    }
}
