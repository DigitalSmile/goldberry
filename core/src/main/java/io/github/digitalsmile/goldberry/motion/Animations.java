package io.github.digitalsmile.goldberry.motion;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.cascade.Transitions;
import io.github.digitalsmile.goldberry.css.cascade.Transitions.Animatable;

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
/// (ADR-0052).
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

    /// What resolves a `@keyframes` block for the node these animations belong
    /// to — the renderer, which has the cascade (ADR-0353).
    @FunctionalInterface
    public interface KeyframeSource {

        /// The block called `name`, resolved against `target`, or null when no
        /// stylesheet declares one.
        @Nullable
        KeyframeTrack track(String name, ComputedStyle target);
    }

    /// When each keyframe animation this node runs was first applied, by name, on
    /// the frame clock. The start of an animation's timeline is the frame its name
    /// appeared in the node's style, and it is kept for as long as the name stays
    /// there, so a later rule that changes only its delay does not restart it.
    private final Map<String, Double> keyframeStarts = new LinkedHashMap<>();

    /// The blocks resolved against [#tracksTarget], by name.
    private final Map<String, KeyframeTrack> tracks = new HashMap<>();

    /// The style [#tracks] were resolved against, by identity. A new style is a
    /// new resolution, since a keyframe's `var()` may mean something else now.
    private @Nullable ComputedStyle tracksTarget;

    /// Whether any keyframe animation was waiting or running on the last frame.
    private boolean keyframesRunning;

    /// A node that has not animated anything yet.
    ///
    /// Built by the element that owns it, and by nothing else: an `Animations`
    /// detached from an element would hold a transition nobody paints.
    public Animations() {}

    /// The style the cascade resolved last frame, which is what a change is
    /// measured against. Null until the first frame.
    private @Nullable ComputedStyle previous;

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
            var to = Animatables.valueOf(target, property);
            var from = currentOr(property, Animatables.valueOf(before, property), now);
            if (Animatables.sameValue(property, from, to)) {
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
            var value = Animatables.interpolate(property, animation.from(), animation.to(), animation.progressAt(now));
            styled = Animatables.withValue(styled, property, value);
        }
        return styled;
    }

    /// `base` with every keyframe animation `target` names applied at `now`
    /// (ADR-0353).
    ///
    /// Beneath transitions, which is CSS's order: a transition's in-flight value
    /// is applied over this result by [#apply], so a control whose hover colour
    /// is moving shows the move even while a keyframe animation names the same
    /// property.
    ///
    /// @param target the style the cascade resolved, which names the animations
    ///               and is what their keyframes are resolved against
    /// @param base   what to write the animated values onto, usually `target`
    /// @param source resolves a block by name, for this node
    public ComputedStyle animate(ComputedStyle target, ComputedStyle base, double now, KeyframeSource source) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(source, "source");
        var declared = target.animations();
        if (declared.isEmpty()) {
            keyframeStarts.clear();
            tracks.clear();
            tracksTarget = null;
            keyframesRunning = false;
            return base;
        }
        if (tracksTarget != target) {
            tracks.clear();
            tracksTarget = target;
        }
        var entries = declared.entries();
        var names = new HashSet<String>();
        var styled = base;
        var anyRunning = false;
        for (var entry : entries) {
            names.add(entry.name());
            var start = keyframeStarts.computeIfAbsent(entry.name(), name -> now);
            var elapsed = now - start;
            anyRunning |= KeyframeTrack.isRunning(entry, elapsed);
            var progress = KeyframeTrack.progress(entry, elapsed);
            if (progress == null) {
                continue;
            }
            var track = tracks.get(entry.name());
            if (track == null) {
                track = source.track(entry.name(), target);
                if (track == null) {
                    continue;
                }
                tracks.put(entry.name(), track);
            }
            styled = track.apply(styled, progress, entry.easing());
        }
        keyframeStarts.keySet().retainAll(names);
        tracks.keySet().retainAll(names);
        keyframesRunning = anyRunning;
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
        return !running.isEmpty() || keyframesRunning;
    }

    /// Whether anything is in flight — a transition, or a keyframe animation
    /// waiting out its delay or running.
    public boolean isAnimating() {
        return !running.isEmpty() || keyframesRunning;
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
        return Animatables.interpolate(property, animation.from(), animation.to(), animation.progressAt(now));
    }
}
