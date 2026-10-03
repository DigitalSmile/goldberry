package dev.goldberry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.goldberry.bind.Property;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.root.WindowRoot;
import dev.goldberry.widget.style.Corner;

/// The list of what floats over a window's content, and the door overlays go
/// on and off it by.
///
/// ```java
/// var layer = new OverlayLayer();
/// var tree = new ElementTree(new WindowRoot(content, layer.overlays()), host);
/// var veil = layer.fill(new Veil()); // drawn over `content` from the next build
/// veil.remove();                     // and gone again
/// ```
///
/// A [WindowRoot] reads the list and draws it; this is the half that writes
/// it. Something that hosts a tree without a launcher — an offscreen session,
/// a test's own host — needs both halves, and an [Overlay] handed back by
/// anything else would be **detached**: its `remove()` does nothing, so a
/// dialog that closes itself would stay on screen.
///
/// Each change sets a fresh list rather than changing the one in the
/// property: the root element is subscribed to the *value*, and a list changed
/// in place is the same value, so nothing would rebuild. Overlays are told
/// apart by identity, since two identical HUDs in one corner are two things on
/// screen.
///
/// Confined to the UI thread, like the tree it is drawn in.
///
/// Read more: [Overlays and popups](https://goldberry.dev/docs/guide/windows.html#overlays-and-popups).
public final class OverlayLayer {

    private final Property<List<Overlay>> overlays = Property.of(List.of());

    private final Runnable changed;

    /// A layer that tells nobody when it changes. The tree reading it still
    /// rebuilds; what is not asked for is a frame.
    public OverlayLayer() {
        this(() -> {});
    }

    /// A layer that runs `changed` after every add and every removal — what a
    /// window does to ask for the frame that shows it.
    public OverlayLayer(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /// What a [WindowRoot] watches.
    public Property<List<Overlay>> overlays() {
        return overlays;
    }

    /// What is on the layer now, bottom first.
    public List<Overlay> current() {
        return list();
    }

    /// Puts `entry` on top of the layer and returns it, attached:
    /// [Overlay#remove()] takes it off again.
    ///
    /// @throws IllegalStateException if it is already on a layer — this one or
    ///         another — since one overlay drawn twice has one handle to
    ///         remove it with
    public Overlay add(Overlay entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isAttached()) {
            throw new IllegalStateException(entry + " is already on a layer");
        }
        var next = new ArrayList<>(list());
        next.add(entry);
        overlays.set(List.copyOf(next));
        entry.attached(() -> {
            var remaining = new ArrayList<Overlay>(list().size());
            for (var existing : list()) {
                if (existing != entry) {
                    remaining.add(existing);
                }
            }
            overlays.set(List.copyOf(remaining));
            changed.run();
        });
        changed.run();
        return entry;
    }

    /// [Host#fill(Widget)] on this layer.
    public Overlay fill(Widget widget) {
        return add(Overlay.filling(widget));
    }

    /// [Host#overlay(Widget, Corner, float)] on this layer.
    public Overlay overlay(Widget widget, Corner corner, float margin) {
        return add(Overlay.of(widget, corner, margin));
    }

    private List<Overlay> list() {
        // Never null, for WindowRoot.children's reason: the property starts at
        // List.of() and is only ever set to a copy. IntelliJ reads Property's
        // nullable type bound rather than the non-null List it is declared with.
        //noinspection DataFlowIssue
        return overlays.get();
    }
}
