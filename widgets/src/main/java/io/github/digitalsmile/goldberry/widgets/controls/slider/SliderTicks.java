package io.github.digitalsmile.goldberry.widgets.controls.slider;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/// The row of marks under a slider's groove — a **part** of [Slider], and the
/// eleventh. `docs/core-widgets.md` §3's "optional tick marks".
///
/// The marks are spread by `justify-content: space-between`, so the stylesheet
/// owns the spacing exactly as it owns everything else about them. What Java
/// supplies is the **count**, and one number more than that: each mark is wrapped
/// in a **zero-sized cell** it overflows out of, centred.
///
/// That wrapper is the whole trick, and it is there because a mark with a width
/// shifts every mark after it. Spread five 2px marks across the free space and
/// their centres land at `i × (C − 2)/4 + 1` rather than at `i × C/4`: the first
/// is a pixel right of the start, the last a pixel left of the end, and each of
/// them a pixel away from the thumb centre it is supposed to name. A cell of zero
/// width takes no part in that arithmetic, so the marks sit exactly where the
/// ratio says and the mark's own size is free to be whatever the theme wants
/// ([ADR-0080](../../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
///
/// The cell is zero on **both** axes rather than on the main one, which is what
/// keeps this widget from having to know which axis it is on: a fader flips the
/// row to a column in the stylesheet (`slider.vertical`), and a 0×0 cell is
/// already right in either. The widget names the semantics and the stylesheet
/// names the axis — ADR-0079's rule, applied to the part that would otherwise
/// have needed a `vertical` flag of its own.
///
/// @param count    how many marks, both ends included; at least two
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderTicks(int count, boolean disabled) implements Widget.Leaf, Styled, Paints {

    private static final StyleLength ZERO = StyleLength.points(0);

    SliderTicks {
        if (count < 2) {
            throw new IllegalArgumentException(
                    "a scale is at least its two ends, not " + count + " marks");
        }
    }

    @Override
    public String cssType() {
        return "slider-ticks";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public List<Widget> children() {
        return IntStream.range(0, count)
                .<Widget>mapToObj(index -> new SliderTick(disabled))
                .toList();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The cells are boxes and not widgets: they carry no style, match no
        // selector and mean nothing to an author, so an element apiece would be
        // a node in three trees for a number this method already knows. A part
        // is what an author can restyle (ADR-0065), and there is nothing here to
        // restyle.
        var cells = new ArrayList<Box>(children.size());
        for (var mark : children) {
            cells.add(Box.of()
                    .size(ZERO, ZERO)
                    .justifyContent(Justify.CENTER)
                    .alignItems(Align.CENTER)
                    .children(mark));
        }
        return Box.of().style(style).children(cells.toArray(Box[]::new));
    }
}
