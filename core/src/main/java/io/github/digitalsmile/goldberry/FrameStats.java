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

    /// What one stage cost across the retained window: its cheapest frame, its
    /// mean, and its dearest.
    ///
    /// **A mean alone hides the shape of the cost**, and the shape is usually the
    /// question. Two windows both averaging 2 ms are different animals if one
    /// ranges 1.9–2.1 and the other 0.2–14: the first is steady work and the
    /// second is a spike being averaged away over sixty frames
    /// ([ADR-0154](../../../../../book/src/adr/0154-a-reading-is-a-range.md)).
    ///
    /// @param min  the cheapest retained frame, in milliseconds
    /// @param mean the mean over them
    /// @param max  the dearest
    record Span(double min, double mean, double max) {

        /// Nothing measured.
        static final Span NONE = new Span(0, 0, 0);

        /// One number with no spread — what a source that reports a mean and
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

    /// The mean time spent **rebuilding widgets** in one of those frames, in
    /// milliseconds — every `setState` since the last frame, settled once
    /// ([ADR-0052](../../../../../book/src/adr/0052-state-is-a-plain-object-and-setstate-defers.md)).
    ///
    /// Zero on a source that does not measure the stages, which is every source
    /// but the frame loop's own. That is not a claim the stage took no time: it
    /// is the same "nothing was measured" [#isEmpty] already means, and the four
    /// stage readings draw dashes on it for the same reason
    /// ([ADR-0146](../../../../../book/src/adr/0146-a-hud-shows-where-the-frame-went.md)).
    default double buildMillis() {
        return 0;
    }

    /// The mean time spent **resolving styles and building boxes**.
    ///
    /// The cascade, its cache, and the widget tree turning into a box tree. The
    /// term ADR-0070 measured as the largest in a frame, and ADR-0142 as the one
    /// that had quietly stopped being cached — which is the whole argument for
    /// this number existing: it was the largest term in the frame for a month and
    /// nothing on screen could have told anybody.
    default double styleMillis() {
        return 0;
    }

    /// The mean time spent in **layout** — Yoga, over the retained render tree.
    default double layoutMillis() {
        return 0;
    }

    /// The mean time spent **rasterizing** — Blend2D, over the damage rectangle
    /// when the platform's buffer retains and over the whole frame when it does
    /// not.
    default double rasterMillis() {
        return 0;
    }

    /// How many times a second the display refreshes, or **0** if the platform
    /// will not say.
    ///
    /// **The only rate that is not counted.** SDL has no achieved-frame-rate
    /// call, and nothing else does either: what a loop managed can only be
    /// measured by the loop, which is what [#fps] is. This is what the *display*
    /// does, asked of `SDL_GetCurrentDisplayMode` — and it is the number every
    /// budget on a `hud` is a share of, so a 120 Hz window judges itself against
    /// 8.3 ms rather than against a hard-coded 16.7
    /// ([ADR-0153](../../../../../book/src/adr/0153-a-rate-is-counted-a-refresh-is-asked-for.md)).
    default double displayHertz() {
        return 0;
    }

    /// [#paintMillis] as a range over the retained window.
    ///
    /// Defaults to a flat span, so a source that keeps no window — a test's fixed
    /// numbers — reports its one number three times rather than inventing a
    /// spread it has not measured.
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
        return new FixedFrameStats(fps, frameMillis, paintMillis, count, 0, 0, 0, 0, 0);
    }

    /// The same, with the four stages a frame is made of — for the golden image
    /// of a HUD showing the breakdown, which has to draw numbers somebody chose.
    ///
    /// The stages do not have to add up to `paintMillis` and are not asserted to:
    /// the total includes the hit-test capture and the frame's own setup, which
    /// are neither large enough to name nor zero.
    static FrameStats of(double fps, double frameMillis, double paintMillis, long count,
            double buildMillis, double styleMillis, double layoutMillis, double rasterMillis) {
        return new FixedFrameStats(fps, frameMillis, paintMillis, count,
                buildMillis, styleMillis, layoutMillis, rasterMillis, 0);
    }

    /// The same, with the display's refresh rate — for the golden image of a
    /// `hud` whose budgets have to be the same on every machine.
    static FrameStats of(double fps, double frameMillis, double paintMillis, long count,
            double buildMillis, double styleMillis, double layoutMillis, double rasterMillis,
            double displayHertz) {
        return new FixedFrameStats(fps, frameMillis, paintMillis, count,
                buildMillis, styleMillis, layoutMillis, rasterMillis, displayHertz);
    }
}
