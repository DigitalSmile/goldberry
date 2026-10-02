package dev.goldberry.widget.root;

import java.util.List;

import dev.goldberry.Overlay;
import dev.goldberry.widget.Widget;

/// One overlay's place among [WindowRoot]'s children: the overlay's widget,
/// keyed by **the overlay itself**.
///
/// The reconciler matches a child by its class and its key. An overlay's widget
/// is the application's value, and two of them are often alike: every dialog
/// that arrives without an id is given the same one, and the same `Toaster`
/// may be shown twice. Matched that way, a dialog removed and a new one shown
/// in the same turn would be the same node, and the new dialog would inherit
/// the old one's state, its closing animation already over. A window with a
/// scrim nobody can see is a window nobody can click.
///
/// The [Overlay] handle is the one thing that is new each time something is
/// put on the window, and it has identity rather than value equality. So each
/// overlay's widget sits under one of these, keyed by its handle, and an
/// element never passes from one overlay to another.
///
/// It is neither styled nor painted, so it adds no box and no selector sees
/// it: the renderer hands its child's boxes straight to the root. It is also how
/// a widget finds the overlay it is in — see [WindowRoot#overlayOf].
///
/// @param overlay the overlay this is the place of
record OverlaySlot(Overlay overlay) implements Widget.Leaf {

    @Override
    public Object key() {
        return overlay;
    }

    @Override
    public List<Widget> children() {
        return List.of(overlay.widget());
    }
}
