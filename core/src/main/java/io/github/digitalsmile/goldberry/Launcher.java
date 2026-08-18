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
        // §7's tooltip: shown on hover *and* on keyboard focus, after a delay.
        // The router knows when either moved and opens nothing; the launcher owns
        // the window, so it is where the two meet (ADR-0105).
        router.onPointingChanged(this::pointingChanged);

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
            public boolean keyPressed(io.github.digitalsmile.goldberry.input.Key key,
                    io.github.digitalsmile.goldberry.input.Modifiers modifiers, boolean repeat) {
                var top = topmostPopup();
                if (top == null) {
                    return false;
                }
                if (key == io.github.digitalsmile.goldberry.input.Key.ESCAPE) {
                    dismissPopups();
                    return true;
                }
                // While a menu is open the keyboard belongs to it, whether or not
                // the platform moved focus there — otherwise an arrow would move
                // the selection in the window *underneath* the menu (ADR-0104).
                return top.handleKey(key, modifiers, repeat);
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

    /// The renderer for this frame, built if the stylesheets have moved.
    ///
    /// Called from the paint path and from [#popup], which is why it is a method:
    /// a popup opened from `Application#start` has to measure its content, and
    /// measuring needs a renderer before the first frame has asked for one.
    ///
    /// A new renderer means new resolved styles — but **not** new animations,
    /// which live on the elements a restyle does not touch, so a transition in
    /// flight when the theme changes carries on into the new colours rather than
    /// snapping (ADR-0067).
    private WidgetRenderer renderer() {
        if (stylesDirty || renderer == null) {
            renderer = new WidgetRenderer(application.stylesheets(), fonts)
                    .frames(window.frames());
            stylesDirty = false;
        }
        return renderer;
    }

    private void paint(io.github.digitalsmile.goldberry.Frame frame) {
        // Every setState since the last frame settles here, once, however many of
        // them there were (ADR-0052).
        if (tree.needsBuild()) {
            tree.flush();
        }
        renderer();

        // One layout pass, two readers. `update` reconciles the retained render
        // tree against this frame's description and lays it out; the paint and the
        // hit-test snapshot both read that one result (ADR-0069).
        render.update(frame, renderer().render(tree));

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
        if (renderer().isAnimating()) {
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

    // --- tooltips -----------------------------------------------------------

    /// How long the pointer has to rest on something before its tooltip appears.
    ///
    /// Long enough not to fire while the pointer is crossing a toolbar on its way
    /// somewhere, short enough that someone who stopped to read is not left
    /// waiting. `docs/core-widgets.md` §7 says "after delay" and does not say how
    /// long; this is the figure the desktop conventions agree on.
    private static final java.time.Duration TOOLTIP_DELAY = java.time.Duration.ofMillis(500);

    /// The tooltip that is showing, or null.
    private Popup tooltip;

    /// The node it belongs to, so a hover that returns to the same widget does
    /// not close and reopen it.
    private io.github.digitalsmile.goldberry.widget.Element tooltipOwner;

    /// The delay in flight, cancelled by anything that moves.
    private io.github.digitalsmile.goldberry.backend.EventLoop.Timer tooltipTimer;

    /// The pointer moved to a different node, or focus did.
    ///
    /// Either can summon a tooltip and either can dismiss one, which is why there
    /// is one handler: a keyboard user tabbing along a toolbar gets the same
    /// tooltips a pointer user does, and §7 asks for exactly that.
    private void pointingChanged() {
        var target = tooltipTarget();
        if (target == tooltipOwner) {
            return;
        }
        hideTooltip();
        tooltipOwner = target;
        if (target == null) {
            return;
        }
        // A fresh delay per node, cancelled by the next move. The pointer
        // crossing five buttons on its way to a sixth schedules five timers and
        // fires none of them.
        tooltipTimer = after(TOOLTIP_DELAY, this::showTooltip);
    }

    /// The node whose tooltip should show: what the pointer is on, or — when the
    /// pointer is on nothing — what the keyboard is on.
    ///
    /// The pointer wins because it is the more recent statement of intent: a
    /// keyboard user who reaches for the mouse is looking at where the mouse is.
    private io.github.digitalsmile.goldberry.widget.Element tooltipTarget() {
        var hovered = withTooltip(router.hovered());
        return hovered != null ? hovered : withTooltip(router.focused());
    }

    /// `element`, or the nearest ancestor of it that has a tooltip.
    ///
    /// Walked upwards because a tooltip on a `button` has to survive the pointer
    /// being over the button's *label*, which is a different element and the one a
    /// hit test reports.
    private io.github.digitalsmile.goldberry.widget.Element withTooltip(
            io.github.digitalsmile.goldberry.widget.Element element) {
        for (var node = element; node != null;
                node = node.parent() instanceof io.github.digitalsmile.goldberry.widget.Element parent
                        ? parent : null) {
            if (tooltipTextOf(node) != null) {
                return node;
            }
        }
        return null;
    }

    private static String tooltipTextOf(io.github.digitalsmile.goldberry.widget.Element element) {
        return element.widget() instanceof io.github.digitalsmile.goldberry.widget.Attributed<?> a
                ? a.attributes().tooltip()
                : null;
    }

    /// Opens the tooltip for whatever [#pointingChanged] last settled on.
    private void showTooltip() {
        tooltipTimer = null;
        var target = tooltipOwner;
        if (target == null || !target.isMounted()) {
            return;
        }
        var text = tooltipTextOf(target);
        var anchor = anchorOf(target);
        if (text == null || anchor.isEmpty()) {
            return;
        }
        // Above by preference and flipped below near the top of the screen, which
        // is `Placement`'s to decide. Never focusable, and never light-dismissed
        // by a press: a tooltip is dismissed by the pointer leaving, and a press
        // that closed it would close it in the same gesture that opened whatever
        // was clicked.
        tooltip = tooltipPopup(
                new io.github.digitalsmile.goldberry.widget.TooltipPanel(text),
                anchor.get())
                .orElse(null);
    }

    private void hideTooltip() {
        if (tooltipTimer != null) {
            tooltipTimer.cancel();
            tooltipTimer = null;
        }
        if (tooltip != null) {
            tooltip.close();
            tooltip = null;
        }
    }

    /// The painted rectangle of an element, by identity — [#anchor] by id, for
    /// the case where the caller has the element itself.
    private java.util.Optional<io.github.digitalsmile.goldberry.backend.LogicalRect> anchorOf(
            io.github.digitalsmile.goldberry.widget.Element element) {
        for (var region : regions) {
            if (region.owner() == element) {
                return java.util.Optional.of(region.bounds());
            }
        }
        return java.util.Optional.empty();
    }

    /// The popup on top: the last one opened that is still open.
    private Popup topmostPopup() {
        for (var i = popups.size() - 1; i >= 0; i--) {
            if (popups.get(i).isOpen()) {
                return popups.get(i);
            }
        }
        return null;
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
        hideTooltip();
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
        return open(new ElementTree(content), RenderTree.create(),
                new io.github.digitalsmile.goldberry.backend.PopupSpec(at, size, kind));
    }

    /// Asks the backend for the window and wires the trees to it.
    private java.util.Optional<Popup> open(ElementTree tree, RenderTree render,
            io.github.digitalsmile.goldberry.backend.PopupSpec spec) {

        var backend = GoldberryRuntime.get().backend()
                .createPopup(window.backendWindow(), spec);
        if (backend.isEmpty()) {
            // The driver has none. The caller's fallback is the overlay layer,
            // clipped to the window, and saying so is more use than an empty
            // Optional on its own (ADR-0102).
            tree.unmount();
            render.close();
            LOG.info("this platform has no popup windows; a {} will have to be an overlay",
                    spec.kind());
            return java.util.Optional.empty();
        }

        var popup = new Popup(backend.get(), Window.over(backend.get()), tree, render,
                this::renderer, () -> popups.removeIf(open -> !open.isOpen()));
        popups.add(popup);
        return java.util.Optional.of(popup);
    }

    @Override
    public java.util.Optional<Popup> popup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalRect anchor, Placement placement) {
        return placed(content, anchor, placement,
                io.github.digitalsmile.goldberry.backend.PopupKind.MENU);
    }

    /// Measure, place, open — the three steps `popover` is made of (ADR-0104),
    /// shared by the menu form and the tooltip one because only the kind differs.
    private java.util.Optional<Popup> placed(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalRect anchor, Placement placement,
            io.github.digitalsmile.goldberry.backend.PopupKind kind) {

        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(placement, "placement");

        // Built once and handed to the popup: measuring throws the layout away
        // otherwise, and the element tree is what carries state, so a second one
        // would also be a second lot of `initState`.
        var tree = new ElementTree(content);
        var render = RenderTree.create();
        var size = measure(tree, render);
        var placed = placement.place(anchor, size, placeableArea());
        return open(tree, render,
                new io.github.digitalsmile.goldberry.backend.PopupSpec(placed.at(), size, kind));
    }

    /// A tooltip's popup: above by preference, never light-dismissed.
    ///
    /// Not light-dismissed because a tooltip is dismissed by the pointer leaving,
    /// and a press that closed it would fire in the same gesture as the click on
    /// whatever it is describing — closing it a moment before it was going to
    /// close anyway, and taking the *next* tooltip's timer with it.
    private java.util.Optional<Popup> tooltipPopup(Widget content,
            io.github.digitalsmile.goldberry.backend.LogicalRect anchor) {
        return placed(content, anchor, Placement.ABOVE.align(Placement.Align.CENTER),
                io.github.digitalsmile.goldberry.backend.PopupKind.TOOLTIP)
                .map(popup -> popup.lightDismiss(false));
    }

    @Override
    public java.util.Optional<Popup> popup(Widget content, String anchorId, Placement placement) {
        Objects.requireNonNull(anchorId, "anchorId");
        var anchor = anchor(anchorId);
        if (anchor.isEmpty()) {
            LOG.warn("nothing with id \"{}\" has been painted, so there is nothing to anchor to",
                    anchorId);
            return java.util.Optional.empty();
        }
        return popup(content, anchor.get().bounds(), placement);
    }

    /// The content's own size, capped at the window's width — in **two passes**,
    /// and the second one is why.
    ///
    /// Yoga lays a root out at exactly the available size when that size is
    /// definite: there is no parent to be "at most" of, so a bound and a target
    /// are the same number. Measuring a menu against the window therefore returns
    /// the window — which is what the first two attempts at this did, once in each
    /// axis, and both looked like a placement bug rather than a measurement one.
    ///
    /// So: measure with **nothing** definite, which gives the content's natural
    /// size, and only if that is wider than the window measure again with the
    /// width pinned — where a definite width is now what is wanted, and a
    /// paragraph wraps at it instead of running off the side. A second pass over a
    /// menu is a few dozen Yoga nodes and it happens once, when the popup opens.
    ///
    /// The height is never bounded here. A menu taller than the screen is
    /// [Placement]'s to clamp, and it can only clamp a number that means the
    /// content.
    private io.github.digitalsmile.goldberry.backend.LogicalSize measure(
            ElementTree tree, RenderTree render) {
        var box = renderer().render(tree);
        var natural = render.measure(box, window.scale(), Float.NaN, Float.NaN);
        var cap = window.size().width();
        if (natural.width() <= cap) {
            return natural;
        }
        return render.measure(box, window.scale(), cap, Float.NaN);
    }

    /// Where a popup is allowed to be, **in this window's coordinates**.
    ///
    /// The display's work area translated by the window's position on the
    /// desktop, because a placement policy works in one space and an anchor is in
    /// this one.
    ///
    /// When the platform will not say where the window is or what the work area
    /// is — a headless run, or a driver that does not know — the window's own
    /// bounds stand in. That is a worse answer and not a wrong one: a popup kept
    /// inside its owner is always on the screen.
    private io.github.digitalsmile.goldberry.backend.LogicalRect placeableArea() {
        var backendWindow = window.backendWindow();
        var origin = backendWindow.position();
        var area = backendWindow.workArea();
        if (origin.isEmpty() || area.isEmpty()) {
            return new io.github.digitalsmile.goldberry.backend.LogicalRect(
                    io.github.digitalsmile.goldberry.backend.LogicalPoint.ZERO, window.size());
        }
        return area.get().offsetBy(-origin.get().x(), -origin.get().y());
    }

    @Override
    public io.github.digitalsmile.goldberry.backend.EventLoop.Timer after(
            java.time.Duration delay, Runnable action) {
        return GoldberryRuntime.get().loop().after(delay, action);
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
