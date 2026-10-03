package dev.goldberry.widgets.panel.card;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A raised surface: a `panel` whose stylesheet says it sits above the page.
///
/// ```kdl
/// card {
///     text class="title" "Disk usage"
///     text "72% of 500 GB"
/// }
/// ```
///
/// ```java
/// new Card(new Text("Disk usage"), new Text("72% of 500 GB"));
/// ```
///
/// ## The elevation is an edge and a shadow
///
/// A card is raised two ways at once. It has a `box-shadow` at the design
/// system's first elevation level, from the `--gb-elevation-1/-2/-3` tokens that
/// both themes define, and it has an edge: a brighter surface and a stronger
/// border than the page it sits on. `class="interactive"` lifts it to level 2
/// under the pointer, with the blur and the offset animating along with the
/// alpha.
///
/// Both are there on purpose. A shadow says "this is nearer" by darkening what
/// is underneath; a border and a lift in tone say it by contrast. A card sitting
/// on *another* card is sitting on its own colour, where the shadow says almost
/// nothing and the edge says it exactly, which is why the Panels screen puts a
/// card inside a card.
///
/// ## Everything else about it is `panel`'s
///
/// A card owns no axis, no padding of its own and no content; it is a `panel`
/// whose stylesheet rules say "raised". Nothing here reads `class="interactive"`,
/// because a class is the stylesheet's business.
///
/// A card carries no title: that is `group-box`'s. It has no accessible name of
/// its own either, which would arrive with the accessibility bridge along with
/// every other widget's, and that bridge is on hold.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#card).
@Markup("card")
public record Card(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Card> {

    public Card(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    /// Written out so that the parameters taking null for a default can say so.
    public Card(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "card";
    }

    @Override
    public Card withAttributes(Attributes value) {
        return new Card(children, value);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Builds a `card` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Card(children, Attributes.of(node));
    }
}
