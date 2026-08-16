package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.FontFace;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Goldberry's showcase.
///
/// It opens a window, lays a flexbox tree out with Yoga, and draws it with
/// Blend2D — including a paragraph HarfBuzz shaped, whose height is what the
/// layout is built around. That is M0 and the whole of M1's vertical slice: the
/// superbuild links, the bindings are right, the SPI holds, a window appears at
/// the display's real pixel density, and the text in it re-wraps as the window is
/// dragged. The widget catalog and the CSS engine arrive on top of this file,
/// not beside it.
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
    private static final int ON_ACCENT = 0xFF2E3440;
    private static final int ON_PANEL = 0xFFECEFF4;

    /// The bar's height and the body's padding, in points. These are the only
    /// sizes written down: everything else comes from the content, which is what
    /// having a measure function buys.
    private static final float BAR_HEIGHT = 32;
    private static final float PADDING = 16;

    /// The icons in the sidebar, in logical pixels. A third number, and the only
    /// one added since ADR-0036 — an icon has no intrinsic size to take from
    /// content the way a paragraph does.
    private static final float ICON_SIZE = 24;

    /// The prose the body wraps. Long on purpose: the point of the window is to
    /// be resized, and wrapping is the thing to watch while it is.
    private static final String BODY_TEXT =
            "Yoga proposes a width and this paragraph answers with a height, which is the only"
                    + " thing a flexbox algorithm needs to know about text. The answer comes back"
                    + " through a Java method called from C returning a struct by value — the"
                    + " fiddliest thing the toolkit asks of the Foreign Function & Memory API, and"
                    + " the reason ADR-0017 exists.\n\n"
                    + "Drag the window's edge. The text is shaped once, when this paragraph is"
                    + " built; every re-wrap after that is arithmetic over the glyphs that shaping"
                    + " already produced.";

    private Showcase() {
    }

    /// The layout the window paints, built once and reused every frame.
    ///
    /// Flexbox through Yoga, filled and drawn through Blend2D, in logical
    /// coordinates throughout. The body is a **measured leaf**: its height is
    /// whatever its text wraps to at the width the sidebar leaves it, so the
    /// layout is decided by the content rather than by a number written here
    /// (ADR-0036).
    private static Box layout(Paragraph title, Paragraph body) {
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .background(BACKGROUND)
                .children(
                        // A 32-point bar across the top, whatever the display
                        // scale, with its title centred in it.
                        Box.of()
                                .background(ACCENT)
                                .size(StyleLength.UNDEFINED, StyleLength.points(BAR_HEIGHT))
                                .alignItems(Align.CENTER)
                                .padding(StyleLength.points(PADDING))
                                .children(Box.text(title, ON_ACCENT)),
                        Box.of()
                                .grow(1)
                                .direction(FlexDirection.ROW)
                                .padding(StyleLength.points(PADDING))
                                .gap(StyleLength.points(PADDING))
                                .children(
                                        // A quarter-width sidebar and a body
                                        // that takes what is left.
                                        Box.filled(PANEL).size(
                                                StyleLength.percent(25), StyleLength.UNDEFINED),
                                        Box.of()
                                                .grow(1)
                                                .direction(FlexDirection.COLUMN)
                                                .background(MUTED)
                                                .padding(StyleLength.points(PADDING))
                                                .children(Box.text(body, ON_PANEL))));
    }

    public static void main(String[] args) {
        var frameLimit = frameLimit(args);
        var painted = new int[1];

        LOG.info("Goldberry {} — showcase starting", Goldberry.version());

        var window = Window.open("Goldberry — showcase", widthOf(args), heightOf(args));

        // A crosshair over the whole window, which is not decoration: it is the
        // only thing in this repository that makes SDL_CreateSystemCursor and
        // SDL_SetCursor actually run. CI drives this showcase under Xvfb on all
        // three platforms, so the cursor path is exercised there rather than left
        // as a binding nothing has ever called (ADR-0057).
        window.cursor(Cursor.CROSSHAIR);

        // On the UI thread, and it has to stay there: these own native objects
        // from two libraries and are confined to the thread that built them.
        // This is that thread — Window.open runs on it, and so does every paint.
        //
        // One face, two sizes. Inter is parsed once and its bytes are held once
        // per library rather than once per size, which is three megabytes here
        // instead of six (ADR-0044). Everything is held for the life of the
        // window: building any of it per frame would put font parsing on the
        // frame path.
        var uiFace = FontFace.bundled(BundledFont.UI);
        var titleFont = Font.on(uiFace, 16);
        var bodyFont = Font.on(uiFace, 14);

        // Shaped once, here, and re-wrapped on every resize without being shaped
        // again (ADR-0036). Building these in the paint callback would put font
        // parsing and shaping on the frame path.
        var layout = layout(
                Paragraph.of(titleFont, "Goldberry"),
                Paragraph.of(bodyFont, BODY_TEXT));

        // One icon per size, for the same reason there is one Font per size: the
        // path is built scaled, so nothing is transformed at draw time
        // (ADR-0043). Three of Lucide's 1544, down the sidebar.
        var icons = List.of(
                Icon.bundled("layout-dashboard", ICON_SIZE),
                Icon.bundled("type", ICON_SIZE),
                Icon.bundled("palette", ICON_SIZE));

        window.onPaint(frame -> {
            // Yoga lays the tree out at the frame's logical size, asking the
            // paragraphs how tall they came out, and Blend2D draws the result.
            // Logical coordinates throughout: the bar is 32 points tall and the
            // sidebar a quarter of the width on every display, and nothing here
            // knows whether the screen runs at 100% or 150%.
            BoxPainter.paint(frame, layout);

            // Drawn over the sidebar rather than laid out in it: an icon is not
            // a box yet, because nothing decides what its intrinsic size is
            // until the widget model does (ADR-0004). Positioned against the
            // same two numbers the layout uses, so it moves with the bar.
            for (var i = 0; i < icons.size(); i++) {
                icons.get(i).draw(
                        frame,
                        PADDING * 2,
                        BAR_HEIGHT + PADDING * 2 + i * (ICON_SIZE + PADDING),
                        ON_PANEL);
            }

            painted[0]++;
            // Not every frame. This runs *inside* the paint callback, so it is
            // inside what `Window` reports as paint time — at 300 frames a
            // console write per frame was 0.4 ms of the measurement, which is
            // the instrument changing the reading (ADR-0045).
            if (painted[0] <= 3 || painted[0] % 50 == 0) {
                LOG.info("painted frame {} at {}", painted[0], frame.pixelSize());
            }

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

        try {
            Goldberry.run();
        } finally {
            // After the loop, not in a try-with-resources around it: the paint
            // callback holds both fonts and the icons, and runs until `run`
            // returns.
            icons.forEach(Icon::close);
            // Sizes first, then the face they share: closing the face while a
            // font still holds it leaves Blend2D reading unmapped memory.
            bodyFont.close();
            titleFont.close();
            uiFace.close();

            // And then hand the window back before the process goes away.
            //
            // This is not tidiness. `Goldberry.stop()` ends the loop with the
            // window still open, so without this the process exits with a live
            // Wayland surface and SDL never quit: no xdg_toplevel.destroy, no
            // wl_surface.destroy, no SDL_Quit. The compositor finds out its
            // client is gone when the socket closes, and has to unwind a
            // connection that never said goodbye.
            //
            // A compositor must survive that -- every killed process does it --
            // and GNOME 46's Mutter does not always: it crashed in
            // wl_client_destroy -> its destroy listener -> g_signal_handler_disconnect
            // while cleaning up after exactly this exit. Disconnecting properly
            // is right regardless of whose bug that is.
            Goldberry.shutdown();
        }
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
