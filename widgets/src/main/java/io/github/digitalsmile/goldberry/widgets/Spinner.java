package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.List;
import java.util.Set;

/// Something is happening — `docs/core-widgets.md` §3's `spinner`, "a small
/// indeterminate activity indicator". The eighth control, and the smallest thing
/// in the catalog: no value, no state, no input, no children.
///
/// ```kdl
/// spinner
/// ```
///
/// ## It is a ring with a gap, drawn by the painter
///
/// The obvious implementation is an icon, and it is the wrong one twice over: an
/// [io.github.digitalsmile.goldberry.icon.Icon] owns native memory and a widget
/// is a value rebuilt every frame, so a spinner holding one would leak a ring per
/// reload — the argument [Button]'s borrowed icon makes — and it would put the
/// toolkit's own spinner behind an asset an application has to register.
///
/// So it is a `Box.Mark`, like a checkbox's tick and a radio's dot, and the arc
/// behind it is **three cubics through the already-exported `bl_path_cubic_to`**.
/// No symbol was added to the export list, which is the rule
/// [ADR-0064](../../../../../../../book/src/adr/0064-a-rounded-rectangle-is-four-cubics.md)
/// set when a rounded corner turned out to be four of them.
///
/// Three quarters rather than a whole circle because **a spinning circle is a
/// circle**: the gap is the entire reason the rotation is visible.
///
/// ## The rotation is the frame clock, and nothing else
///
/// §3.1: "rotation 900ms `linear` loop". There is no controller, no start, no
/// stop and no state — the angle is `(now mod 900) / 900` of a turn, so a row of
/// spinners is in step by construction and one that unmounts leaves nothing
/// behind ([ADR-0081](../../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)).
///
/// Reduced motion replaces the rotation with §3.1's opacity pulse, which is the
/// stylesheet's: this widget simply stops turning.
public record Spinner(Widgets.Attributes attributes) implements Widget.Leaf, Styled, Paints {

    /// §3.1's "rotation **900ms** linear loop", in milliseconds.
    private static final double PERIOD = 900;

    /// The ring's stroke, in logical pixels.
    ///
    /// §3 pins no metric for a spinner at all — it is not in the component table
    /// — so this is Lucide's own 2px stroke at 24px, which §1.6 already makes the
    /// toolkit's line weight for anything drawn on that grid. Inventing a third
    /// number would be inventing a scale the design system does not have.
    private static final double THICKNESS = 2;

    public Spinner {
        attributes = attributes == null ? Widgets.Attributes.NONE : attributes;
    }

    public Spinner() {
        this(Widgets.Attributes.NONE);
    }

    @Override
    public String cssType() {
        return "spinner";
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

    /// Always. A spinner that stopped asking for frames would be a picture of a
    /// spinner, and §1.7's idle loop would leave it there — which is exactly what
    /// an application should be able to see when it forgets to unmount one.
    @Override
    public boolean isAnimating() {
        return true;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.ARC, style.color(), THICKNESS))
                .transform(angleAt(context));
    }

    /// A rotation about the box's centre, which is `transform-origin`'s default
    /// and the only origin a ring has any use for.
    private Transform angleAt(Context context) {
        if (context.reducedMotion()) {
            return Transform.NONE;
        }
        return Transform.of(new Transform.Function.Rotate(turnAt(context.nowMillis()) * 2 * Math.PI));
    }

    /// How far round the loop `now` is, `0..1` — see [ProgressFill#phaseAt].
    static double turnAt(double now) {
        var phase = (now % PERIOD) / PERIOD;
        return phase < 0 ? phase + 1 : phase;
    }
}
