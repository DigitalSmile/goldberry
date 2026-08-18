package io.github.digitalsmile.goldberry.widgets.controls.segmented;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The filled pill that marks the selected segment — a **part** of [Segmented],
/// and the thing that moves.
///
/// ## Why the fill is one box that travels rather than one per segment
///
/// `docs/design-system.md` §3.1 asks for a "selection indicator `translate` +
/// width between segments". A fill per segment, faded in and out, is the same
/// picture at rest and a different thing entirely in motion: two segments dim
/// and brighten in place, and nothing moves. One box that translates is what the
/// row describes, and it is only expressible because the segments are a **grid**
/// — every one of them exactly `1/n` of the track, so the distance to segment
/// *k* is `k` times this box's own width and needs no measurement at all
/// ([ADR-0099](../../../../../../../../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
///
/// [ADR-0097](../../../../../../../../book/src/adr/0097-a-selection-that-travels-needs-a-geometry.md)
/// deferred this on the grounds that a `translate` "would have to name the
/// distance from the segment being left to the one being arrived at — a fact
/// about two boxes' laid-out geometry". On a grid it is not: a percentage in a
/// `transform` is a proportion of the box's *own* border box, which the painter
/// resolves after Yoga has run ([ADR-0068]). What was missing was never the
/// geometry.
///
/// ## Both of its numbers come from a count, so both come from Java
///
/// A stylesheet cannot say "one fifth", because it cannot count the segments.
/// [#restyle] is where the widget says it, and it is deliberately *not*
/// `render`: a value written here is part of what the animation observes, so the
/// translation moves under the `transition` `controls.css` declares. The same
/// value written in `render` would arrive after the observation and snap.
///
/// @param index the selected segment, or **-1** when the bar's value matches
///              none — which is a real state (a model that has not loaded) and
///              is drawn as no pill at all
/// @param count how many segments there are, which is the grid
record SegmentedIndicator(int index, int count) implements Widget.Leaf, Styled, Paints {

    /// Pinned to the track's top and bottom, and to its left — the horizontal
    /// place is the transform's, because that is the half that has to move.
    private static final Insets PINNED = new Insets(
            StyleLength.points(0), StyleLength.UNDEFINED,
            StyleLength.points(0), StyleLength.points(0));

    SegmentedIndicator {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "an indicator over " + count + " segments has nothing to point at;"
                            + " SegmentedTrack builds one only when there are segments");
        }
    }

    @Override
    public String cssType() {
        return "segmented-indicator";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// Mirrored to `:checked`, which is how the stylesheet fades the pill away
    /// when the bar's value matches no segment.
    ///
    /// It is **always built**, in every state, for `radio-dot`'s reason: a node
    /// that only exists while something is selected cannot transition, and the
    /// first frame of a newly built element starts nothing ([ADR-0065]).
    @Override
    public boolean isChecked() {
        return index >= 0;
    }

    /// The two numbers a selector cannot write: the width of one cell, and how
    /// many cells along this one sits.
    ///
    /// Both are proportions rather than lengths, so neither needs to know how
    /// wide the bar is. The width is of the **track**, which is the containing
    /// block; the translation is of this box's **own** width, which is CSS's
    /// rule for a percentage in a transform and the reason one number does for
    /// every bar at every size.
    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        return resolved
                .width(StyleLength.percent((float) (100.0 / count)))
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.percent(100.0 * Math.max(index, 0)),
                        Transform.Length.ZERO)));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Out of flow, which is the whole of what makes it an indicator rather
        // than a fourth segment: in flow it would take a share of the row and
        // push the labels along. Pinned in Java rather than in `controls.css`
        // for `row`'s reason -- a stylesheet that could put this back in flow
        // would break the widget rather than restyle it.
        return Box.of().style(style)
                .position(PositionType.ABSOLUTE)
                .inset(PINNED);
    }
}
