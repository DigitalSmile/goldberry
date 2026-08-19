package io.github.digitalsmile.goldberry.widgets.overlay.hud;

import io.github.digitalsmile.goldberry.FrameStats;
import java.util.Locale;

/// One number a [Hud] can show.
///
/// The formatting is here rather than in the widget because it is the whole of
/// what distinguishes one reading from another: they all read the same
/// [FrameStats] and differ only in which number they take out of it and how many
/// digits of it are worth reading.
///
/// **[Locale#ROOT], deliberately.** `docs/core-widgets.md` §5 says a
/// locale-formatted number produced inside the toolkit makes a golden image that
/// cannot be reproduced on another machine, and that rule was written for
/// `statistic`, whose numbers are the application's. A HUD's are the toolkit's
/// own and it formats them itself — so it formats them the one way that is the
/// same everywhere. A frame rate is not prose.
public enum Reading {

    /// Frames per second, whole: `60 fps`.
    ///
    /// No decimal. A rate that reads `59.7` invites the question of which of the
    /// last sixty frames was late, which this number cannot answer.
    FPS("fps") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "%.0f fps", stats.fps());
        }

        /// A **floor**, not a ceiling: this is the one reading where more is
        /// better, so it reads its own level rather than sharing [#level]'s
        /// arithmetic. 55 is a 60 Hz loop missing the odd frame; 30 is one
        /// missing every other one.
        @Override
        Level level(FrameStats stats) {
            if (stats == null || stats.isEmpty() || stats.fps() <= 0) {
                return Level.OK;
            }
            if (stats.fps() < 30) {
                return Level.OVER;
            }
            return stats.fps() < 55 ? Level.NEAR : Level.OK;
        }

        @Override
        double value(FrameStats stats) {
            return stats.fps();
        }

        @Override
        double budgetMillis() {
            return 0;
        }
    },

    /// The mean interval between frames: `16.7 ms`.
    ///
    /// The budget. One decimal, because the difference between 16.7 and 16.9 is
    /// the difference between hitting a 60 Hz vsync and missing it.
    FRAME("frame") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "frame %.1f ms", stats.frameMillis());
        }

        @Override
        double value(FrameStats stats) {
            return stats.frameMillis();
        }

        /// A 60 Hz frame. The one budget here that is a fact about the display
        /// rather than a judgement about the toolkit.
        @Override
        double budgetMillis() {
            return 16.7;
        }

        /// **A target to sit at, not a ceiling to stay under**, which is why this
        /// reading does not share [#level]'s three-quarters band: a vsynced loop
        /// is *supposed* to measure 16.7, and a healthy window reading amber
        /// teaches a reader to ignore the colour.
        ///
        /// So: on the budget is fine, half again is worth a look, and beyond that
        /// is a loop missing frames.
        @Override
        Level level(FrameStats stats) {
            if (stats == null || stats.isEmpty() || stats.frameMillis() <= 0) {
                return Level.OK;
            }
            var measured = stats.frameMillis();
            if (measured > budgetMillis() * 1.5) {
                return Level.OVER;
            }
            // Five percent of vsync jitter, so a loop hitting its rate exactly
            // does not flicker between two colours.
            return measured > budgetMillis() * 1.05 ? Level.NEAR : Level.OK;
        }
    },

    /// The mean time inside the painter: `paint 2.1 ms`.
    ///
    /// The half of the interval the toolkit is answerable for. Labelled, unlike
    /// the other two, because `2.1 ms` beside `16.7 ms` says nothing about which
    /// is which.
    PAINT("paint") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "paint %.1f ms", stats.paintMillis());
        }

        @Override
        double value(FrameStats stats) {
            return stats.paintMillis();
        }

        /// Half a 60 Hz frame. The toolkit's share of the interval, leaving the
        /// platform its own — a paint over this is a window that cannot absorb a
        /// resize, whatever the rate currently says.
        @Override
        double budgetMillis() {
            return 8.0;
        }
    },

    /// Time spent rebuilding widgets: `build 0.05 ms`.
    ///
    /// The first of the four stages [#PAINT] is the total of, and the one that is
    /// usually nothing: a frame where no `setState` arrived rebuilds no widgets
    /// at all. When it *is* something, the answer is that a stateful widget high
    /// in the tree is marking itself dirty every frame.
    ///
    /// **Two decimals for the stages**, unlike the three readings above. A stage
    /// that reads `0.0 ms` at one decimal is indistinguishable from a stage that
    /// is not running, and the whole use of a breakdown is telling those apart
    /// ([ADR-0146](../../../../../../../../book/src/adr/0146-a-hud-shows-where-the-frame-went.md)).
    BUILD("build") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "build %.2f ms", stats.buildMillis());
        }

        @Override
        double value(FrameStats stats) {
            return stats.buildMillis();
        }

        @Override
        double budgetMillis() {
            return 1.0;
        }
    },

    /// Time spent in the cascade and building boxes: `style 0.29 ms`.
    ///
    /// The reading this whole breakdown was added for. ADR-0070 measured style
    /// resolution as the largest term in a frame and cached it; ADR-0142 found
    /// the cache had quietly stopped working the day `scroll` shipped, and the
    /// showcase had been spending 10 ms a frame re-deriving last frame's answer
    /// with nothing on screen able to say so. This is what says so.
    STYLE("style") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "style %.2f ms", stats.styleMillis());
        }

        @Override
        double value(FrameStats stats) {
            return stats.styleMillis();
        }

        @Override
        double budgetMillis() {
            return 2.0;
        }
    },

    /// Time spent in layout: `layout 0.11 ms`.
    ///
    /// Yoga over the retained render tree, which is why it is usually small: a
    /// frame where nothing resized re-lays out the nodes that changed and reuses
    /// the rest (ADR-0069).
    LAYOUT("layout") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "layout %.2f ms", stats.layoutMillis());
        }

        @Override
        double value(FrameStats stats) {
            return stats.layoutMillis();
        }

        @Override
        double budgetMillis() {
            return 2.0;
        }
    },

    /// Time spent rasterizing: `raster 0.34 ms`.
    ///
    /// Blend2D, over the damage rectangle where the platform's buffer retains and
    /// over the whole frame where it does not (ADR-0072) — so a reading that
    /// jumps when nothing on screen moved is a buffer that stopped retaining
    /// rather than a scene that got harder.
    RASTER("raster") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "raster %.2f ms", stats.rasterMillis());
        }

        @Override
        double value(FrameStats stats) {
            return stats.rasterMillis();
        }

        @Override
        double budgetMillis() {
            return 4.0;
        }
    };

    /// How a reading is doing against its budget — see [#level].
    ///
    /// Three levels and not a number, because what a colour can say is "fine",
    /// "watch this" and "this is the problem", and a gradient would say none of
    /// them at a glance
    /// ([ADR-0150](../../../../../../../../book/src/adr/0150-a-hud-reads-itself-against-a-budget.md)).
    enum Level {
        OK("ok"), NEAR("near"), OVER("over");

        private final String cssClass;

        Level(String cssClass) {
            this.cssClass = cssClass;
        }

        /// The class a stylesheet selects this level by: `hud-reading.over`.
        String cssClass() {
            return cssClass;
        }
    }

    /// At what fraction of its budget a reading starts to be worth looking at.
    ///
    /// Three quarters, so the warning arrives with a quarter of the budget left
    /// rather than after it has gone.
    private static final double NEAR_FRACTION = 0.75;

    private final String cssClass;

    Reading(String cssClass) {
        this.cssClass = cssClass;
    }

    /// The class a stylesheet selects this reading by: `hud-reading.fps`.
    public String cssClass() {
        return cssClass;
    }

    /// Parses the spelling used in `readings="fps paint"`.
    ///
    /// @throws IllegalArgumentException naming the legal values
    public static Reading parse(String text) {
        if (text != null) {
            for (var reading : values()) {
                if (reading.cssClass.equals(text.trim())) {
                    return reading;
                }
            }
        }
        throw new IllegalArgumentException(
                "\"" + text + "\" is not a hud reading. Use one of:"
                        + " fps, frame, paint, build, style, layout, raster");
    }

    /// This reading of `stats`, assuming there is something to read.
    abstract String text(FrameStats stats);

    /// The number behind [#text], for comparing against [#budgetMillis].
    abstract double value(FrameStats stats);

    /// What this reading is allowed to cost, in milliseconds, or 0 for one that
    /// is not a duration.
    ///
    /// Every budget here is a share of the 16.7 ms a 60 Hz frame has, and every
    /// one is a judgement rather than a measurement — which is why they are on
    /// the reading and not in a stylesheet: a token would invite an application
    /// to move the line rather than the number (ADR-0150).
    abstract double budgetMillis();

    /// How this reading is doing against its budget.
    ///
    /// [Level#OK] when there is nothing measured, because a HUD with no loop
    /// behind it is not a HUD reporting a healthy one — it draws dashes, and
    /// dashes in red would be an alarm about nothing.
    Level level(FrameStats stats) {
        if (stats == null || stats.isEmpty() || budgetMillis() <= 0) {
            return Level.OK;
        }
        var measured = value(stats);
        if (measured > budgetMillis()) {
            return Level.OVER;
        }
        return measured >= budgetMillis() * NEAR_FRACTION ? Level.NEAR : Level.OK;
    }

    /// This reading of `stats`, or dashes when there is nothing to read.
    ///
    /// Dashes rather than `0 fps`: a zero is a measurement, and a HUD in a tree
    /// with no frame loop over it has not measured anything. The two states look
    /// different on purpose — a loop genuinely stopped dead reads `0 fps`, and
    /// that is worth being able to tell apart from a HUD that is not plugged in.
    String render(FrameStats stats) {
        if (stats == null || stats.isEmpty()) {
            return switch (this) {
                case FPS -> "— fps";
                case FRAME -> "frame —";
                case PAINT -> "paint —";
                case BUILD -> "build —";
                case STYLE -> "style —";
                case LAYOUT -> "layout —";
                case RASTER -> "raster —";
            };
        }
        return text(stats);
    }
}
