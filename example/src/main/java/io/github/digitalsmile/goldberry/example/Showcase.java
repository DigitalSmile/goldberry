package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Window;
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

    private Showcase() {
    }

    public static void main(String[] args) {
        var frameLimit = frameLimit(args);
        var painted = new int[1];

        LOG.info("Goldberry {} — showcase starting", Goldberry.version());

        var window = Window.open("Goldberry — showcase", 960, 640);

        window.onPaint(frame -> {
            frame.fill(BACKGROUND);

            // Logical coordinates: this bar is 32 points tall on every display,
            // and Goldberry decides how many real pixels that is. Nothing here
            // knows or cares whether the screen runs at 100% or 150%.
            frame.fillRect(0, 0, frame.size().width(), 32, ACCENT);

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
        for (var arg : args) {
            if (arg.startsWith("--frames=")) {
                return Integer.parseInt(arg.substring("--frames=".length()));
            }
        }
        return 0;
    }
}
