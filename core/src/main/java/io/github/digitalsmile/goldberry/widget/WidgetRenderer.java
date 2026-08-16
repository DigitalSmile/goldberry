package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Turns a built element tree into a box tree, styling every node on the way.
///
/// The join that makes the last six ADRs one thing: the element tree gives nodes
/// identity ([ADR-0052]), the cascade resolves each one's style
/// ([ADR-0049](../../../../../../book/src/adr/0049-the-css-engine-stops-at-computedstyle.md)),
/// and `BoxPainter` rasterizes what comes out.
///
/// Not every element renders. A [Widget.Stateless] exists to describe others and
/// produces nothing itself, so the renderer passes through it — which is why the
/// box tree is shallower than the element tree, and why a composition wrapper
/// costs nothing at paint time.
public final class WidgetRenderer {

    private final StyleResolver resolver;
    private final CssLength.Context lengths;
    private final Paints.Context paintContext;

    /// @param stylesheets in any order; the cascade decides by layer, not by
    ///                    the order they are handed over
    /// @param font        what text is shaped with
    public WidgetRenderer(List<Stylesheet> stylesheets, Font font) {
        this(stylesheets, font, CssLength.Context.DEFAULT);
    }

    public WidgetRenderer(List<Stylesheet> stylesheets, Font font, CssLength.Context lengths) {
        this.resolver = new StyleResolver(Objects.requireNonNull(stylesheets, "stylesheets"));
        this.lengths = Objects.requireNonNull(lengths, "lengths");
        Objects.requireNonNull(font, "font");
        this.paintContext = () -> font;
    }

    /// Renders a whole tree.
    ///
    /// @throws IllegalStateException if the tree describes nothing that paints —
    ///         a root of pure composition with no primitive under it is almost
    ///         certainly a mistake, and an empty window is a poor way to report it
    public Box render(ElementTree tree) {
        Objects.requireNonNull(tree, "tree");
        var boxes = render(tree.root());
        if (boxes.isEmpty()) {
            throw new IllegalStateException(
                    "nothing in this widget tree paints; the root described only composition");
        }
        if (boxes.size() == 1) {
            return boxes.getFirst();
        }
        // A root that described several siblings needs something to hold them.
        return Box.of().children(boxes.toArray(Box[]::new));
    }

    /// The boxes one element contributes — one if it paints, otherwise its
    /// children's.
    private List<Box> render(Element element) {
        var children = new ArrayList<Box>();
        for (var child : element.children()) {
            children.addAll(render(child));
        }

        // The one pseudo-class a widget owns rather than the router: `:disabled`
        // is a fact about the description, so it is mirrored onto the element
        // here, before the cascade is asked. Every other state on an element was
        // put there by input.
        if (element.widget() instanceof Styled styled) {
            element.setPseudoClass(
                    io.github.digitalsmile.goldberry.css.Selector.PseudoClass.DISABLED,
                    styled.isDisabled());
        }

        if (!(element.widget() instanceof Paints paints)) {
            // A composition node: it has no box of its own, so its children
            // become its parent's directly.
            return children;
        }

        var style = ComputedStyle.of(resolver.resolve(element), lengths);
        // Tagged with the element that produced it, which is how a pointer
        // event gets from a rectangle on screen back to a node (ADR-0054).
        return List.of(paints.render(style, List.copyOf(children), paintContext).owner(element));
    }
}
