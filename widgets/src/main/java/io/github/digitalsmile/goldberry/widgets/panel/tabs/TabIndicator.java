package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The underline on a selected [Tab] — a **part**, and a box rather than a
/// border.
///
/// §8's CSS subset has one `border` and no per-edge longhands, which
/// ADR-0097
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
/// starts nothing (ADR-0065).
///
/// ## How it travels
///
/// §3.1 gives `tabs` and `segmented` the same effect, and `segmented`'s pill
/// moves between cells while this used to fade in and out in place. ADR-0097
/// deferred the travelling version for want of "a fact about two boxes'
/// laid-out geometry"; that fact arrives now, because every header reports where
/// it was painted ([ADR-0372]) and the strip keeps the rectangles.
///
/// What the strip hands over is not a position but a **difference**: where the
/// underline *was*, relative to where it is now. So this box is drawn at the
/// newly selected header — its own, correct, resting place — displaced back onto
/// the old one, and then the displacement is taken away and the box slides home.
/// Nothing here needs to know where either header is on the screen, which is why
/// a strip painted with no geometry behind it (a plain `BoxPainter.paint` of a
/// tree, which is what half the golden images are) still draws exactly what it
/// drew before ([ADR-0377]).
///
/// Both halves of the displacement are in one `transform` — a `translateX` for
/// the distance and a `scaleX` for the difference in width, about the top-left
/// corner — because `transition` takes the compositor-cheap set only, and a
/// `left` or a `width` that animated would run Yoga on every frame of the
/// travel.
///
/// @param selected whether this tab is the selected one
/// @param colour   the tab's own colour, or 0 for the stylesheet's
/// @param from     the journey this underline is on, or null when it is on none
///                 — a strip painted for the first time, a selection nothing
///                 preceded, or a strip whose headers nothing has measured
record TabIndicator(boolean selected, int colour, Tab.@Nullable Travel from) implements Widget.Leaf, Styled, Paints {

    /// The shape an indicator had before it could travel.
    TabIndicator(boolean selected, int colour) {
        this(selected, colour, null);
    }

    /// Across the bottom, and nothing about the top: the header's height is the
    /// header's.
    private static final Insets PINNED =
            new Insets(Length.UNDEFINED, Length.points(0), Length.points(0), Length.points(0));

    @Override
    public String cssType() {
        return "tab-indicator";
    }

    /// The journey's number, so that a displacement arrives on a **new** element
    /// — whose first frame starts nothing (ADR-0065) — and the frame that takes
    /// the displacement away is a change to the *same* element, and therefore a
    /// transition.
    ///
    /// Null while nothing is travelling, which is a strip that has never changed
    /// its selection.
    @Override
    public Object key() {
        return from == null ? null : from.id();
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

    /// The tab's own colour, when it has one, and the displacement it is sliding
    /// out of.
    ///
    /// Both in `restyle` rather than in `render`: a value written here is part of
    /// what the animation observes, so it moves under the `transition`
    /// `controls.css` declares. The same value written in `render` would arrive
    /// after the observation and snap (ADR-0099's seam).
    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        var styled = colour == 0 || !selected ? resolved : resolved.background(colour);
        if (from == null) {
            return styled;
        }
        return styled.transform(Transform.of(
                        new Transform.Function.Translate(Transform.Length.px(from.dx()), Transform.Length.ZERO),
                        new Transform.Function.Scale(from.scale(), 1))
                .origin(Transform.Origin.TOP_LEFT));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Drawn once displaced, and then asked to be let go of. Here rather than
        // in the build, because this is the moment the frame the displacement is
        // drawn on actually exists: the strip marks itself for a rebuild and the
        // next frame is where the underline starts moving (ADR-0052's deferred
        // rebuild, which is how a tab's departure ends too).
        if (from != null && from.isDisplaced() && from.arrived() != null) {
            from.arrived().run();
        }
        return Box.of().style(style).position(Position.ABSOLUTE).inset(PINNED);
    }
}
