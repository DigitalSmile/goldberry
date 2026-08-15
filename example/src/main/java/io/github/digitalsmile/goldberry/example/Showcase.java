package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.backend.Backend;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.backend.sdl3.Sdl3Backend;
import java.util.List;

/// Goldberry's showcase.
///
/// Today it opens a window and fills it with a colour. That is the whole of M0:
/// the superbuild links, the bindings are right, the SPI holds, and a window
/// appears at the display's real pixel density. The widget catalog, the CSS
/// engine and the text stack arrive on top of this file, not beside it.
///
/// Run it with `./gradlew run` from this directory.
public final class Showcase {

    /// Nord `nord0` — the theme's dark background (`docs/ARCHITECTURE.md` §10),
    /// so the first window Goldberry ever draws is already the right colour.
    private static final int BACKGROUND = 0xFF2E3440;

    private Showcase() {
    }

    public static void main(String[] args) {
        var frameLimit = frameLimit(args);

        // The calling thread becomes the UI thread. Goldberry does not spawn one
        // -- AppKit requires the process's first thread for window calls, so the
        // rule is the same on every platform (ADR-0019).
        try (Backend backend = new Sdl3Backend();
                var loop = new EventLoop(backend)) {

            System.out.println("Goldberry showcase — " + backend.name()
                    + ", SDL " + ((Sdl3Backend) backend).sdlVersion());

            var window = backend.createWindow(
                    WindowSpec.of("Goldberry — showcase", LogicalSize.of(960f, 640f)));

            System.out.println("window " + window.size() + " at " + window.scale()
                    + " → " + window.physicalSize());

            // Something to prove the UI thread is not where work belongs. It
            // finishes on a virtual thread and its result arrives back on the UI
            // thread, which is the only thread allowed to touch the window.
            loop.supplyAsync(Showcase::describeEnvironment)
                    .thenAccept(text -> window.setTitle("Goldberry — " + text));

            window.requestFrame();

            var painted = new int[1];
            loop.run(event -> {
                switch (event) {
                    case BackendEvent.FrameDue frame -> {
                        paint(frame.window().physicalSize(), frame.window());
                        painted[0]++;
                        System.out.println("painted frame " + painted[0]);
                        if (frameLimit <= 0) {
                            // Interactive: nothing else to draw until something
                            // changes. An idle window costs no frames, which is
                            // the whole reason frames are requested rather than
                            // produced on a timer.
                            return;
                        }
                        if (painted[0] >= frameLimit) {
                            System.out.println("painted " + painted[0] + " frame(s); exiting");
                            loop.stop();
                        } else {
                            frame.window().requestFrame();
                        }
                    }
                    case BackendEvent.Exposed exposed -> exposed.window().requestFrame();
                    case BackendEvent.Resized resized -> {
                        System.out.println("resized to " + resized.size()
                                + " → " + resized.physicalSize());
                        resized.window().requestFrame();
                    }
                    case BackendEvent.ScaleChanged rescaled -> {
                        System.out.println("scale is now " + rescaled.scale()
                                + " → " + rescaled.physicalSize());
                        rescaled.window().requestFrame();
                    }
                    case BackendEvent.CloseRequested closed -> {
                        System.out.println("closing");
                        closed.window().close();
                    }
                }
            });
        }
    }

    /// Fills the window with a flat colour.
    ///
    /// Written by hand for now. Blend2D takes this over in M1, and the only thing
    /// that changes is where the buffer comes from — `present` already takes the
    /// shape a rasterizer produces.
    private static void paint(PhysicalSize size, io.github.digitalsmile.goldberry.backend.BackendWindow window) {
        var frame = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
        var pixels = frame.pixels();
        for (var i = 0; i + 3 < pixels.capacity(); i += 4) {
            pixels.putInt(i, BACKGROUND);
        }
        window.present(frame, List.of(DamageRect.all(size)));
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
