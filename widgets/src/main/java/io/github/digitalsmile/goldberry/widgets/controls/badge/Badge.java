package io.github.digitalsmile.goldberry.widgets.controls.badge;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Bindable;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// A count or a status — `docs/core-widgets.md` §3's `badge`, "count/status chip,
/// typically composed inside `stack`".
///
/// The ninth entry in §3's table and **the first one that is not a control**. It
/// is not focusable, holds no value, has no keyboard map, and matches none of
/// §2.1's states: §3 gives its semantics as `text`, and that is the whole of it.
/// It lives in `widget.controls` because that is where the catalog put it, not
/// because it behaves like its neighbours.
///
/// ```kdl
/// badge "3"
/// badge class="danger" "offline"
/// badge bind="inbox.unread"
/// ```
///
/// ## The variants are classes, and they are the aurora hues' first honest use
///
/// `docs/design-system.md` §1.2 lets the aurora hues appear "**only** with
/// semantic meaning (danger/warning/success/info […]) — never as decoration on
/// controls". Every widget shipped so far had to obey the *never* half; a status
/// chip is the first one whose entire job is the *only* half, so `badge.danger`
/// and `badge.success` are the point of the widget rather than a skin on it.
///
/// Classes rather than an enum, for §11's parity invariant: KDL spells a variant
/// `class="danger"`, and an enum would be a second vocabulary only Java could
/// use. [io.github.digitalsmile.goldberry.widgets.controls.button.Button#styled] made the same choice for the same reason.
///
/// **The foreground is not the theme's.** A filled chip in an aurora hue cannot
/// take `--gb-text`: white on `--gb-warning` is 1.35:1 against §1.2's 4.5:1
/// floor. Each variant therefore pins its own text token, and two of the hues
/// carry no legible text at either end of the palette and ship as a derived,
/// darker fill
/// ([ADR-0087](../../../../../../../../book/src/adr/0087-a-semantic-fill-brings-its-own-foreground.md)).
///
/// ## Nothing about it moves
///
/// §3.1 has no `badge` row, and its preamble is explicit that "anything not
/// listed does not animate". So `controls.css` declares no `transition` for it —
/// deliberately, and stated there rather than left to be noticed.
///
/// @param text       what the chip says; a count is already a string, because
///                   formatting one is the application's business and not a
///                   pattern this widget would have to validate
/// @param source     §9's `bind=`, or null — a count is the archetypal bound
///                   value, and an [Observable] rather than a property because a
///                   badge reports nothing back ([ADR-0063])
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("badge")
public record Badge(String text, Observable<?> source, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Badge>, Bindable<Badge> {

    public Badge {
        Objects.requireNonNull(text, "text");
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A chip with a literal, which is what a status wants.
    public Badge(String text) {
        this(text, null, Attributes.NONE);
    }

    /// A chip that follows a property. The Java spelling of `bind=`.
    ///
    /// The literal stays as the fallback rather than being refused, which is the
    /// rule [Text] set: a path nothing answers yet should show a designer
    /// something rather than nothing.
    public static Badge of(String fallback, Observable<?> source) {
        return new Badge(fallback, Objects.requireNonNull(source, "source"), Attributes.NONE);
    }


    /// What the chip says right now: the bound value, or the literal.
    ///
    /// `String.valueOf` rather than a `Number` check, because unlike a slider's
    /// binding this one has no numeric meaning to fall back to — §3 says "count
    /// **or status**", and a status is whatever the model calls it.
    public String resolved() {
        return source == null ? text : String.valueOf(source.get());
    }

    @Override
    public Badge bound(Observable<?> source) {
        return new Badge(text, source, attributes);
    }

    @Override
    public Badge withAttributes(Attributes attributes) {
        return new Badge(text, source, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "badge";
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
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The text is a **child box** and not text on the chip's own box, which
        // is [io.github.digitalsmile.goldberry.widgets.controls.button.Button]'s split and here it buys the one thing a pill needs: a box
        // that measures its own text is a measured leaf, and Yoga sizes a
        // measured node to its content, so the chip could not be given the
        // 20px height that makes `border-radius: 10px` a full radius. With the
        // text as a child, `align-items: center` centres it in a height the
        // stylesheet pins -- and it is still **one styled element**, because the
        // paragraph is built from the chip's own `style` rather than from a
        // second widget's (`SliderValue` takes the other branch, and says why).
        return Box.of().style(style)
                .children(Box.text(context.paragraph(style, resolved()), style.color()));
    }

    /// Builds a `badge` from markup.
    ///
    /// §3's only entry that is not a control: no action, no state, nothing to
    /// resolve against a registry. A count is the archetypal bound value, so
    /// `bind` is the one wiring it takes, and the literal argument stays as the
    /// fallback the way `text`'s does.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Badge(Wiring.label(node), wiring.bound(node), Attributes.of(node));
    }
}
