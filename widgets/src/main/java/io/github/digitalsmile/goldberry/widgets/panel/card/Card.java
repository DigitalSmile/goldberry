package io.github.digitalsmile.goldberry.widgets.panel.card;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.util.List;
import java.util.Set;

/// A raised surface — `docs/core-widgets.md` §5's `card`, "elevated surface:
/// shadow tokens, hover-elevation optional via class".
///
/// ```kdl
/// card {
///     text class="title" "Disk usage"
///     text "72% of 500 GB"
/// }
/// ```
///
/// ## The elevation is not a shadow, because there are none
///
/// §5 says "shadow tokens" and §10's CSS subset has no `box-shadow` — the whole
/// supported property list is flex, box, colour, text, transform and transition,
/// and nothing paints outside a box's own rectangle. `popover` hit this first and
/// answered it the same way
/// ([ADR-0104](../../../../../../../../book/src/adr/0104-a-popup-is-measured-then-placed.md)):
/// **elevation is an edge**, a brighter surface and a stronger border than the
/// page it sits on.
///
/// That is not a workaround so much as the honest version of the same idea. A
/// shadow says "this is nearer" by faking a light source; a border and a lift in
/// tone say it by contrast, and contrast is what a raster with no shadow pass can
/// actually express. The tokens exist and it is the same pair `panel` and
/// `popover` already use, one step apart.
///
/// ## Everything else about it is `panel`'s
///
/// A card owns no axis, no padding of its own and no content — it is a `panel`
/// whose stylesheet rules say "raised". `class="interactive"` adds the hover
/// elevation §5 calls optional; nothing here reads it, because a class is the
/// stylesheet's business.
///
/// A card carries **no title**. §5 gives that to `group-box`, and the "group with
/// optional label" in this line is the *accessible* name, which arrives with the
/// AccessKit bridge in M5 along with every other widget's.
@Markup("card")
public record Card(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Card> {

    public Card(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    public Card {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
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
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// Builds a `card` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Card(children, Attributes.of(node));
    }
}
