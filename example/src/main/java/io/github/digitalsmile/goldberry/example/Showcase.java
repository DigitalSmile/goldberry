package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Window;

/// Goldberry's showcase.
///
/// Today it opens a window and paints it. That is the whole of M0: the superbuild
/// links, the bindings are right, the SPI holds, and a window appears at the
/// display's real pixel density. The widget catalog, the CSS engine and the text
/// stack arrive on top of this file, not beside it.
///
/// Run it with `./gradlew run` from either this directory or the repository root.
public final class Showcase {

    /// Nord `nord0` and `nord8` (`docs/ARCHITECTURE.md` §10) — so the first
    /// window Goldberry ever draws is already the right colour.
    private static final int BACKGROUND = 0xFF2E3440;
    private static final int ACCENT = 0xFF88C0D0;

    private Showcase() {
    }

    public static void main(String[] args) {
        var frameLimit = frameLimit(args);
        var painted = new int[1];

        var window = Window.open("Goldberry — showcase", 960, 640);

        window.onPaint(frame -> {
            frame.fill(BACKGROUND);

            // Logical coordinates: this bar is 32 points tall on every display,
            // and Goldberry decides how many real pixels that is. Nothing here
            // knows or cares whether the screen runs at 100% or 150%.
            frame.fillRect(0, 0, frame.size().width(), 32, ACCENT);

            painted[0]++;
            System.out.println("painted frame " + painted[0] + " at " + frame.pixelSize());

            if (frameLimit > 0) {
                if (painted[0] >= frameLimit) {
                    System.out.println("painted " + painted[0] + " frame(s); exiting");
                    Goldberry.stop();
                } else {
                    window.repaint();
                }
            }
        });

        window.onResize(size -> System.out.println("resized to " + size));
        window.onScaleChange(scale -> System.out.println("scale is now " + scale));
        window.onCloseRequest(() -> {
            System.out.println("closing");
            return true;
        });

        // Work that is not instant belongs off the UI thread. It comes back on
        // it automatically, so touching the window here is safe (ADR-0020).
        Goldberry.async(Showcase::describeEnvironment)
                .thenAccept(text -> window.title("Goldberry — " + text));

        System.out.println("Goldberry " + Goldberry.version()
                + " — " + window.size() + " at " + window.scale()
                + " → " + window.pixelSize());

        Goldberry.run();
    }

    /// Stands in for real background work — reading a config file, loading a
    /// font, talking to a service. The point is where its result lands.
    private static String describeEnvironment() {
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
