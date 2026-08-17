package io.github.digitalsmile.goldberry.motion;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.css.Transitions;
import io.github.digitalsmile.goldberry.css.Transitions.Animatable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/// One node's running transitions — `docs/design-system.md` §1.7.
///
/// ## The overlay, and why it is never written back
///
/// §1.7: "animated values live in a per-node **animation overlay** applied at
/// paint time, never written back into computed style, so style recomputation and
/// animation can't fight."
///
/// That sentence is the whole design. The cascade resolves a node's *target*
/// style every frame from the stylesheets and the node's current pseudo-classes;
/// this holds where each animating property has actually got to. [#apply] returns
/// a style with the in-flight values substituted, and the target is what the next
/// frame diffs against. If the animated value were written back, the next
/// cascade would see the halfway colour as the node's real one, diff *that*
/// against the target, and start a second transition from it — a control that
/// approached its hover colour asymptotically and never arrived.
///
/// ## Retargeting starts from the current value
///
/// §1.7: "retargeting mid-flight starts from the *current animated value* —
/// values never jump." A pointer that leaves a button 40 ms into a 100 ms hover
/// fade must return from where the colour actually is, not from the full hover
/// colour it never reached. So a new transition for a property already in flight
/// takes the interpolated value as its start.
///
/// ## Lifetime
///
/// One of these per element, living on the element, so it survives the rebuilds
/// that replace the widget describing it
/// ([ADR-0052](../../../../../../book/src/adr/0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)).
/// A transition that outlived its element would animate something nobody can see;
/// one that died with a *widget* would restart on every `setState`.
///
/// Confined to the UI thread, like everything else in the frame path.
public final class Animations {

    /// One property, in flight.
    ///
    /// ## Why the value is an `Object`
    ///
    /// It was a `double` while every animatable property was a number or a colour
    /// — and a colour is a number, because a `double` holds every 32-bit integer
    /// exactly, so `0xAARRGGBB` round-trips through one. `transform` is the first
    /// that is not: it is a list of functions, and there is no encoding of one in
    /// a `double` that is not a lie.
    ///
    /// The alternative was a second map, keyed by the same enum, holding
    /// transforms — and then `observe`, `apply`, `settle` and `currentOr` each
    /// grow a second half that must stay in step with the first. One map with a
    /// boxed value and a dispatch at the two points that read it is less code and
    /// has one place to be wrong. The boxing costs an allocation per property per
    /// frame *while something is moving*, which is a handful of objects on a
    /// frame that is already rasterizing.
    ///
    /// @param from        the value it started at — the *current animated* value
    ///                    when this replaced an earlier transition
    /// @param to          the target the cascade resolved
    /// @param startMillis when it began, on the frame clock
    private record Running(Object from, Object to, double startMillis, Transitions.Timing timing) {

        /// Where this is at `now`, in `0..1` of eased progress.
        double progressAt(double now) {
            var elapsed = now - startMillis - Math.max(0, timing.delayMillis());
            if (elapsed <= 0) {
                return 0;
            }
            if (timing.isInstant() || elapsed >= timing.durationMillis()) {
                return 1;
            }
            return timing.easing().at(elapsed / timing.durationMillis());
        }

        boolean isDoneAt(double now) {
            return now - startMillis >= timing.totalMillis();
        }
    }

    private final Map<Animatable, Running> running = new EnumMap<>(Animatable.class);

    /// A node that has not animated anything yet.
    ///
    /// Built by the element that owns it, and by nothing else: an `Animations`
    /// detached from an element would hold a transition nobody paints.
    public Animations() {
    }

    /// The style the cascade resolved last frame, which is what a change is
    /// measured against. Null until the first frame.
    private ComputedStyle previous;

    /// Notes what the cascade resolved for this node and starts whatever moved.
    ///
    /// Called once per node per frame, **before** the node is rendered.
    ///
    /// The first frame starts nothing: there is no previous style, so nothing
    /// changed. That is what stops a window fading every control in from black
    /// when it opens — a control appearing is not a control changing, and §1.7's
    /// enter/exit animations belong to overlays, which announce themselves.
    ///
    /// @param target the style the cascade just produced
    /// @param now    the frame's timestamp, read once for the whole frame
    public void observe(ComputedStyle target, double now) {
        Objects.requireNonNull(target, "target");
        var before = previous;
        previous = target;
        if (before == null) {
            return;
        }

        var transitions = target.transitions();
        for (var property : Animatable.values()) {
            var timing = transitions.get(property);
            if (timing == null) {
                // No longer declared: stop rather than finish. A rule that
                // removed its own transition means the author wants the value
                // now, and continuing would animate against a stylesheet that
                // no longer asks for it.
                running.remove(property);
                continue;
            }
            var to = valueOf(target, property);
            var from = currentOr(property, valueOf(before, property), now);
            if (sameValue(property, from, to)) {
                // Already there, or never left. Clearing here is what makes a
                // finished transition stop costing a frame.
                if (running.containsKey(property) && running.get(property).to().equals(to)) {
                    continue;
                }
                running.remove(property);
                continue;
            }
            if (timing.isInstant()) {
                // Reduced motion, or a zero duration. The value snaps and no
                // frame is requested for it.
                running.remove(property);
                continue;
            }
            var current = running.get(property);
            if (current != null && current.to().equals(to)) {
                // Already heading there. Restarting would reset the clock every
                // frame and the value would never arrive.
                continue;
            }
            running.put(property, new Running(from, to, now, timing));
        }
    }

    /// `target` with every in-flight value substituted.
    ///
    /// The result is what gets painted; `target` is what the next frame diffs
    /// against. Returns `target` itself when nothing is running, so a static tree
    /// allocates nothing.
    public ComputedStyle apply(ComputedStyle target, double now) {
        Objects.requireNonNull(target, "target");
        if (running.isEmpty()) {
            return target;
        }
        var styled = target;
        for (var entry : running.entrySet()) {
            var property = entry.getKey();
            var animation = entry.getValue();
            var value = interpolate(
                    property, animation.from(), animation.to(), animation.progressAt(now));
            styled = withValue(styled, property, value);
        }
        return styled;
    }

    /// Drops whatever has finished, and says whether anything is still moving.
    ///
    /// Called after [#apply] so that the **last** frame of a transition is
    /// painted at its target value before it is forgotten. Clearing first would
    /// leave the final frame drawn from the cascade's value — the same number, in
    /// every case that matters, and a needless difference in the one where a
    /// rule changed in the same frame the transition ended.
    ///
    /// @return whether this node needs another frame
    public boolean settle(double now) {
        running.entrySet().removeIf(entry -> entry.getValue().isDoneAt(now));
        return !running.isEmpty();
    }

    /// Whether anything is in flight.
    public boolean isAnimating() {
        return !running.isEmpty();
    }

    /// How many properties are moving — diagnostics, and what a test asserts
    /// when it wants to know a transition really started.
    public int runningCount() {
        return running.size();
    }

    /// The value a property is at right now, or `fallback` if it is not running.
    private Object currentOr(Animatable property, Object fallback, double now) {
        var animation = running.get(property);
        if (animation == null) {
            return fallback;
        }
        // The retarget rule: a reversal starts from where the value actually is.
        return interpolate(property, animation.from(), animation.to(), animation.progressAt(now));
    }

    /// A property's value read off a style.
    ///
    /// Colours are carried as their `0xAARRGGBB` bits in a `Double`, which is
    /// exact — a double holds every 32-bit integer — so the four numeric
    /// properties share one representation. `transform` is the [Transform]
    /// itself; see [Running] for why that is worth a boxed value.
    private static Object valueOf(ComputedStyle style, Animatable property) {
        return switch (property) {
            case OPACITY -> style.opacity();
            case BACKGROUND_COLOR -> (double) style.background();
            case BORDER_COLOR -> (double) style.decoration().borderColor();
            case COLOR -> (double) style.color();
            case TRANSFORM -> style.transform();
        };
    }

    private static ComputedStyle withValue(ComputedStyle style, Animatable property, Object value) {
        return switch (property) {
            case OPACITY -> style.opacity((Double) value);
            case BACKGROUND_COLOR -> style.background(argb(value));
            case BORDER_COLOR -> style.decoration(style.decoration().borderColor(argb(value)));
            case COLOR -> style.color(argb(value));
            case TRANSFORM -> style.transform((Transform) value);
        };
    }

    private static int argb(Object value) {
        return (int) Math.round((Double) value);
    }

    private static boolean sameValue(Animatable property, Object a, Object b) {
        // Opacity is the one that arrives from arithmetic rather than from a
        // literal -- `45%` of an inherited value -- so two "equal" opacities can
        // differ in the last bit and a transition would restart every frame.
        if (property == Animatable.OPACITY) {
            return Math.abs((Double) a - (Double) b) < 1e-6;
        }
        return a.equals(b);
    }

    /// Where a property is at eased progress `t`.
    ///
    /// Numbers move linearly; colours move through **OKLCH**, because the sRGB
    /// midpoint of two saturated colours is a muddy grey that is neither of them
    /// (§1.7, and the reason the space is specified rather than left to the
    /// implementation); a transform moves function by function, which is what
    /// makes halfway between `rotate(0)` and `rotate(180deg)` a rotation rather
    /// than a collapsed box.
    private static Object interpolate(Animatable property, Object from, Object to, double t) {
        return switch (property) {
            case OPACITY -> (Double) from + ((Double) to - (Double) from) * t;
            case BACKGROUND_COLOR, BORDER_COLOR, COLOR ->
                    (double) io.github.digitalsmile.goldberry.css.CssColor.mix(
                            argb(from), argb(to), t);
            case TRANSFORM -> ((Transform) from).mix((Transform) to, t);
        };
    }
}
