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
    },

    /// The mean interval between frames: `16.7 ms`.
    ///
    /// The budget. One decimal, because the difference between 16.7 and 16.9 is
    /// the difference between hitting a 60 Hz vsync and missing it.
    FRAME("frame") {
        @Override
        String text(FrameStats stats) {
            return String.format(Locale.ROOT, "%.1f ms", stats.frameMillis());
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
    };

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
                "\"" + text + "\" is not a hud reading. Use one of: fps, frame, paint");
    }

    /// This reading of `stats`, assuming there is something to read.
    abstract String text(FrameStats stats);

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
                case FRAME -> "— ms";
                case PAINT -> "paint —";
            };
        }
        return text(stats);
    }
}
