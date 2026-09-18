package io.github.digitalsmile.goldberry.widget.root;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.tree.ContainingBlock;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
public record WindowRoot(Widget content, Property<List<Overlay>> overlays) implements Widget.Leaf, Styled, Paints {

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
        for (var child : children) {
            var entry = overlayFor(child, entries);
            if (entry == null) {
                boxes.add(child.grow(1));
                continue;
            }
            // Insets on all four sides is Yoga's "fill"; two sides is a corner.
            // One flag, no second placement path (ADR-0121).
            var inset = entry.isFilling()
                    ? Insets.all(Length.points(0))
                    : entry.corner().insets(entry.margin());
            boxes.add(child.position(Position.ABSOLUTE)
                    // Against the **window**, not against the application's
                    // content box. An absolutely positioned child is placed
                    // inside its containing block's padding by default, which is
                    // CSS and is right for a child of the content — and this
                    // layer is not one. A `window-root { padding: 16px }` is the
                    // application saying where its own widgets start; a toast
                    // pinned 12 points from the corner means 12 from the corner
                    // of the window, and a veil that fills means the window
                    // (ADR-0272).
                    .inset(ContainingBlock.acrossBorderBox(inset, style.padding())));
        }
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Which overlay a box came from, or null when the application's content did.
    ///
    /// **Asked of the box, not of its position in the list.** A child widget and
    /// a child *box* are not the same count and never were: a hidden node
    /// contributes none ([ADR-0366]), so does [Widget#nothing()], and a
    /// composition contributes as many as the boxes it composes. Matching by
    /// index therefore placed a toast in the corner of the overlay above it the
    /// moment one of them drew nothing, and walked off the end of the list when
    /// one of them drew twice.
    ///
    /// A box carries the element that produced it — the tag hit testing gets from
    /// a rectangle back to a node (ADR-0054) — so the honest question is which of
    /// this node's own children that element is under, and the element tree
    /// answers it. Child 0 is the content, child *i* is overlay *i-1*, which is
    /// the order [#children()] builds them in.
    private @Nullable Overlay overlayFor(Box box, @Nullable List<Overlay> entries) {
        if (entries == null || entries.isEmpty() || !(box.owner() instanceof Element owner)) {
            return null;
        }
        // Up from whoever painted the box to this node's own child, which is
        // where the answer is: an overlay that is a composition is several
        // elements above the box it eventually produced.
        Element child = null;
        for (var node = owner; node != null; node = parentOf(node)) {
            if (node.widget() != this) {
                child = node;
                continue;
            }
            var index = child == null ? -1 : node.children().indexOf(child);
            return index >= 1 && index <= entries.size() ? entries.get(index - 1) : null;
        }
        return null;
    }

    private static @Nullable Element parentOf(Element element) {
        return element.parent() instanceof Element parent ? parent : null;
    }
}
