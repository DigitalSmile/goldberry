package dev.goldberry.stats;

import dev.goldberry.paint.Layer;

/// What the frame loop has been doing lately: the frame rate, where each frame's
/// time went, and how many refreshes were missed, averaged over the last few
/// dozen frames.
///
/// ```java
/// FrameStats frames = host.frames();
/// if (!frames.isEmpty()) {
///     log.info("{} fps, paint {} ms", frames.fps(), frames.paintMillis());
/// }
/// ```
///
/// These are the numbers behind a `hud`. It is an interface rather than the class
/// that collects them so that a widget drawing a rate can be tested against a
/// rate somebody chose: [#of] builds fixed numbers, [#none] a loop that has not
/// run, and a golden image of a HUD is then a golden image rather than a race
/// against the machine that ran it.
///
/// Every mean here is over the last [#capacity] frames, not since start-up. A
/// rate since start-up stops moving and only ever tells you about the resize you
/// finished a minute ago. The window is a frame count and not a duration, so
/// nothing here changes with time passing: a loop that goes idle leaves the last
/// frames in the window and the numbers freeze at whatever the loop was managing
/// when it stopped. That is deliberate, because a diagnostic must not be the
/// thing keeping the loop awake, and it corrects itself: the first frame after an
/// idle second carries that second in its interval, so the rate falls the moment
/// there is anything to fall in front of.
///
/// Zero is "nothing was measured", not "nothing happened". A source that does not
/// measure the stages, the display's rate or the GPU presents answers 0 for them,
/// and a HUD draws dashes on it.
///
/// Confined to the UI thread, like everything the frame loop touches.
///
/// Read more: [Measuring](https://goldberry.dev/docs/performance/measuring.html#per-frame-timings-at-trace).
public interface FrameStats {

    /// What one stage cost across the retained window: its cheapest frame, its
    /// mean, and its dearest.
    ///
    /// A mean alone hides the shape of the cost, and the shape is usually the
    /// question. Two windows both averaging 2 ms are different animals if one
    /// ranges 1.9 to 2.1 and the other 0.2 to 14: the first is steady work and the
    /// second is a spike being averaged away over sixty frames.
    ///
    /// @param min  the cheapest retained frame, in milliseconds
    /// @param mean the mean over them
    /// @param max  the dearest
    record Span(double min, double mean, double max) {

        /// Nothing measured.
        static final Span NONE = new Span(0, 0, 0);

        /// One number with no spread: what a source that reports a mean and
        /// keeps no window can honestly say.
        static Span of(double mean) {
            return new Span(mean, mean, mean);
        }
    }

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
    /// different questions: 60 fps is a reassurance, 16.7 ms is a budget.
    double frameMillis();

    /// The mean time spent painting one of those frames, in milliseconds.
    ///
    /// The part of the interval the toolkit is responsible for. On a vsynced loop
    /// [#frameMillis] is the display's and says nothing about headroom; this is
    /// what says it. A paint of 2 ms inside a 16.7 ms frame is idle hardware, and
    /// 15 ms inside the same frame is one resize away from dropping every other
    /// one.
    double paintMillis();

    /// The mean time spent rebuilding widgets in one of those frames, in
    /// milliseconds: every `setState` since the last frame, settled once.
    ///
    /// Zero on a source that does not measure the stages, which is every source
    /// but the frame loop's own. That is not a claim the stage took no time; it
    /// is the same "nothing was measured" that [#isEmpty] means, and a HUD draws
    /// dashes on it for the same reason.
    default double buildMillis() {
        return 0;
    }

    /// The mean time spent resolving styles and building boxes, in milliseconds:
    /// the cascade, its cache, and the widget tree turning into a box tree.
    ///
    /// Reported on its own because it has been the largest term in a frame
    /// without anything on screen being able to say so.
    default double styleMillis() {
        return 0;
    }

    /// The mean time spent in layout, in milliseconds: Yoga, over the retained
    /// render tree.
    default double layoutMillis() {
        return 0;
    }

    /// The mean time spent rasterizing, in milliseconds: Blend2D, over the damage
    /// rectangle when the platform's buffer retains and over the whole frame when
    /// it does not.
    default double rasterMillis() {
        return 0;
    }

    /// How many of the display's refreshes went by, over the retained window,
    /// with a frame wanted and never seen.
    ///
    /// Everything else on this interface is measured over the frames that were
    /// painted, so a frame that was never painted moves the mean and is not
    /// otherwise reported, and a frame that was painted and then refused by the
    /// platform is in the mean as though the user had seen it. This is the
    /// number that reports both.
    ///
    /// Two sources, one number, because a reader wants the one:
    ///
    /// - the pacer's view: a frame was asked for, the display refreshed, and the
    ///   loop had not produced it. That is a loop that overran its budget, and
    ///   it is the only place the overrun is visible.
    /// - the painter's view: a frame was painted and then refused, which during
    ///   a resize is ordinary rather than exotic. The window became a different
    ///   size while the frame was being drawn for the old one.
    ///
    /// Over the retained window, like every mean here and unlike [#count()]: a
    /// total since start-up would only ever go up, so a loop that dropped four
    /// frames during a resize a minute ago would still be reporting them.
    /// [#summary] has the total.
    ///
    /// Zero on a source that does not measure it, which is every source but the
    /// frame loop's own. Like [#displayHertz], zero is "nothing was measured"
    /// rather than "nothing was dropped".
    default long lateFrames() {
        return 0;
    }

    /// How many times a second the display refreshes, or 0 if the platform will
    /// not say.
    ///
    /// The only rate here that is not counted. Nothing can report what a loop
    /// achieved except the loop, which is what [#fps] is; this is what the
    /// display does, as the platform reports its current mode. Every budget on a
    /// `hud` is a share of it, so a 120 Hz window judges itself against 8.3 ms
    /// rather than a hard-coded 16.7.
    default double displayHertz() {
        return 0;
    }

    /// [#paintMillis] as a range over the retained window.
    ///
    /// Defaults to a flat span, so a source that keeps no window, such as a
    /// test's fixed numbers, reports its one number three times rather than
    /// inventing a spread it has not measured.
    default Span paint() {
        return Span.of(paintMillis());
    }

    /// [#buildMillis] as a range.
    default Span build() {
        return Span.of(buildMillis());
    }

    /// [#styleMillis] as a range.
    default Span style() {
        return Span.of(styleMillis());
    }

    /// [#layoutMillis] as a range.
    default Span layout() {
        return Span.of(layoutMillis());
    }

    /// [#rasterMillis] as a range.
    default Span raster() {
        return Span.of(rasterMillis());
    }

    /// How many of the retained frames were presented through the GPU, which is
    /// every frame of a composited window.
    ///
    /// Zero for a window presenting through its window surface, and for every
    /// source but the frame loop's own. The three present readings below are
    /// taken over these frames alone, and mean nothing when there are none.
    default int compositedFrames() {
        return 0;
    }

    /// The time spent uploading a composited frame: copying its damage into
    /// staging memory and recording the copy to the GPU.
    ///
    /// The CPU cost of compositing that grows with the damage, so a caret
    /// blinking should keep it small and a full-window animation should not.
    /// [Span#NONE] when [#compositedFrames] is zero.
    default Span upload() {
        return Span.NONE;
    }

    /// The time spent waiting for the swapchain before a composited frame could
    /// be drawn: the display pacing the loop, not work. Near the rest of a
    /// display frame on a loop that keeps up, and near zero on one that does not.
    default Span acquire() {
        return Span.NONE;
    }

    /// The time spent recording a composited frame's composite and submitting
    /// it.
    default Span submit() {
        return Span.NONE;
    }

    /// The mean number of bytes a composited frame uploaded: the damage, at four
    /// bytes a pixel. This says whether damage tracking is paying for itself on
    /// the GPU path, where every damaged pixel crosses to the GPU.
    default double uploadBytes() {
        return 0;
    }

    /// Whether anything has been recorded yet.
    default boolean isEmpty() {
        return count() == 0;
    }

    /// The whole run, for the line a launcher writes at exit.
    ///
    /// A source that keeps no totals answers from its window: the count is
    /// already every frame, and the rest is the last sixty standing in for the
    /// lot. [FrameRing] overrides it with what it actually summed.
    default FrameSummary summary() {
        var paint = paint();
        return new FrameSummary(count(), lateFrames(), paint.mean(), paint.max(), displayHertz());
    }

    /// The whole run's composited presents, for the line a launcher writes at
    /// exit: [PresentSummary#NONE] for a source that keeps no totals, and for a
    /// window that composited nothing.
    default PresentSummary presentSummary() {
        return PresentSummary.NONE;
    }

    /// Statistics for a loop that has not run: every number zero.
    ///
    /// What a widget gets when it asks a tree that has no window under it, such
    /// as a unit test or a render into a [Layer], and the reason a HUD in that
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
        return new FixedFrameStats(fps, frameMillis, paintMillis, count, 0, 0, 0, 0, 0, 0);
    }

    /// The same, with the four stages a frame is made of, for the golden image
    /// of a HUD showing the breakdown.
    ///
    /// The stages do not have to add up to `paintMillis` and are not asserted to:
    /// the total includes the hit-test capture and the frame's own setup, which
    /// are neither large enough to name nor zero.
    static FrameStats of(
            double fps,
            double frameMillis,
            double paintMillis,
            long count,
            double buildMillis,
            double styleMillis,
            double layoutMillis,
            double rasterMillis) {
        return new FixedFrameStats(
                fps, frameMillis, paintMillis, count, buildMillis, styleMillis, layoutMillis, rasterMillis, 0, 0);
    }

    /// The same, with the display's refresh rate, for the golden image of a
    /// `hud` whose budgets have to be the same on every machine.
    static FrameStats of(
            double fps,
            double frameMillis,
            double paintMillis,
            long count,
            double buildMillis,
            double styleMillis,
            double layoutMillis,
            double rasterMillis,
            double displayHertz) {
        return of(
                fps,
                frameMillis,
                paintMillis,
                count,
                buildMillis,
                styleMillis,
                layoutMillis,
                rasterMillis,
                displayHertz,
                0);
    }

    /// The same, with the frames that never reached the screen, for the golden
    /// image of a `hud` reporting a loop that dropped some, which is a picture
    /// nothing else can produce on demand.
    static FrameStats of(
            double fps,
            double frameMillis,
            double paintMillis,
            long count,
            double buildMillis,
            double styleMillis,
            double layoutMillis,
            double rasterMillis,
            double displayHertz,
            long lateFrames) {
        return new FixedFrameStats(
                fps,
                frameMillis,
                paintMillis,
                count,
                buildMillis,
                styleMillis,
                layoutMillis,
                rasterMillis,
                displayHertz,
                lateFrames);
    }
}
