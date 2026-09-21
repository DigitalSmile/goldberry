package io.github.digitalsmile.goldberry.example.webpump;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/// What the page reported, and what it adds up to.
///
/// Kept separate from [PumpProbe] because it is the part worth a unit test: a
/// probe needs a display and an engine, and a median does not.
///
/// The **first** reading is dropped from the summary. A page's opening second
/// includes its own load, layout and first paint, and a summary that included
/// them would describe the load rather than the animation.
final class FpsLog {

    /// Below this, a clock is judged to have failed rather than merely to be
    /// slow. Well under any display rate and well over the ~1 Hz an engine falls
    /// back to, so it separates the two without being a guess about either.
    private static final double FAILED = 10;

    /// One report from the page.
    ///
    /// @param index       which report this was, from zero
    /// @param rafFps      `requestAnimationFrame` callbacks per second
    /// @param timelineFps rendering updates per second, from `document.timeline`
    /// @param timerFps    `setInterval` callbacks per second
    /// @param visible     what the page's own `document.visibilityState` said
    record Reading(int index, double rafFps, double timelineFps, double timerFps, boolean visible) {

        /// Which of the three stages this reading implicates.
        ///
        /// The whole reason three clocks are measured rather than one. They
        /// nest: a timer that does not run means the main context is not being
        /// drained, a timeline that does not advance means the engine is not
        /// compositing, and frame callbacks that do not arrive while the
        /// timeline advances means the engine is compositing and simply not
        /// running the page's callbacks. Each is somebody else's bug.
        Verdict verdict() {
            if (timerFps < FAILED) {
                return Verdict.CONTEXT_STARVED;
            }
            if (timelineFps < FAILED) {
                return Verdict.RENDERING_STALLED;
            }
            return rafFps < timelineFps / 4 ? Verdict.RAF_STARVED : Verdict.HEALTHY;
        }
    }

    /// What a reading implicates, worst first — see [Reading#verdict()]. The
    /// declaration order is the severity order, which [FpsLog#verdict()] relies
    /// on.
    enum Verdict {

        /// Timers do not run: GLib's main context is not being drained often
        /// enough. The embedder's fault, and the only one of these Goldberry
        /// can fix.
        CONTEXT_STARVED,

        /// Timers run but the rendering update does not advance: the engine is
        /// being pumped and is not compositing.
        RENDERING_STALLED,

        /// The engine composites, but the page's frame callbacks do not arrive.
        /// A fault in the engine's scripted animation controller, which no
        /// amount of pumping will change.
        RAF_STARVED,

        /// Frame callbacks arrive at the rate the engine composites.
        HEALTHY
    }

    private final List<Reading> readings = new ArrayList<>();

    /// Records a reading, and returns it.
    Reading add(double rafFps, double timelineFps, double timerFps, boolean visible) {
        var reading = new Reading(readings.size(), rafFps, timelineFps, timerFps, visible);
        readings.add(reading);
        return reading;
    }

    /// Every reading, in the order the page sent them.
    List<Reading> readings() {
        return List.copyOf(readings);
    }

    /// The readings the summary is built from: everything but the first.
    ///
    /// Empty when the page reported once or not at all, which is why every
    /// statistic below is an optional rather than a zero. A run that measured
    /// nothing must not read as a run that measured zero frames a second — one
    /// is a broken probe and the other is a broken engine.
    List<Reading> settled() {
        return readings.size() <= 1 ? List.of() : List.copyOf(readings.subList(1, readings.size()));
    }

    /// The middle value, which is the statistic to compare runs on: a mean over
    /// a handful of readings moves too far for one stalled second.
    private static OptionalDouble median(double[] values) {
        if (values.length == 0) {
            return OptionalDouble.empty();
        }
        var sorted = values.clone();
        Arrays.sort(sorted);
        var middle = sorted.length / 2;
        return OptionalDouble.of(sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2);
    }

    OptionalDouble medianRaf() {
        return median(settled().stream().mapToDouble(Reading::rafFps).toArray());
    }

    OptionalDouble medianTimeline() {
        return median(settled().stream().mapToDouble(Reading::timelineFps).toArray());
    }

    OptionalDouble medianTimer() {
        return median(settled().stream().mapToDouble(Reading::timerFps).toArray());
    }

    /// The **worst** verdict among the settled readings, or empty when there
    /// are none.
    ///
    /// Worst rather than a vote: one starved second in six is still a starved
    /// second, and a summary that averaged it away would hide the thing this
    /// exists to look for.
    Optional<Verdict> verdict() {
        return settled().stream().map(Reading::verdict).min(Comparator.naturalOrder());
    }

    /// One line, for the log the experiment is read from.
    String describe() {
        if (settled().isEmpty()) {
            return "the page reported %d time(s); too few to summarize".formatted(readings.size());
        }
        var hidden = settled().stream().filter(reading -> !reading.visible()).count();
        return String.format(
                Locale.ROOT,
                "%d reading(s) after the first: median %.1f raf/s, %.1f timeline/s, %.1f timer/s — %s%s",
                settled().size(),
                medianRaf().orElseThrow(),
                medianTimeline().orElseThrow(),
                medianTimer().orElseThrow(),
                verdict().orElseThrow(),
                hidden == 0 ? "" : " (the page called itself hidden in %d of them)".formatted(hidden));
    }
}
