package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Goldberry's showcase.
///
/// Today it opens a window and paints it. That is the whole of M0: the superbuild
/// links, the bindings are right, the SPI holds, and a window appears at the
/// display's real pixel density. The widget catalog, the CSS engine and the text
/// stack arrive on top of this file, not beside it.
///
/// It is also where logging is configured — `logback.xml` beside this class,
/// because binding a logging implementation is an application's decision and
/// never a library's (ADR-0023).
///
/// Run it with `./gradlew run` from the repository root.
public final class Showcase {

    private static final Logger LOG = LoggerFactory.getLogger(Showcase.class);

    /// Nord `nord0` and `nord8` (`docs/ARCHITECTURE.md` §10) — so the first
    /// window Goldberry ever draws is already the right colour.
    private static final int BACKGROUND = 0xFF2E3440;
    private static final int ACCENT = 0xFF88C0D0;
    private static final int PANEL = 0xFF3B4252;
    private static final int MUTED = 0xFF4C566A;

    /// The layout the window paints, built once and reused every frame.
    ///
    /// Flexbox through Yoga, filled through Blend2D, in logical coordinates
    /// throughout — the two engines meeting (ADR-0033). It is a value, so
    /// building it outside the paint callback is not an optimisation but the
    /// natural thing to do.
    private static final Box LAYOUT = Box.of()
            .direction(FlexDirection.COLUMN)
            .background(BACKGROUND)
            .children(
                    // A 32-point bar across the top, whatever the display scale.
                    Box.filled(ACCENT).size(StyleLength.UNDEFINED, StyleLength.points(32)),
                    Box.of()
                            .grow(1)
                            .direction(FlexDirection.ROW)
                            .padding(StyleLength.points(16))
                            .gap(StyleLength.points(16))
                            .children(
                                    // A quarter-width sidebar and a body that
                                    // takes what is left.
                                    Box.filled(PANEL).size(
                                            StyleLength.percent(25), StyleLength.UNDEFINED),
                                    Box.filled(MUTED).grow(1)));

    private Showcase() {
    }

    public static void main(String[] args) {
        var frameLimit = frameLimit(args);
        var painted = new int[1];

        LOG.info("Goldberry {} — showcase starting", Goldberry.version());

        var window = Window.open("Goldberry — showcase", widthOf(args), heightOf(args));

        window.onPaint(frame -> {
            // Yoga lays the tree out at the frame's logical size and Blend2D
            // fills the result. Logical coordinates throughout: the bar is 32
            // points tall and the sidebar a quarter of the width on every
            // display, and nothing here knows whether the screen runs at 100%
            // or 150%.
            BoxPainter.paint(frame, LAYOUT);

            painted[0]++;
            LOG.info("painted frame {} at {}", painted[0], frame.pixelSize());

            if (frameLimit > 0) {
                if (painted[0] >= frameLimit) {
                    LOG.info("painted {} frame(s); exiting", painted[0]);
                    Goldberry.stop();
                } else {
                    window.repaint();
                }
            }
        });

        window.onResize(size -> LOG.info("resized to {}", size));
        window.onScaleChange(scale -> LOG.info("scale is now {}", scale));
        window.onCloseRequest(() -> {
            LOG.info("close requested");
            return true;
        });

        // Work that is not instant belongs off the UI thread. It comes back on
        // it automatically, so touching the window here is safe (ADR-0020).
        Goldberry.async(Showcase::describeEnvironment)
                .thenAccept(text -> window.title("Goldberry — " + text));

        Goldberry.run();
        LOG.info("showcase finished");
    }

    /// Stands in for real background work — reading a config file, loading a
    /// font, talking to a service. The point is where its result lands.
    private static String describeEnvironment() {
        LOG.debug("describing the environment on {}", Thread.currentThread());
        return "showcase on " + System.getProperty("os.name")
                + " / " + System.getProperty("os.arch");
    }

    /// `--frames=N` paints N frames and exits, so CI can prove a window opened
    /// without a human to close it. Absent, the window stays until closed.
    private static int frameLimit(String[] args) {
        return intArgument(args, "--frames=", 0);
    }

    /// `--size=WxH` opens at a size other than the default — for looking at what
    /// a frame costs when there are two million pixels of it.
    private static float widthOf(String[] args) {
        return sizeArgument(args, 0, 960);
    }

    private static float heightOf(String[] args) {
        return sizeArgument(args, 1, 640);
    }

    private static float sizeArgument(String[] args, int index, float fallback) {
        for (var arg : args) {
            if (arg.startsWith("--size=")) {
                var parts = arg.substring("--size=".length()).split("x");
                if (parts.length == 2) {
                    return Float.parseFloat(parts[index]);
                }
            }
        }
        return fallback;
    }

    private static int intArgument(String[] args, String prefix, int fallback) {
        for (var arg : args) {
            if (arg.startsWith(prefix)) {
                return Integer.parseInt(arg.substring(prefix.length()));
            }
        }
        return fallback;
    }
}
