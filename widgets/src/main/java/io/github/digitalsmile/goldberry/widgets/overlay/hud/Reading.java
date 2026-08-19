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

        /// **Never coloured**, and that is why it is still here rather than gone.
        ///
        /// §1.7 makes the loop idle when nothing asks for a frame, so the gap
        /// between two frames is however long the user did not touch the window.
        /// A rate counted over that measures the *user*: it collapses the moment
        /// you stop clicking and stays low for the next sixty frames. Judging it
        /// against a budget turned normal idling into an alarm, which is the
        /// opposite of what a budget is for
        /// ([ADR-0153](../../../../../../../../book/src/adr/0153-a-rate-is-counted-a-refresh-is-asked-for.md)).
        ///
        /// It is worth showing anyway: while something *is* moving — a drag, a
        /// scroll, a transition — the loop runs continuously and this is exactly
        /// the number to watch. It is context, and the readings the toolkit is
        /// answerable for are the ones below it.
        @Override
        Level level(FrameStats stats) {
            return Level.OK;
        }

        @Override
        double value(FrameStats stats) {
            return stats.fps();
        }
    },

    /// What the **display** does: `refresh 60 Hz`.
    ///
    /// Named `refresh` and not `display`, which is where it started: a reading's
    /// name is its CSS class, and `.display` is already §1.4's largest type rank
    /// — so the first draft of this reading rendered at 28px. A widget's class
    /// names share one namespace with the design system's, and this is the first
    /// collision (ADR-0153).
    ///
    /// The only rate a platform can be asked for. SDL has no achieved-frame-rate
    /// call and nothing else does either — `SDL_GetCurrentDisplayMode` reports
    /// the display's mode, and what a loop managed can only be counted by the
    /// loop, which is [#FPS] with all of its caveats.
    ///
    /// It is here because it is the number every budget below is a share of: on a
    /// 120 Hz window a frame is 8.3 ms and not 16.7, and a `hud` judging against
    /// the wrong one would call a healthy loop late (ADR-0153).
    ///
    /// Dashes when the platform will not say — a headless backend, or a mode SDL
    /// cannot describe.
    REFRESH("refresh") {
        @Override
        String text(FrameStats stats) {
            return stats.displayHertz() > 0
                    ? String.format(Locale.ROOT, "refresh %.0f Hz", stats.displayHertz())
                    : "refresh —";
        }

        @Override
        double value(FrameStats stats) {
            return stats.displayHertz();
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

        /// **Half a display frame**, whatever the display is: the toolkit's share
        /// of the interval, leaving the platform its own. A paint over this is a
        /// window that cannot absorb a resize, whatever the rate currently says.
        ///
        /// A share rather than a number, so a 120 Hz window judges itself against
        /// 4.2 ms where a 60 Hz one gets 8.3 (ADR-0153).
        @Override
        double budgetMillis(FrameStats stats) {
            return frameBudget(stats) / 2;
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

        /// A sixteenth of a display frame (ADR-0153).
        @Override
        double budgetMillis(FrameStats stats) {
            return frameBudget(stats) / 16;
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

        /// An eighth of a display frame (ADR-0153).
        @Override
        double budgetMillis(FrameStats stats) {
            return frameBudget(stats) / 8;
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

        /// An eighth of a display frame (ADR-0153).
        @Override
        double budgetMillis(FrameStats stats) {
            return frameBudget(stats) / 8;
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

        /// A quarter of a display frame (ADR-0153).
        @Override
        double budgetMillis(FrameStats stats) {
            return frameBudget(stats) / 4;
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
                        + " fps, refresh, paint, build, style, layout, raster");
    }

    /// This reading of `stats`, assuming there is something to read.
    abstract String text(FrameStats stats);

    /// The number behind [#text], for comparing against [#budgetMillis].
    abstract double value(FrameStats stats);

    /// What this reading is allowed to cost, in milliseconds, or 0 for one that
    /// is not a duration and cannot be over anything.
    ///
    /// Every budget here is a share of one **display** frame, and every one is a
    /// judgement rather than a measurement — which is why they are on the reading
    /// and not in a stylesheet: a token would invite an application to move the
    /// line rather than the number (ADR-0150).
    double budgetMillis(FrameStats stats) {
        return 0;
    }

    /// How long one frame of the display lasts, in milliseconds.
    ///
    /// 16.7 when the platform will not say what the display does — a headless
    /// backend, or a mode SDL cannot describe. A stated assumption rather than a
    /// hidden one (ADR-0153).
    static double frameBudget(FrameStats stats) {
        var hertz = stats == null ? 0 : stats.displayHertz();
        return hertz > 0 ? 1_000.0 / hertz : 1_000.0 / 60;
    }

    /// How this reading is doing against its budget.
    ///
    /// [Level#OK] when there is nothing measured, because a HUD with no loop
    /// behind it is not a HUD reporting a healthy one — it draws dashes, and
    /// dashes in red would be an alarm about nothing.
    Level level(FrameStats stats) {
        if (stats == null || stats.isEmpty()) {
            return Level.OK;
        }
        var budget = budgetMillis(stats);
        if (budget <= 0) {
            return Level.OK;
        }
        var measured = value(stats);
        if (measured > budget) {
            return Level.OVER;
        }
        return measured >= budget * NEAR_FRACTION ? Level.NEAR : Level.OK;
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
                case REFRESH -> "refresh —";
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
