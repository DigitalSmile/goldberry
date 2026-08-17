package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.List;
import java.util.Set;

/// How far along something is — `docs/core-widgets.md` §3's `progress`. The
/// seventh control, and the first that is not a control at all: nothing here is
/// focusable, nothing takes a pointer, and there is no value to raise. It reports.
///
/// ```kdl
/// progress value=0.4
/// progress indeterminate=#true
/// ```
///
/// ## Two widgets in one, and the pseudo-class says which
///
/// §3 asks for "determinate (`value/max`) and indeterminate (animated,
/// reduced-motion aware)", and those draw differently enough that the obvious
/// design is two widgets. They are one, because `:indeterminate` already exists
/// and already means exactly this: a control whose value is not a point on its
/// scale. [Checkbox] mirrors it for its mixed state
/// ([ADR-0065](../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)),
/// the renderer mirrors it onto the element for free, and a stylesheet reaches
/// the two states with `progress-fill` and `progress:indeterminate progress-fill`.
///
/// ## The determinate half places a value, and does not use a ratio to do it
///
/// [Slider] places its thumb by flex ratio because a percentage `translate` is a
/// proportion of the *moving box* and could not express it
/// ([ADR-0079](../../../../../../../book/src/adr/0079-a-continuous-value-is-placed-by-ratio.md)).
/// A progress bar has no thumb, so its fill is simply `width: 40%` — the plain
/// answer, available here and not there, and the difference between the two is
/// worth reading before assuming one control's technique belongs on the other.
///
/// ## The indeterminate half is a function of the frame clock
///
/// §3.1: "sweep loop 1.2s `linear`". A transition interpolates between two styles
/// the cascade resolved, and a sweep has no two styles — so it is drawn from
/// [Paints.Context#nowMillis()] instead, with **no state anywhere**: the phase is
/// `now mod 1200`, so two bars in one window sweep together and nothing has to be
/// started, stopped or disposed
/// ([ADR-0081](../../../../../../../book/src/adr/0081-a-perpetual-loop-has-no-state.md)).
///
/// The sweep is a `transform`, which is what keeps it affordable: animating the
/// fill's *width* would run Yoga on every frame of a loop that never ends, and
/// that is the thing §1.7's whitelist exists to prevent. Both halves of the
/// drawing live on [ProgressFill], because both are facts about that box.
///
/// @param value         how far along, `0..max`; ignored when indeterminate
/// @param max           what `value` is out of; 1 by default, so a fraction works
/// @param indeterminate whether this reports progress it cannot measure
/// @param source        §9's `bind`, read-only — see [#resolved()]
public record Progress(
        double value, double max, boolean indeterminate,
        Observable<?> source, Widgets.Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    public Progress {
        if (!Double.isFinite(max) || max <= 0) {
            throw new IllegalArgumentException("progress is out of a positive maximum, not " + max);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("progress needs a real value, not " + value);
        }
        attributes = attributes == null ? Widgets.Attributes.NONE : attributes;
    }

    /// A fraction of one, which is what most callers have.
    public Progress(double value) {
        this(value, 1, false, null, Widgets.Attributes.NONE);
    }

    /// A bar that follows a property.
    public Progress(double max, Observable<?> source) {
        this(0, max, false, source, Widgets.Attributes.NONE);
    }

    /// A bar for work whose size is unknown — §3's second half.
    ///
    /// Named `sweeping` rather than `indeterminate` because a record component
    /// already owns that name, and an accessor and a factory cannot share one.
    /// Which is a fair reading anyway: what an application is choosing here is
    /// the drawing, not a fact about its own knowledge.
    public static Progress sweeping() {
        return new Progress(0, 1, true, null, Widgets.Attributes.NONE);
    }

    /// How far along this is, `0..1` — the bound value if there is one, clamped.
    ///
    /// Any `Number`, and anything else reads as [#value()], which is the rule
    /// [Slider#resolved()] follows.
    public double resolved() {
        var raw = source == null ? value
                : source.get() instanceof Number number ? number.doubleValue() : value;
        return Math.clamp(raw / max, 0, 1);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "progress";
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

    /// What makes one widget two, and it costs nothing: the renderer mirrors this
    /// onto the element before the cascade runs, so `progress:indeterminate` is a
    /// selector an author already knows.
    @Override
    public boolean isIndeterminate() {
        return indeterminate;
    }

    /// Only while it sweeps. A bar that has been given a value is a still
    /// picture, and §1.7's frame loop must be allowed to go back to sleep in
    /// front of one.
    @Override
    public boolean isAnimating() {
        return indeterminate;
    }

    @Override
    public List<Widget> children() {
        return List.of(new ProgressFill(resolved(), indeterminate));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
