package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Runs an [Application]: the window, the trees, the frame loop and the shutdown
/// order, in one place instead of in every `main`.
///
/// Not public — [Goldberry#launch] is the door. There is exactly one right way to
/// wire these six objects together and no reason for an application to hold a
/// launcher, so what it gets is a [Host]
/// ([ADR-0093](../../../../../book/src/adr/0093-an-application-is-a-root-widget.md)).
final class Launcher implements Host {

    private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

    private final Application application;
    private final Options options;

    private Window window;
    private Fonts fonts;
    private ElementTree tree;
    private RenderTree render;
    private PointerRouter router;
    private WidgetRenderer renderer;

    /// Set when the stylesheets must be re-read — see [#restyle()].
    private boolean stylesDirty = true;

    private int painted;

    /// The two flags the launcher understands on the command line, both for CI.
    ///
    /// A toolkit that parsed argv would be overstepping; these are read only from
    /// the array an application chose to hand over, and an application that calls
    /// [Goldberry#launch(Application)] passes none.
    ///
    /// @param frames paint this many frames and exit, or 0 to run until closed —
    ///               which is how a headless run proves a window opened without a
    ///               human to close it
    /// @param size   the opening size, or null for the application's own
    record Options(int frames, LogicalSize size) {

        static final Options NONE = new Options(0, null);

        /// Reads `--frames=N` and `--size=WxH`, ignoring everything else — an
        /// application's own arguments are its business.
        static Options of(String[] args) {
            var frames = 0;
            LogicalSize size = null;
            for (var arg : args == null ? new String[0] : args) {
                if (arg.startsWith("--frames=")) {
                    frames = Integer.parseInt(arg.substring("--frames=".length()));
                } else if (arg.startsWith("--size=")) {
                    var parts = arg.substring("--size=".length()).split("x");
                    if (parts.length == 2) {
                        size = new LogicalSize(
                                Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
                    }
                }
            }
            return new Options(frames, size);
        }
    }

    Launcher(Application application, Options options) {
        this.application = Objects.requireNonNull(application, "application");
        this.options = options == null ? Options.NONE : options;
    }

    /// Opens the window, runs the loop, and takes everything down in the reverse
    /// order it was built.
    void run() {
        LOG.info("Goldberry {} — starting {}", Goldberry.version(),
                application.getClass().getSimpleName());

        var size = options.size() == null ? application.size() : options.size();
        window = Window.open(application.title(), size.width(), size.height());

        // On the UI thread and staying there: the book owns native objects from
        // two libraries, confined to the thread that built them, and opening a
        // face per frame would put font parsing on the frame path.
        fonts = Fonts.bundled();

        router = new PointerRouter();
        window.pointerRouter(router);

        // Before `root()`, so an application can open its icons and bind its
        // accelerators and then describe a tree that uses them.
        application.start(this);

        tree = new ElementTree(application.root());
        router.focusRoot(tree.root());

        // Held for the life of the window, which is the whole point of it: the
        // Yoga nodes, their layout cache and the measure callbacks behind every
        // paragraph survive from frame to frame, so a frame where nothing changed
        // re-lays out nothing (ADR-0069).
        render = RenderTree.create();

        window.onPaint(this::paint);

        try {
            Goldberry.run();
        } finally {
            shutDown();
        }
        LOG.info("{} finished after {} frame(s)",
                application.getClass().getSimpleName(), painted);
    }

    private void paint(io.github.digitalsmile.goldberry.Frame frame) {
        // Every setState since the last frame settles here, once, however many of
        // them there were (ADR-0052).
        if (tree.needsBuild()) {
            tree.flush();
        }
        if (stylesDirty) {
            // A new renderer means new resolved styles -- but **not** new
            // animations, which live on the elements a restyle does not touch, so
            // a transition in flight when the theme changes carries on into the
            // new colours rather than snapping (ADR-0067).
            renderer = new WidgetRenderer(application.stylesheets(), fonts);
            stylesDirty = false;
        }

        // One layout pass, two readers. `update` reconciles the retained render
        // tree against this frame's description and lays it out; the paint and the
        // hit-test snapshot both read that one result (ADR-0069).
        render.update(frame, renderer.render(tree));

        // What differs from the last frame, computed before painting because the
        // clip has to be in place before anything is drawn (ADR-0072).
        var damage = render.damage(frame);
        if (window.canRepaintPartially()) {
            render.paint(frame, damage);
        } else {
            render.paint(frame);
        }
        window.damaged(damage);

        // What the pointer is tested against is the frame that was just painted,
        // not a fresh layout -- which would be one frame ahead of what the user
        // can see (ADR-0054).
        router.updateRegions(HitTest.capture(render));

        // §1.7's "the frame loop is fully idle when no animation is active": ask
        // for another frame *only* while something is moving.
        if (renderer.isAnimating()) {
            window.repaint();
        }

        painted++;
        if (options.frames() > 0) {
            if (painted >= options.frames()) {
                LOG.info("painted {} frame(s); exiting", painted);
                Goldberry.stop();
            } else {
                window.repaint();
            }
        }
    }

    /// The reverse of the build order, and the ordering is the reason this class
    /// exists.
    ///
    /// After the loop rather than in a try-with-resources around it: the paint
    /// callback holds the renderer and the fonts and runs until `run` returns. And
    /// the render tree goes before the fonts, because a render object holds a Yoga
    /// measure callback that closes over a paragraph, and a paragraph over a font
    /// — closing them the other way round leaves Blend2D reading unmapped memory.
    private void shutDown() {
        if (tree != null) {
            tree.unmount();
        }
        if (render != null) {
            render.close();
        }
        // After the tree, so a widget still holding an icon has already gone.
        application.stop();
        if (fonts != null) {
            fonts.close();
        }
        // And hand the window back before the process goes away. Not tidiness:
        // `Goldberry.stop()` ends the loop with the window still open, so without
        // this the process exits with a live Wayland surface and SDL never quit --
        // which GNOME 46's Mutter does not always survive (ADR-0085).
        Goldberry.shutdown();
    }

    // --- Host ---------------------------------------------------------------

    @Override
    public void repaint() {
        window.repaint();
    }

    @Override
    public void restyle() {
        stylesDirty = true;
        window.repaint();
    }

    @Override
    public void title(String title) {
        window.title(title);
    }

    @Override
    public void shortcut(io.github.digitalsmile.goldberry.input.Shortcut accelerator,
            Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public Fonts fonts() {
        return fonts;
    }

    @Override
    public Window window() {
        return window;
    }
}
