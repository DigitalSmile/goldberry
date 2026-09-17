package io.github.digitalsmile.goldberry.motion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Keyframes;
import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations;
import io.github.digitalsmile.goldberry.css.cascade.Transitions.Animatable;
import io.github.digitalsmile.goldberry.css.parse.Token;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.log.Logs;

/// A `@keyframes` block resolved for one element: for each animatable property
/// it mentions, the value at each keyframe that mentions it (ADR-0353).
///
/// ## Resolved against the element, once per style
///
/// A keyframe's declarations are applied **on top of** the element's resolved
/// style, so `var()` means the element's token and `em` its own font size. Only
/// the properties the keyframe declares are read back out. A property a
/// keyframe does not mention is not in that keyframe's stops, which is how CSS
/// treats it: `from { opacity: 0 }` and nothing at `to` animates towards the
/// element's own opacity.
///
/// ## The whitelist
///
/// A keyframe may declare anything, and only the six
/// [Animatable] properties move, for §1.7's reason: a keyframed `width` is a
/// layout pass on every frame. The rest are dropped, and each is named once in
/// a warning per block and property, not once per frame.
public final class KeyframeTrack {

    private static final Logger LOG = Logs.of(KeyframeTrack.class);

    /// Blocks and properties already reported, so a stylesheet that keyframes
    /// `width` says so once rather than sixty times a second. Bounded the way
    /// the cascade's report sets are.
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static final int REPORT_LIMIT = 512;

    /// One property's value at one keyframe.
    private record Stop(double offset, Object value) {}

    private final Map<Animatable, List<Stop>> stops;

    private KeyframeTrack(Map<Animatable, List<Stop>> stops) {
        this.stops = stops;
    }

    /// Resolves `block` for an element whose style is `target`.
    ///
    /// @param declarations each keyframe's declarations with `var()` substituted
    ///                     for this element — `StyleResolver.resolveKeyframe`
    /// @param context      what `rem` resolves against
    public static KeyframeTrack resolve(
            Keyframes block,
            ComputedStyle target,
            Function<Keyframes.Frame, Map<String, List<Token>>> declarations,
            CssLength.Context context) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(context, "context");
        var stops = new EnumMap<Animatable, List<Stop>>(Animatable.class);
        for (var frame : block.frames()) {
            var declared = declarations.apply(frame);
            var styled = target.applied(declared, context);
            for (var property : declared.keySet()) {
                var animatable = Animatable.parse(property);
                if (animatable == null) {
                    report(block.name(), property);
                    continue;
                }
                stops.computeIfAbsent(animatable, key -> new ArrayList<>())
                        .add(new Stop(frame.offset(), Animatables.valueOf(styled, animatable)));
            }
        }
        return new KeyframeTrack(stops);
    }

    /// Whether this block moves nothing at all — every declaration in it was
    /// refused, or it has no keyframes.
    public boolean isEmpty() {
        return stops.isEmpty();
    }

    /// `base` with every property this track moves at `progress` through one
    /// iteration.
    ///
    /// The implicit 0% and 100% keyframes are `base`'s own values, and `easing`
    /// is applied **between each pair of keyframes** rather than across the whole
    /// iteration, which is CSS's rule and why a three-keyframe bounce eases into
    /// every stop.
    public ComputedStyle apply(ComputedStyle base, double progress, Easing easing) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(easing, "easing");
        var styled = base;
        for (var entry : stops.entrySet()) {
            var property = entry.getKey();
            var own = Animatables.valueOf(base, property);
            var value = valueAt(entry.getValue(), own, progress, easing, property);
            styled = Animatables.withValue(styled, property, value);
        }
        return styled;
    }

    private static Object valueAt(List<Stop> stops, Object own, double progress, Easing easing, Animatable property) {
        var from = new Stop(0, own);
        var to = new Stop(1, own);
        for (var stop : stops) {
            if (stop.offset() <= progress && stop.offset() >= from.offset()) {
                from = stop;
            }
        }
        for (var i = stops.size() - 1; i >= 0; i--) {
            var stop = stops.get(i);
            if (stop.offset() >= progress && stop.offset() <= to.offset() && stop.offset() >= from.offset()) {
                to = stop;
            }
        }
        var span = to.offset() - from.offset();
        if (span <= 0) {
            return from.value();
        }
        var local = easing.at((progress - from.offset()) / span);
        return Animatables.interpolate(property, from.value(), to.value(), local);
    }

    /// Where an animation is in its current iteration, from 0 to 1, `elapsed`
    /// milliseconds after it was first applied — or null when it shows nothing
    /// then (ADR-0353).
    ///
    /// CSS's timing model: the delay first, filled by the first frame only
    /// under `backwards` or `both`; then `iterations` passes of `duration`, each
    /// played backwards when the direction says so; then the last frame, held
    /// only under `forwards` or `both`.
    public static @Nullable Double progress(KeyframeAnimations.Entry entry, double elapsed) {
        Objects.requireNonNull(entry, "entry");
        var local = elapsed - entry.delayMillis();
        var fill = entry.fillMode();
        var direction = entry.direction();
        if (local < 0) {
            return fill.holdsStart() ? (direction.backwards(0) ? 1.0 : 0.0) : null;
        }
        var duration = entry.durationMillis();
        var iterations = entry.iterations();
        var active = duration <= 0 ? 0 : duration * iterations;
        if (local >= active) {
            if (!fill.holdsEnd()) {
                return null;
            }
            if (Double.isInfinite(iterations)) {
                return direction.backwards(0) ? 1.0 : 0.0;
            }
            var whole = Math.floor(iterations);
            var fraction = iterations - whole;
            // Ending exactly on an iteration boundary shows the end of the one
            // before it, not the start of one that never plays.
            var index = fraction == 0 ? Math.max(0, (long) whole - 1) : (long) whole;
            var at = fraction == 0 ? (iterations == 0 ? 0 : 1) : fraction;
            return direction.backwards(index) ? 1 - at : at;
        }
        var index = (long) Math.floor(local / duration);
        var at = (local - index * duration) / duration;
        return direction.backwards(index) ? 1 - at : at;
    }

    /// Whether an animation still needs frames `elapsed` milliseconds in —
    /// waiting out its delay or running, and not merely holding its last frame.
    public static boolean isRunning(KeyframeAnimations.Entry entry, double elapsed) {
        Objects.requireNonNull(entry, "entry");
        var local = elapsed - entry.delayMillis();
        if (local < 0) {
            return true;
        }
        var duration = entry.durationMillis();
        return duration > 0 && local < duration * entry.iterations();
    }

    private static void report(String block, String property) {
        if (property.startsWith("--")) {
            return;
        }
        var key = block + '/' + property;
        if (REPORTED.size() >= REPORT_LIMIT || REPORTED.add(key)) {
            LOG.warn(
                    "@keyframes {} declares \"{}\", which does not animate; only {} do (§1.7)",
                    block,
                    property,
                    List.of(Animatable.values()).stream()
                            .map(Animatable::cssName)
                            .toList());
        }
    }
}
