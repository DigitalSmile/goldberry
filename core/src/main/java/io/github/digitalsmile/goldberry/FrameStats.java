package io.github.digitalsmile.goldberry;

/// What the frame loop has been doing lately, for whoever is watching it.
///
/// The numbers behind a frame-rate HUD, and the only reason this is an interface
/// rather than the class that collects them: a widget that draws a rate should be
/// testable against a rate somebody chose, so a golden image of a HUD is a golden
/// image and not a race against the machine that ran it.
///
/// ## What "lately" means
///
/// A mean over the last [#capacity] frames, not since start-up. A rate since
/// start-up answers a question nobody asks — it tells you about the resize you
/// finished a minute ago — and it stops moving, so a HUD showing it looks broken.
///
/// The window is a **frame count and not a duration**, which has one consequence
/// worth stating: nothing here is updated by time passing. A loop that goes idle
/// leaves the last frames in the window and the numbers freeze at whatever the
/// loop was managing when it stopped. That is deliberate — see
/// [io.github.digitalsmile.goldberry.widget.WindowRoot] for why a diagnostic must
/// not be the thing keeping the loop awake — and it is self-correcting: the next
/// frame after an idle second carries that second in its interval, so the rate
/// falls the moment there is anything to fall in front of.
///
/// Confined to the UI thread, like everything the frame loop touches.
public interface FrameStats {

    /// How many frames the averages are taken over, at most.
    int capacity();

    /// How many frames have been painted since the window opened.
    ///
    /// Monotonic, and the one number here that is not a mean. Zero means nothing
    /// has been drawn yet, which is the state every [#fps] of 0 should be read
    /// against.
    long count();

    /// Frames per second over the retained window, or 0 when fewer than two
    /// frames have been recorded.
    ///
    /// Two, not one: a rate is a distance between frames, and a single frame has
    /// nothing to be a distance from.
    double fps();

    /// The mean interval between the retained frames, in milliseconds, or 0.
    ///
    /// `1000 / fps()` by construction. Both are here because they answer
    /// different questions — 60 fps is a reassurance, 16.7 ms is a budget — and a
    /// HUD that made its reader do the division would be showing its working.
    double frameMillis();

    /// The mean time spent *painting* one of those frames, in milliseconds.
    ///
    /// The half of the interval the toolkit is responsible for. On a vsynced loop
    /// [#frameMillis] is the display's and says nothing about whether there is
    /// headroom; this is what says it. A paint of 2 ms inside a 16.7 ms frame is
    /// idle hardware, and 15 ms inside the same frame is one resize away from
    /// dropping every other one.
    double paintMillis();

    /// Whether anything has been recorded yet.
    default boolean isEmpty() {
        return count() == 0;
    }

    /// Statistics for a loop that has not run: every number zero.
    ///
    /// What a widget gets when it asks a tree that has no window under it — a
    /// unit test, or a render into a [Layer] — and the reason a HUD in that
    /// position draws dashes rather than throwing.
    static FrameStats none() {
        return of(0, 0, 0, 0);
    }

    /// Fixed numbers, for a test or a preview that wants a HUD to draw something
    /// it chose rather than whatever the machine managed.
    ///
    /// @param fps         the rate to report
    /// @param frameMillis the interval to report
    /// @param paintMillis the paint time to report
    /// @param count       the frame count to report
    static FrameStats of(double fps, double frameMillis, double paintMillis, long count) {
        return new FixedFrameStats(fps, frameMillis, paintMillis, count);
    }
}
