package io.github.digitalsmile.goldberry.widgets.panel.card;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

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
/// ## The elevation is an edge, and no longer because it has to be
///
/// §5 says "shadow tokens". When this was built §10's CSS subset had no
/// `box-shadow` and nothing painted outside a box's own rectangle, so `popover`
/// hit the wall first and answered it the same way
/// (ADR-0104):
/// **elevation is an edge**, a brighter surface and a stronger border than the
/// page it sits on.
///
/// The property exists now — `box-shadow`, with `--gb-elevation-1/-2/-3` in both
/// themes (ADR-0310) — and this card still does not use it, which is a decision
/// rather than an omission: adding one is a change to every golden in the
/// catalog that contains a card, and it belongs in its own change.
///
/// The edge is **not** going away when that happens, and it never was only a
/// workaround. A shadow says "this is nearer" by faking a light source onto what
/// is underneath; a border and a lift in tone say it by contrast. A card sitting
/// on *another card* is sitting on its own colour, where the first says almost
/// nothing and the second says it exactly. The tokens are the same pair `panel`
/// and `popover` already use, one step apart.
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
