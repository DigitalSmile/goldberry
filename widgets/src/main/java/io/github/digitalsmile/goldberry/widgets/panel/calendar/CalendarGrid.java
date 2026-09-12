package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// The six weeks — `calendar-grid`, a **part**, and where §3.1's month change
/// happens.
///
/// > `calendar` | month change: content `opacity` cross-fade fast — **never a
/// > slide**, because the grid is the same shape and sliding it implies the days
/// > moved
///
/// ## A cross-fade is two months, and one of them is out of flow
///
/// A single grid dipping to transparent and back is a *dissolve to the
/// background* — the surface behind shows through at the halfway point, which on
/// a `date-picker`'s popover reads as the popover blinking. A cross-fade needs
/// both months on screen at once, so the **incoming** month is in flow and sizes
/// this box while the **outgoing** one is absolutely positioned over it at the
/// complementary opacity. Only one of the two can size the box, and it has to be
/// the one that is staying.
///
/// Each month is one [CalendarMonthLayer] rather than six loose weeks, so the
/// pinning is `inset: 0 0 auto 0` on a single node instead of a row height
/// multiplied by an index — a measurement `render` would have had to guess at.
///
/// That absolute child lands where it should because
/// [io.github.digitalsmile.goldberry.paint.tree.ContainingBlock] shifts it by this
/// box's padding (ADR-0272).
///
/// **`fast`, not `base`** — §3.1 says so, and a month change is a change of
/// content rather than something entering the layout. It is a constant here for
/// [Phase]'s stated reason: a clock-driven animation cannot read a `transition`
/// declaration, because it is not one.
///
/// @param weeks    the month arriving, six [CalendarWeek]s
/// @param outgoing the month leaving, or null when nothing is changing
/// @param phase    how far through the change, or null when nothing is changing
record CalendarGrid(
        List<Widget> weeks,
        @Nullable List<Widget> outgoing,
        @Nullable Phase phase) implements Widget.Leaf, Styled, Paints {

    /// §3.1's `fast`, which is `--gb-motion-fast`'s 100ms.
    static final double CROSS_FADE_MILLIS = 100;

    /// Pinned across the top of the grid, and left to its own height.
    private static final Insets OVER =
            new Insets(Length.points(0), Length.points(0), Length.UNDEFINED, Length.points(0));

    CalendarGrid {
        weeks = List.copyOf(weeks);
        outgoing = outgoing == null ? null : List.copyOf(outgoing);
    }

    @Override
    public String cssType() {
        return "calendar-grid";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var layers = new ArrayList<Widget>(2);
        layers.add(new CalendarMonthLayer(weeks));
        if (outgoing != null) {
            layers.add(new CalendarMonthLayer(outgoing));
        }
        return List.copyOf(layers);
    }

    /// Whether a month change is still being drawn — what keeps the frame loop
    /// awake for the length of it and lets it sleep again afterwards.
    @Override
    public boolean isAnimating() {
        return phase != null && phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (outgoing == null || children.size() < 2) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
        var progress = phase == null ? 1 : phase.progressAt(context.nowMillis());
        return Box.of()
                .style(style)
                .children(
                        children.getFirst().opacity(progress),
                        children.get(1)
                                .opacity(1 - progress)
                                .position(Position.ABSOLUTE)
                                .inset(OVER));
    }
}
