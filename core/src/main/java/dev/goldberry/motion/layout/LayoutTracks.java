package dev.goldberry.motion.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.value.ViewTransition;

/// Where each named box was laid out, and where it is drawn while layout moves
/// it.
///
/// One pass per layout: [#begin()], then [#place] for every named box the tree
/// has, then [#end()]. A box whose layout moved since the last pass is drawn
/// where it was and travels to where it is, over its [ViewTransition]'s time.
/// A box that moves again while travelling sets off from wherever it is drawn
/// at that moment, so a move interrupted is never a jump.
///
/// A name missing from a pass is forgotten, so a box that leaves and comes back
/// arrives where it is laid out rather than flying in from where it once was.
/// A name given twice in one pass belongs to the first box that has it, and the
/// second does not move.
///
/// UI-thread confined, like the render tree that asks.
public final class LayoutTracks {

    /// A [#place] time that says there is no clock: every move is instant.
    public static final double NO_CLOCK = Double.NaN;

    /// One move in progress.
    private record Run(Place from, Place to, double startMillis, ViewTransition timing) {

        double progress(double now) {
            return Math.clamp((now - startMillis) / timing.durationMillis(), 0, 1);
        }

        Place at(double now) {
            return from.toward(to, timing.easing().at(progress(now)));
        }
    }

    /// One name: where its box was last laid out, and its move if it has one.
    private static final class Track {
        Place laidOut;

        @Nullable
        Run run;

        long pass;

        Track(Place laidOut, long pass) {
            this.laidOut = laidOut;
            this.pass = pass;
        }
    }

    private final Map<String, Track> tracks = new HashMap<>();
    private long pass;
    private boolean moving;

    /// Starts a pass.
    public void begin() {
        pass++;
        moving = false;
    }

    /// Where the box named `timing.name()` is drawn in this pass, laid out at
    /// `laidOut` at `nowMillis`.
    ///
    /// @param nowMillis the frame's time, or [#NO_CLOCK]
    public Place place(ViewTransition timing, Place laidOut, double nowMillis) {
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(laidOut, "laidOut");
        var track = tracks.get(timing.name());
        if (track == null || track.pass < pass - 1) {
            tracks.put(timing.name(), new Track(laidOut, pass));
            return laidOut;
        }
        if (track.pass == pass) {
            // A second box with the same name: the first has it.
            return laidOut;
        }
        track.pass = pass;
        var clock = !Double.isNaN(nowMillis);
        if (!laidOut.near(track.laidOut)) {
            var from = track.run == null || !clock ? track.laidOut : track.run.at(nowMillis);
            track.laidOut = laidOut;
            track.run = timing.isInstant() || !clock ? null : new Run(from, laidOut, nowMillis, timing);
        }
        var run = track.run;
        if (run == null) {
            return laidOut;
        }
        if (!clock || run.progress(nowMillis) >= 1) {
            track.run = null;
            return laidOut;
        }
        moving = true;
        return run.at(nowMillis);
    }

    /// Ends a pass, forgetting every name it did not see.
    public void end() {
        tracks.values().removeIf(track -> track.pass != pass);
    }

    /// Whether some box is still travelling after the last pass, which is what
    /// keeps the frame loop awake.
    public boolean isMoving() {
        return moving;
    }

    /// How many names are remembered, for a test.
    int size() {
        return tracks.size();
    }
}
