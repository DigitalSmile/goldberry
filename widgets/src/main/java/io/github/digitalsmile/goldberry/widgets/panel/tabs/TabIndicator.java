package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The underline on a selected [Tab] — a **part**, and a box rather than a
/// border.
///
/// §8's CSS subset has one `border` and no per-edge longhands, which
/// [ADR-0097](../../../../../../../../book/src/adr/0097-a-selection-that-travels-needs-a-geometry.md)
/// recorded when `segmented` wanted per-corner radii. A `border-bottom` is
/// therefore not a thing that can be written, and the first version of this
/// widget wrote one anyway: the declaration was dropped, and the golden image is
/// what said so — every number in the layout was correct and the underline was
/// simply not there.
///
/// So it is a 2px box pinned across the bottom of the header, out of flow, which
/// is `segmented-indicator`'s shape for the same reason.
///
/// **Always built**, selected or not: a node that only exists while a tab is
/// selected cannot transition, because the first frame of a newly built element
/// starts nothing ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// @param selected whether this tab is the selected one
/// @param colour   the tab's own colour, or 0 for the stylesheet's
record TabIndicator(boolean selected, int colour) implements Widget.Leaf, Styled, Paints {

    /// Across the bottom, and nothing about the top: the header's height is the
    /// header's.
    private static final Insets PINNED = new Insets(
            StyleLength.UNDEFINED, StyleLength.points(0),
            StyleLength.points(0), StyleLength.points(0));

    @Override
    public String cssType() {
        return "tab-indicator";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// Mirrored to `:checked`, which is how `controls.css` fades it in and out.
    @Override
    public boolean isChecked() {
        return selected;
    }

    /// The tab's own colour, when it has one — the value a stylesheet cannot
    /// know, written where a transition can still see it (ADR-0099's seam).
    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        return colour == 0 || !selected ? resolved : resolved.background(colour);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).position(PositionType.ABSOLUTE).inset(PINNED);
    }
}
