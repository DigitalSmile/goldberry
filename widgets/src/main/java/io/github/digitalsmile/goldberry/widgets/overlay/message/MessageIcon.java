package io.github.digitalsmile.goldberry.widgets.overlay.message;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The glyph at the head of a [Message] — a **part**, so it is styleable and not
/// constructible
/// (ADR-0065).
///
/// It draws the kind's [Box.Mark], not an icon, and the difference is lifetime:
/// an `Icon` owns native memory that has to be closed exactly once, and a banner
/// is described afresh on every build. `tab-close` made the same choice for the
/// same reason.
///
/// **Its size is the stylesheet's** — a mark does not size the box it is on — so
/// `docs/design-system.md` §2's "icon 20 with gap 12" is two declarations in
/// `controls.css` rather than two numbers here. A theme that wants a bigger glyph
/// changes one of them and the drawing follows, because every mark's geometry is
/// a proportion of its box.
///
/// The stroke is 1.5 logical pixels, which is the weight `tab-close` and
/// `select`'s chevron already draw at: Lucide's own 2px is against a 24px box,
/// and this glyph is 20.
record MessageIcon(Message.Kind kind) implements Widget.Leaf, Styled, Paints {

    /// Lucide's 2px in a 24 box, at 20 — see the class note.
    private static final double STROKE = 1.5;

    @Override
    public String cssType() {
        return "message-icon";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(kind.glyph(), style.color(), STROKE));
    }
}
