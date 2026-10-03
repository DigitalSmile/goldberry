package dev.goldberry.widgets.controls.progressbar;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A bar that reports a value out of a maximum, or that something is happening
/// and nobody can say how much is left. Nothing here is focusable, nothing takes
/// a pointer, and there is no value to raise: it reports.
///
/// ```kdl
/// progress value=0.4
/// progress max=100 bind="download.received"
/// progress indeterminate=#true
/// ```
///
/// In Java, `new Progress(0.4)` for a fraction, `Progress.of(max, observable)`
/// for a bar that follows a property, and `Progress.sweeping()` for one with no
/// value. `value=` is the written value, `max=` is what a full bar is (1 by
/// default, so a fraction works), `bind=` names a `Number` to follow and
/// `indeterminate=#true` sweeps instead of filling.
///
/// ## Two widgets in one, and the pseudo-class says which
///
/// A determinate bar and an indeterminate one draw differently enough that the
/// obvious design is two widgets. They are one, because `:indeterminate` already
/// exists and already means exactly this: a control whose value is not a point
/// on its scale. [dev.goldberry.widgets.controls.checkbox.Checkbox] uses it for
/// its mixed state, the renderer mirrors it onto the element for free, and a
/// stylesheet reaches the two states with `progress-fill` and
/// `progress:indeterminate progress-fill`.
///
/// ## The determinate half places a value, and does not use a ratio to do it
///
/// [dev.goldberry.widgets.controls.slider.Slider] places its thumb by flex
/// ratio, because a percentage `translate` is a proportion of the *moving box*
/// and cannot place a thumb along a track. A progress bar has no thumb, so its
/// fill is simply `width: 40%` — the plain answer, available here and not there.
///
/// ## The indeterminate half is a function of the frame clock
///
/// The sweep loops every 1.2 s, linearly. A transition interpolates between two
/// styles the cascade resolved, and a sweep has no two styles — so it is drawn
/// from [Paints.Context#nowMillis()] instead, with **no state anywhere**: the
/// phase is `now mod 1200`, so two bars in one window sweep together and nothing
/// has to be started, stopped or disposed.
///
/// The sweep is a `transform`, which is what keeps it affordable: animating the
/// fill's *width* would run layout on every frame of a loop that never ends,
/// which is why `width` is not on the motion whitelist. Both halves of the
/// drawing live on [ProgressFill], because both are facts about that box.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#progress).
///
/// @param value         how far along, `0..max`; ignored when indeterminate
/// @param max           what `value` is out of; 1 by default, so a fraction works
/// @param indeterminate whether this reports progress it cannot measure
/// @param source        `bind=`, read-only — see [#resolved()]
@Markup("progress")
public record Progress(
        double value,
        double max,
        boolean indeterminate,
        @Nullable Observable<?> source,
        Attributes attributes) implements Widget.Leaf, Styled, Paints, Attributed<Progress>, Bindable<Progress> {

    /// The canonical constructor, written out so that the parameters taking null for a default can say so.
    public Progress(
            double value,
            double max,
            boolean indeterminate,
            @Nullable Observable<?> source,
            @Nullable Attributes attributes) {
        if (!Double.isFinite(max) || max <= 0) {
            throw new IllegalArgumentException("progress is out of a positive maximum, not " + max);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("progress needs a real value, not " + value);
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.value = value;
        this.max = max;
        this.indeterminate = indeterminate;
        this.source = source;
        this.attributes = attributes;
    }

    /// A fraction of one, which is what most callers have.
    public Progress(double value) {
        this(value, 1, false, null, Attributes.NONE);
    }

    /// A bar that follows a property.
    ///
    /// Named, because `new Progress(100, gain)` reads as "a bar at 100" and means
    /// "a bar whose maximum is 100" — the first parameter changes meaning between
    /// the two constructors and nothing at the call site says so.
    public static Progress of(double max, Observable<?> source) {
        return new Progress(0, max, false, source, Attributes.NONE);
    }

    /// A bar for work whose size is unknown — the indeterminate form.
    ///
    /// Named `sweeping` rather than `indeterminate` because a record component
    /// already owns that name, and an accessor and a factory cannot share one.
    /// Which is a fair reading anyway: what an application is choosing here is
    /// the drawing, not a fact about its own knowledge.
    public static Progress sweeping() {
        return new Progress(0, 1, true, null, Attributes.NONE);
    }

    /// How far along this is, `0..1` — the bound value if there is one, clamped.
    ///
    /// Any `Number`, and anything else reads as [#value()], which is the rule
    /// [dev.goldberry.widgets.controls.slider.Slider#resolved()] follows.
    public double resolved() {
        var raw = source == null ? value : source.get() instanceof Number number ? number.doubleValue() : value;
        return Math.clamp(raw / max, 0, 1);
    }

    @Override
    public Progress bound(Observable<?> source) {
        return new Progress(value, max, indeterminate, source, attributes);
    }

    @Override
    public Progress withAttributes(Attributes attributes) {
        return new Progress(value, max, indeterminate, source, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "progress";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
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
    /// picture, and the frame loop must be allowed to go back to sleep in front
    /// of one.
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

    /// Builds a `progress` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Progress(
                node.numberProperty("value", 0),
                node.numberProperty("max", 1),
                node.booleanProperty("indeterminate"),
                wiring.bound(node),
                Attributes.of(node));
    }
}
