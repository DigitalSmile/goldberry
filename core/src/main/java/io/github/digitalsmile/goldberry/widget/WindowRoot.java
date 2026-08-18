package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The window's own node in the widget tree: the application's root, and whatever
/// is floating over it.
///
/// Every tree the launcher builds has one of these at the top, whether or not
/// anything is floating — a node that appears when the first overlay is added
/// would re-parent the entire application, throwing away every element's state
/// and every animation in flight to show a toast.
///
/// ## What it is for
///
/// **The in-window overlay layer** (`docs/core-widgets.md` §7). An [Overlay] is
/// pinned to a [Corner] out of flow, so it floats over the content without taking
/// space from it, and it is a **sibling of the application's root** rather than a
/// descendant — which is the whole point. A widget deep in the tree cannot pin
/// itself to the window's corner: an absolute box is placed against its own
/// parent, so the furthest it can reach is whatever panel it happens to be in.
///
/// It is deliberately *only* that. What a subtree can ask about the window it is
/// in — the frame rate, for one — travels down [Paints.Context] instead, where
/// the frame clock and the reduced-motion flag already are: those are facts about
/// the frame being rendered, and a node that had to walk to the root to find one
/// would be walking past the renderer that knows it.
///
/// ## Why the overlays arrive through a binding
///
/// `Host.overlay(...)` is called at any time, from a handler or from
/// `Application#start`, and the root widget of an [ElementTree] cannot be
/// swapped. So the list is a [Property] the launcher owns and this widget
/// **watches** — §9's `bind`, applied to the toolkit's own state. The element
/// subscribes for as long as it lives and a change marks it for rebuild, which is
/// the same route an application's model takes to the screen (ADR-0062). Nothing
/// here needs a `setState` or a second invalidation path.
///
/// ## Not a catalog widget
///
/// `window-root` is CSS-selectable and **not** KDL-constructible — a stated
/// exception to §11's parity invariant, on the same grounds a part is one
/// (ADR-0065): a document cannot write the node it is the document *of*. It is
/// selectable because it is the element `:root` matches and the one place a
/// stylesheet can put the window's own background.
///
/// @param content  the application's root widget
/// @param overlays what is floating over it, watched rather than captured
public record WindowRoot(Widget content, Property<List<Overlay>> overlays)
        implements Widget.Leaf, Styled, Paints {

    public WindowRoot {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(overlays, "overlays");
    }

    /// A root with nothing floating over it — for a test that wants the node
    /// without the launcher.
    public static WindowRoot of(Widget content) {
        return new WindowRoot(content, Property.of(List.of()));
    }

    @Override
    public String cssType() {
        return "window-root";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// The overlay list, so a change to it rebuilds this node (ADR-0062).
    @Override
    public Observable<?> binding() {
        return overlays;
    }

    /// The content **first**, so everything floating is painted after it.
    ///
    /// A box tree has no z-order beyond document order (ADR-0053), which is what
    /// makes an overlay layer a matter of list position rather than of a new
    /// concept.
    @Override
    public List<Widget> children() {
        var entries = overlays.get();
        if (entries == null || entries.isEmpty()) {
            return List.of(content);
        }
        var children = new ArrayList<Widget>(entries.size() + 1);
        children.add(content);
        for (var entry : entries) {
            children.add(entry.widget());
        }
        return List.copyOf(children);
    }

    /// Content fills the window; every overlay is pinned out of flow.
    ///
    /// `grow` on the content and nothing else: the root node is laid out at
    /// the frame's size, so a single growing child in a stretching row *is* the
    /// window. An absolute child takes no part in that, which is why adding a HUD
    /// cannot move a pixel of the application under it.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (children.isEmpty()) {
            return Box.of().style(style);
        }
        var entries = overlays.get();
        var boxes = new ArrayList<Box>(children.size());
        boxes.add(children.getFirst().grow(1));
        for (var i = 1; i < children.size(); i++) {
            var entry = entries.get(i - 1);
            boxes.add(children.get(i)
                    .position(PositionType.ABSOLUTE)
                    .inset(entry.corner().insets(entry.margin())));
        }
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
