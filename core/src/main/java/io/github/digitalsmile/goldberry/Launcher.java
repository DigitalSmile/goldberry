package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WindowRoot;
import java.util.ArrayList;
import java.util.List;
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

    /// What is floating over the content, watched by [WindowRoot] rather than
    /// handed to it: the root widget of an element tree cannot be swapped, so a
    /// list that changes has to be one the root *reads* (ADR-0062).
    private final Property<List<Overlay>> overlays = Property.of(List.of());

    /// The popups this window has open. Light-dismissed together on a press or an
    /// `Escape` in the window below them, which is the input their own routers
    /// never see.
    private final List<Popup> popups = new ArrayList<>();

    /// The last painted frame's geometry — see [#anchor].
    private List<HitTest.Region> regions = List.of();

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

        // The application's root goes *under* the window's own node, from the
        // first frame and whether or not anything is floating yet: a layer that
        // appeared with the first overlay would re-parent the whole application
        // to show a toast, and re-parenting is what throws state away.
        tree = new ElementTree(new WindowRoot(application.root(), overlays));
        router.focusRoot(tree.root());

        // Held for the life of the window, which is the whole point of it: the
        // Yoga nodes, their layout cache and the measure callbacks behind every
        // paragraph survive from frame to frame, so a frame where nothing changed
        // re-lays out nothing (ADR-0069).
        render = RenderTree.create();

        window.onPaint(this::paint);

        // A press on nothing, or an Escape, closes whatever is open over this
        // window. Neither reaches a widget, which is why it is watched here
        // rather than handled by one (ADR-0103).
        window.inputWatcher(new Window.InputWatcher() {
            @Override
            public void pressed() {
                dismissPopups();
            }

            @Override
            public void keyPressed(io.github.digitalsmile.goldberry.input.Key key) {
                if (key == io.github.digitalsmile.goldberry.input.Key.ESCAPE) {
                    dismissPopups();
                }
            }
        });

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
            renderer = new WidgetRenderer(application.stylesheets(), fonts)
                    .frames(window.frames());
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
        // can see (ADR-0054). Kept as well as handed over: `anchor` answers from
        // the same capture, so a menu opens under where its button *was drawn*
        // rather than where a fresh layout would put it.
        regions = HitTest.capture(render);
        router.updateRegions(regions);

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

    /// Closes every light-dismissed popup. Copied first: closing one removes it
    /// from the list it is being iterated over.
    private void dismissPopups() {
        if (popups.isEmpty()) {
            return;
        }
        for (var popup : List.copyOf(popups)) {
            popup.dismissedByInput();
        }
        popups.removeIf(popup -> !popup.isOpen());
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
        // Before the window's own trees: a popup holds a render tree of its own
        // over the same fonts, and the fonts go last.
        for (var popup : List.copyOf(popups)) {
            popup.close();
        }
        popups.clear();
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
    public Overlay overlay(Widget widget, Corner corner) {
        return overlay(widget, corner, Overlay.WINDOW_MARGIN);
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner, float margin) {
        var entry = Overlay.of(widget, corner, margin);
        var next = new ArrayList<>(overlays.get());
        next.add(entry);
        // A fresh list rather than a mutation of the one in the property: the
        // root element is subscribed to the *value*, and a list changed in place
        // is the same value, so nothing would rebuild.
        overlays.set(List.copyOf(next));
        // By identity, not by equality: two `new Hud()` overlays in one corner
        // are equal values and two different things on screen.
        entry.attached(() -> {
            var remaining = new ArrayList<Overlay>(overlays.get().size());
            for (var existing : overlays.get()) {
                if (existing != entry) {
                    remaining.add(existing);
                }
            }
            overlays.set(List.copyOf(remaining));
            window.repaint();
        });
        window.repaint();
        return entry;
    }

    @Override
    public java.util.Optional<HitTest.Region> anchor(String id) {
        Objects.requireNonNull(id, "id");
        for (var region : regions) {
            if (region.owner() instanceof io.github.digitalsmile.goldberry.widget.Element element
                    && element.widget() instanceof io.github.digitalsmile.goldberry.widget.Styled styled
                    && id.equals(styled.id())) {
                return java.util.Optional.of(region);
            }
        }
        return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size) {
        return popup(content, at, size,
                io.github.digitalsmile.goldberry.backend.PopupKind.MENU);
    }

    @Override
    public java.util.Optional<Popup> tooltip(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size) {
        return popup(content, at, size,
                io.github.digitalsmile.goldberry.backend.PopupKind.TOOLTIP);
    }

    private java.util.Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalPoint at,
            io.github.digitalsmile.goldberry.backend.LogicalSize size,
            io.github.digitalsmile.goldberry.backend.PopupKind kind) {

        Objects.requireNonNull(content, "content");
        var spec = new io.github.digitalsmile.goldberry.backend.PopupSpec(at, size, kind);
        var backend = GoldberryRuntime.get().backend()
                .createPopup(window.backendWindow(), spec);
        if (backend.isEmpty()) {
            // The driver has none. The caller's fallback is the overlay layer,
            // clipped to the window, and saying so is more use than an empty
            // Optional on its own (ADR-0102).
            LOG.info("this platform has no popup windows; a {} will have to be an overlay", kind);
            return java.util.Optional.empty();
        }

        var popup = new Popup(backend.get(), Window.over(backend.get()), content,
                () -> renderer, () -> popups.removeIf(open -> !open.isOpen()));
        popups.add(popup);
        return java.util.Optional.of(popup);
    }

    @Override
    public FrameStats frames() {
        return window.frames();
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
