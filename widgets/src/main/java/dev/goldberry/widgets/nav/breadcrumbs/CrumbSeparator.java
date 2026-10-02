package dev.goldberry.widgets.nav.breadcrumbs;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The `>` between two crumbs — a **part**, so it is CSS-selectable and not
/// constructible.
///
/// ## It is a mark and not a character
///
/// The separator is a chevron drawn in `--gb-text-muted`, **not a character**,
/// so it never joins the text run.
///
/// A `>` or a `/` typed between two labels is part of a paragraph: it shapes with
/// the words, it takes their colour, it wraps with them, and a screen reader
/// reads it aloud. A node of its own does none of those — it is muted
/// independently of the crumbs, it cannot be selected as text, and it carries no
/// accessible name.
///
/// [Box.Mark.Kind#CHEVRON_END] rather than Lucide's `chevron-right`, which that
/// constant's own documentation anticipated: an icon is a value an application
/// has to hold and hand in, and a separator is drawn between every pair of crumbs
/// on every frame. A mark is two strokes the painter already knows how to draw.
record CrumbSeparator() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "crumb-separator";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
    }
}
