package io.github.digitalsmile.goldberry.paint.overflow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// Says once that something did not fit.
///
/// ## Why once
///
/// A layout is recomputed whenever anything moves, so a window that is too
/// narrow overflows on every frame it is looked at — sixty identical lines a
/// second, which is not a louder warning but a quieter one. The same argument
/// `ComputedStyle` makes about a dropped declaration, and the same answer: a
/// bounded set of what has already been said ([ADR-0375]).
///
/// The key is the *shape* of the overrun and not its size, so a window being
/// dragged narrower reports the first pixel it overflowed by and then stops.
/// Resizing it back and forth says nothing new, which is the point: the answer
/// to "what is off the edge" does not change with the distance.
public final class OverflowLog {

    private static final Logger LOG = Logs.of(OverflowLog.class);

    /// What has already been reported, keyed by `container > child`.
    ///
    /// Kept rather than counted, so that a test — and an application's own
    /// diagnostics — can read what was said without a logging backend under it.
    private static final Map<String, Overrun> REPORTED = new ConcurrentHashMap<>();

    /// How many distinct overruns are remembered before the deduplication gives
    /// up and lets them all through — `ComputedStyle`'s cap, for its reason.
    private static final int LIMIT = 256;

    private OverflowLog() {}

    /// Reports `overrun` unless an overrun of the same shape has been reported.
    ///
    /// @return whether anything was logged, which is what a test asserts on
    public static boolean report(Overrun overrun) {
        var key = overrun.container() + " > " + overrun.child();
        if (REPORTED.size() < LIMIT && REPORTED.putIfAbsent(key, overrun) != null) {
            return false;
        }
        LOG.warn(
                "{} — it is laid out past the edge and is clipped or off screen. A `scroll`"
                        + " around it, an ellipsis on it, or a `min-width` it may not go below"
                        + " are the three answers; nothing here picks one.",
                overrun);
        return true;
    }

    /// Every overrun reported since the last [#forget()], in no order.
    ///
    /// Bounded by the cap above, so this is a diagnostic an application may read
    /// on a frame without it growing under them.
    public static List<Overrun> reported() {
        return List.copyOf(REPORTED.values());
    }

    /// Forgets everything reported so far — for a test, and for an application
    /// that has changed its whole screen and wants the next overrun heard.
    public static void forget() {
        REPORTED.clear();
    }
}
