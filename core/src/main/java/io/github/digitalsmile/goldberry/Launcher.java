package io.github.digitalsmile.goldberry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.drive.FrameBudgetException;
import io.github.digitalsmile.goldberry.drive.ResizeWalk;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.desktop.SystemTheme;
import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogSpec;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogs;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.popup.PopupKind;
import io.github.digitalsmile.goldberry.render.popup.PopupSpec;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;
import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.root.WindowRoot;
import io.github.digitalsmile.goldberry.widget.style.Corner;

/// Runs an [Application]: the window, the trees, the frame loop and the shutdown
/// order, in one place instead of in every `main`.
///
/// Not public — [Goldberry#launch] is the door. There is exactly one right way to
/// wire these six objects together and no reason for an application to hold a
/// launcher, so what it gets is a [Host]
/// (ADR-0093).
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

    /// The one clock this window runs on.
    ///
    /// A field rather than the renderer's default, because two things read it and
    /// they must agree: the renderer times transitions against it, and [#clock()]
    /// hands it to widgets that need to know how long ago a keystroke arrived. A
    /// renderer left on its own `Clock.system()` would be a second clock -- the
    /// same defect `frameNow` exists to prevent one level down.
    ///
    /// It is also what a restyle must not disturb: `renderer()` rebuilds the
    /// renderer whenever the sheets change, and a clock rebuilt with it would
    /// restart every transition in flight when the theme switched.
    private final io.github.digitalsmile.goldberry.motion.Clock clock =
            io.github.digitalsmile.goldberry.motion.Clock.system();

    /// Set when the stylesheets must be re-read — see [#restyle()].
    private boolean stylesDirty = true;

    /// What is floating over the content, watched by [WindowRoot] rather than
    /// handed to it: the root widget of an element tree cannot be swapped, so a
    /// list that changes has to be one the root *reads* (ADR-0062).
    private final Property<List<Overlay>> overlays = Property.of(List.of());

    /// How a popup was placed, so it can be placed again — see [#replacePopups].
    ///
    /// @param anchorId  the id it was anchored to, or null for a rectangle the
    ///                  caller computed. An id is worth more than a rectangle: it
    ///                  is re-resolved against the frame the *resize* produced, so
    ///                  a menu follows a heading that moved rather than staying
    ///                  where the heading used to be.
    /// @param anchor    the rectangle it was opened against
    /// @param placement the side, alignment and gap it was opened with
    private record Placed(String anchorId, LogicalRect anchor, Placement placement) {}

    /// What [#replacePopups] needs, per open popup.
    ///
    /// An `IdentityHashMap` rather than a `Map`, and the declaration is the
    /// documentation: a `Popup` is looked up by *which popup it is*, and two
    /// popups over the same anchor are two entries.
    private final java.util.IdentityHashMap<Popup, Placed> placements = new java.util.IdentityHashMap<>();

    /// Set when the window is resized, acted on at the end of the next paint —
    /// see [#replacePopups] for why the two are not the same moment.
    private boolean replaceAfterPaint;

    /// Puts every open popup back where its anchor now is.
    ///
    /// Called from the three things that move an anchor without moving the popup
    /// with it ([ADR-0231], [ADR-0270]):
    ///
    /// - a **resize**, which moves the widget the popup hangs off and the work
    ///   area it was clamped against, and tells neither of them;
    /// - a **move**, which moves the screen's edges in this window's own
    ///   coordinates and nothing else — the anchor is where it was, and a menu
    ///   flipped against the old position may not fit at the new one;
    /// - a **frame**, while anything is anchored by id: a `popover` hanging off a
    ///   widget in a `scroll` travels with it, and scrolling is not an event
    ///   anybody reports.
    ///
    /// A popup lives at an offset from its owner, so dragging the window carries
    /// it along — which is why a move re-clamps rather than re-anchors.
    ///
    /// **A move and not a reopen.** `Popup.move` exists for exactly this and is
    /// cheaper: the tree stays mounted, the keyboard stays where it is, and
    /// nothing flickers.
    private void replacePopups() {
        placements.keySet().removeIf(popup -> !popup.isOpen());
        for (var entry : List.copyOf(placements.entrySet())) {
            var popup = entry.getKey();
            var placed = entry.getValue();
            var anchor = placed.anchorId() == null
                    ? placed.anchor()
                    : anchor(placed.anchorId()).map(HitTest.Region::painted).orElse(placed.anchor());
            var at = placed.placement()
                    .place(anchor, popup.bounds().size(), placeableArea())
                    .at();
            if (!at.equals(popup.offset())) {
                popup.move(at);
            }
        }
    }

    /// Whether any open popup was anchored to an **id**, which is the only kind
    /// that can follow anything.
    ///
    /// A popup opened against a rectangle a caller computed has nothing to be
    /// re-resolved against: the rectangle is all there ever was, and re-placing
    /// it every frame would put it back where it already is. An id is a question
    /// the last paint can answer again, and the answer moves when the widget
    /// does ([ADR-0270]).
    private boolean followsAnAnchor() {
        for (var entry : placements.entrySet()) {
            if (entry.getValue().anchorId() != null && entry.getKey().isOpen()) {
                return true;
            }
        }
        return false;
    }

    /// The popups this window has open. Light-dismissed together on a press or an
    /// `Escape` in the window below them, which is the input their own routers
    /// never see.
    private final List<Popup> popups = new ArrayList<>();

    /// The last painted frame's geometry — see [#anchor].
    private List<HitTest.Region> regions = List.of();

    /// The application's models, kept because they are swept once a frame.
    ///
    /// A no-op for a woven model, which notified from inside the assignment that
    /// changed it. For one bound at run time it is how a change made from
    /// somewhere no listener could see — a timer callback, a background job
    /// reporting in — reaches the screen rather than waiting for the next action
    /// (ADR-0155).
    ///
    /// Read once at start-up rather than per frame: `models()` is a description of
    /// the wiring, and an application that returns a fresh list every call would
    /// otherwise allocate one per frame.
    private List<Object> models = List.of();

    private int painted;

    /// The walk `--resize=` asked for, or null when the window is left alone.
    private ResizeWalk resizeWalk;

    /// The four flags the launcher understands on the command line, all for CI.
    ///
    /// A toolkit that parsed argv would be overstepping; these are read only from
    /// the array an application chose to hand over, and an application that calls
    /// [Goldberry#launch(Application)] passes none.
    ///
    /// @param frames     paint this many frames and exit, or 0 to run until
    ///                   closed — which is how a headless run proves a window
    ///                   opened without a human to close it
    /// @param size       the opening size, or null for the application's own
    /// @param resize     walk the window's size a pixel a frame between the
    ///                   opening size and this one, or null to leave it alone —
    ///                   the load a frame-rate claim is measured under
    ///                   ([ADR-0342])
    /// @param lateBudget how many refreshes the run may miss before it exits
    ///                   non-zero, or -1 for a run that is not judged
    record Options(int frames, LogicalSize size, LogicalSize resize, long lateBudget) {

        static final Options NONE = new Options(0, null, null, -1);

        /// The two flags there were before the walk and the budget.
        Options(int frames, LogicalSize size) {
            this(frames, size, null, -1);
        }

        /// Reads `--frames=N`, `--size=WxH`, `--resize=WxH` and
        /// `--late-budget=N`, ignoring everything else — an application's own
        /// arguments are its business.
        static Options of(String[] args) {
            var frames = 0;
            LogicalSize size = null;
            LogicalSize resize = null;
            var lateBudget = -1L;
            for (var arg : args == null ? new String[0] : args) {
                if (arg.startsWith("--frames=")) {
                    frames = whole(arg, arg.substring("--frames=".length()), "frames");
                } else if (arg.startsWith("--size=")) {
                    size = pair(arg, arg.substring("--size=".length()));
                } else if (arg.startsWith("--resize=")) {
                    resize = pair(arg, arg.substring("--resize=".length()));
                } else if (arg.startsWith("--late-budget=")) {
                    lateBudget = whole(arg, arg.substring("--late-budget=".length()), "late frames");
                }
            }
            return new Options(frames, size, resize, lateBudget);
        }

        /// `WxH`, or null for anything that is not two numbers around an `x`.
        private static LogicalSize pair(String flag, String text) {
            // Trailing empties dropped is the behaviour wanted: the length
            // check below is what rejects `--size=800x` anyway.
            @SuppressWarnings("StringSplitter")
            var parts = text.split("x");
            if (parts.length != 2) {
                return null;
            }
            return new LogicalSize(real(flag, parts[0]), real(flag, parts[1]));
        }

        /// `--frames=N`'s `N`, or a refusal that names the flag.
        ///
        /// `NumberFormatException` names the text and not the flag it came from,
        /// and a launcher's argument error is read by whoever typed it.
        private static int whole(String flag, String text, String what) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(flag + " is not a whole number of " + what, e);
            }
        }

        /// One side of `--size=WxH`, or a refusal that names the flag.
        private static float real(String flag, String text) {
            try {
                return Float.parseFloat(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(flag + " is not a width and a height", e);
            }
        }
    }

    Launcher(Application application, Options options) {
        this.application = Objects.requireNonNull(application, "application");
        this.options = options == null ? Options.NONE : options;
    }

    /// [Application#minimumSize], unless the window is opening smaller than it.
    ///
    /// The contradiction is only reachable through `--size=`, and that flag is
    /// the reason this is a demotion rather than a refusal: it exists so a
    /// screenshot or a golden run can pin the window's geometry (ADR-0221), and
    /// an application that declares a 1024-wide minimum must not be able to make
    /// `--size=800x600` fail to start. The floor is a promise to a *user* about
    /// what dragging an edge may do, and there is no user in a golden run.
    ///
    /// Said out loud rather than dropped quietly: the next person to wonder why
    /// their window resizes past its minimum is running with a `--size=`.
    private LogicalSize floorFitting(LogicalSize opening) {
        var minimum = application.minimumSize();
        if (minimum.width() <= opening.width() && minimum.height() <= opening.height()) {
            return minimum;
        }
        LOG.warn(
                "--size={} is smaller than the {} minimum {} asks for; opening without a minimum size",
                opening,
                minimum,
                application.getClass().getSimpleName());
        return WindowSpec.NO_MINIMUM;
    }

    /// Opens the window, runs the loop, and takes everything down in the reverse
    /// order it was built.
    void run() {
        LOG.info(
                "Goldberry {} — starting {}",
                Goldberry.version(),
                application.getClass().getSimpleName());

        var size = options.size() == null ? application.size() : options.size();
        // `--size=` un-maximizes as well as resizing, because an explicit size on
        // the command line and a window that ignores it is the one combination
        // nobody means: the flag exists so a screenshot or a golden run can pin
        // the window's geometry (ADR-0221).
        window = Window.open(WindowSpec.of(application.title(), size)
                .withMinimumSize(floorFitting(size))
                .withMaximized(application.maximized() && options.size() == null));
        if (options.resize() != null) {
            resizeWalk = new ResizeWalk(size, options.resize());
            LOG.info("walking the window's size a pixel a frame: {}", resizeWalk);
        }

        // On the UI thread and staying there: the book owns native objects from
        // two libraries, confined to the thread that built them, and opening a
        // face per frame would put font parsing on the frame path.
        fonts = Fonts.bundled();

        router = new PointerRouter();
        window.pointerRouter(router);
        // §7's tooltip: shown on hover *and* on keyboard focus, after a delay.
        // The router knows when either moved and opens nothing; the launcher owns
        // the window, so it is where the two meet (ADR-0105).
        // Held rather than dropped: the registration outlives this line, and a
        // returned handle nobody keeps is the shape that makes a leak invisible
        // (ADR-0230). The launcher's own router dies with the launcher, so
        // closing it is tidiness rather than necessity — and tidiness is what
        // stops the next caller from thinking it does not have to.
        pointing = router.onPointingChanged(this::pointingChanged);

        // Before `root()`, so an application can open its icons and bind its
        // accelerators and then describe a tree that uses them.
        application.start(this);

        // The application's models drive the window, and it says nothing about
        // it: a change to a bound field asks for a frame, and a change to one
        // declared `@Bind(restyle = true)` drops the resolved styles first
        // (ADR-0128, ADR-0133).
        models = List.copyOf(application.models());
        for (var model : models) {
            io.github.digitalsmile.goldberry.bind.runtime.Models.onRestyle(model, this::restyle);
            io.github.digitalsmile.goldberry.bind.runtime.Models.onRepaint(model, window::repaint);
        }

        // The application's root goes *under* the window's own node, from the
        // first frame and whether or not anything is floating yet: a layer that
        // appeared with the first overlay would re-parent the whole application
        // to show a toast, and re-parenting is what throws state away.
        tree = new ElementTree(new WindowRoot(application.root(), overlays), this);
        // A `setState` anywhere in the tree asks for a frame. Without it the
        // change waits for some unrelated event to paint, which is a widget that
        // reacts one interaction late (ADR-0122).
        tree.onDirty(window::repaint);
        router.focusRoot(tree.root());

        // Held for the life of the window, which is the whole point of it: the
        // Yoga nodes, their layout cache and the measure callbacks behind every
        // paragraph survive from frame to frame, so a frame where nothing changed
        // re-lays out nothing (ADR-0069).
        render = RenderTree.create();

        window.onPaint(this::paint);

        // A popup is positioned as an offset from its owner, so **moving** the
        // window carries it along and only **resizing** moves what it was
        // anchored to. Re-placing here is what stops a menu opened at the bottom
        // of a short window from hanging off a taller one, and what keeps a
        // right-aligned heading's menu under the heading ([ADR-0231]).
        // In the launcher's own hook and not the application's slot: this runs
        // after `start`, and taking `onResize` here replaced whatever the
        // application had just wired into it (ADR-0342).
        window.launcherOnResize(resized -> replaceAfterPaint = true);

        // A **move** does not move the anchor and does not need a paint: the
        // frame on screen is still the right one, and `anchor(id)` answers from
        // it. What moved is the work area *in this window's coordinates*, so a
        // menu that was flipped or shifted against a screen edge has to be asked
        // again — immediately, from the capture that is already current
        // ([ADR-0270]).
        window.launcherOnMove(position -> replacePopups());

        // The desktop's light-or-dark setting, forwarded to whoever asked for it.
        // Installed unconditionally rather than on the first listener: there is one
        // handler slot per window, and taking it here means nothing else can be
        // wired into it later and quietly win (`docs/gaps.md` G26, [ADR-0322]).
        window.onSystemThemeChanged(this::notifySystemTheme);

        // A press on nothing, or an Escape, closes whatever is open over this
        // window. Neither reaches a widget, which is why it is watched here
        // rather than handled by one (ADR-0103).
        window.inputWatcher(new Window.InputWatcher() {
            @Override
            public boolean pressed(
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Button button, float x, float y) {
                // **A press that dismissed something is a dismissal and not a
                // click**, which is what every desktop does: with a menu open,
                // the click that puts it away does not also press the button it
                // landed on. The rule was already here for the secondary button
                // below (ADR-0108); it turns out to be the general one.
                //
                // Without it a control that opens its own popup cannot be closed
                // by clicking it again: the press dismisses the list and the
                // release then reads as "open it", so a `select` toggles twice
                // and stays open ([ADR-0141]).
                if (dismissPopups()) {
                    return true;
                }
                // The secondary button, on a widget that named a menu: the press
                // is *taken*, so it does not also reach whatever it landed on —
                // right-clicking a button should open its menu, not press it
                // (ADR-0108).
                return button == io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.SECONDARY
                        && openContextMenu(x, y);
            }

            /// An exit that arrives within a moment of the toolkit opening a
            /// window over the pointer is the window, not the pointer.
            @Override
            public boolean exited() {
                return swallowExit();
            }

            @Override
            public boolean keyPressed(
                    io.github.digitalsmile.goldberry.input.key.Key key,
                    io.github.digitalsmile.goldberry.input.key.Modifiers modifiers,
                    boolean repeat) {
                // The topmost popup that wants keys at all, which is not always
                // the topmost popup: a *panel* is open over the window the whole
                // time something is selected and must take nothing from it
                // (`docs/gaps.md` G29, [ADR-0319]).
                var top = topmostKeyboardPopup();
                if (top == null) {
                    // `Escape` is still a dismissal, because declining keys and
                    // refusing to close are different promises — a panel that must
                    // survive one says so with `lightDismiss(false)`, and then
                    // this returns false and the key reaches the window.
                    if (key == io.github.digitalsmile.goldberry.input.key.Key.ESCAPE
                            && topmostPopup() != null
                            && dismissTopmostPopup()) {
                        return true;
                    }
                    // **The keyboard's right-click**, and only while nothing is
                    // open over the window: with a menu already showing, the
                    // menu key belongs to the menu (ADR-0208).
                    //
                    // `Shift+F10` beside the menu key rather than instead of it.
                    // A Mac keyboard has no menu key at all, and a PC one that
                    // does still has users who reach for the pair — so the two
                    // are companions everywhere, which is what every other
                    // desktop toolkit binds.
                    if (key == io.github.digitalsmile.goldberry.input.key.Key.MENU && modifiers.none()) {
                        return openContextMenuForFocus();
                    }
                    if (key == io.github.digitalsmile.goldberry.input.key.Key.F10
                            && modifiers.shift()
                            && !modifiers.control()
                            && !modifiers.alt()) {
                        return openContextMenuForFocus();
                    }
                    return false;
                }
                if (key == io.github.digitalsmile.goldberry.input.key.Key.ESCAPE) {
                    // **The innermost, not the stack.** A press *outside* a chain
                    // dismisses the whole thing, because the user pointed at
                    // something else; `Escape` steps back out of it one menu at a
                    // time, which is what every desktop does and what makes a
                    // submenu escapable without losing the menu that opened it
                    // ([ADR-0233]).
                    return dismissTopmostPopup();
                }
                // While a menu is open the keyboard belongs to it, whether or not
                // the platform moved focus there — otherwise an arrow would move
                // the selection in the window *underneath* the menu (ADR-0104).
                return top.handleKey(key, modifiers, repeat);
            }
        });

        // A popup goes away when the *application* does, which no platform
        // reports: opening one sends a focus-lost for the window under it and a
        // focus-gained for the popup itself (ADR-0144).
        GoldberryRuntime.get().onFocusChange(this::focusMayHaveLeft);

        try {
            Goldberry.run();
        } finally {
            shutDown();
        }
        LOG.info("{} finished after {} frame(s)", application.getClass().getSimpleName(), painted);
        // The whole run in one line, after the window has gone: the ring kept
        // the totals, and this is the number a frame-rate claim is (ADR-0342).
        var summary = window.frames().summary();
        LOG.info("frames: {}", summary.describe());
        if (summary.exceeds(options.lateBudget())) {
            throw new FrameBudgetException(summary, options.lateBudget());
        }
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
                    .frames(window.frames())
                    .clock(clock);
            stylesDirty = false;
        }
        return renderer;
    }

    private void paint(io.github.digitalsmile.goldberry.paint.Frame frame) {
        // Four timestamps rather than four `if (traced)` pairs: the stages are
        // what a `hud` shows, so they are measured on every frame or the number
        // on screen would be a different frame's. Five `nanoTime` calls against a
        // frame that costs hundreds of microseconds is not a cost worth a branch
        // (ADR-0146).
        var beganAt = System.nanoTime();
        if (io.github.digitalsmile.goldberry.widget.FrameTrace.ENABLED) {
            tree.trace().reset();
        }

        // Before the build rather than after it, so a change a sweep notices is a
        // change *this* frame shows. A listener marks an element dirty, and the
        // flush below is what a dirty element is waiting for -- the other order
        // would show it one frame late (ADR-0155).
        //
        // Nothing at all for a woven model: `refresh` returns false without
        // looking, which is what makes this line free in a native image.
        for (var model : models) {
            io.github.digitalsmile.goldberry.bind.runtime.Models.refresh(model);
        }

        // Every setState since the last frame settles here, once, however many of
        // them there were (ADR-0052).
        // Before the flush, not after: a build may ask the cascade about a custom
        // property (ADR-0254), and a resolver handed over afterwards would be a
        // frame late for no reason. `renderer()` is lazy, so this is also what
        // creates it on the first frame.
        renderer().prepare(tree);
        if (tree.needsBuild()) {
            tree.flush();
        }
        var builtAt = System.nanoTime();

        // The cascade and the box tree: the term ADR-0070 measured as the largest
        // in a frame, and ADR-0142 as the one that had stopped being cached.
        var boxes = renderer().render(tree);
        var styledAt = System.nanoTime();

        // One layout pass, two readers. `update` reconciles the retained render
        // tree against this frame's description and lays it out; the paint and the
        // hit-test snapshot both read that one result (ADR-0069).
        render.update(frame, boxes);
        var laidOutAt = System.nanoTime();

        // What differs from the last frame, computed before painting because the
        // clip has to be in place before anything is drawn (ADR-0072).
        var damage = render.damage(frame);
        if (window.canRepaintPartially()) {
            render.paint(frame, damage);
        } else {
            render.paint(frame);
        }
        var rasterizedAt = System.nanoTime();
        window.frameRing()
                .stages(builtAt - beganAt, styledAt - builtAt, laidOutAt - styledAt, rasterizedAt - laidOutAt);
        window.damaged(damage);

        // What the pointer is tested against is the frame that was just painted,
        // not a fresh layout -- which would be one frame ahead of what the user
        // can see (ADR-0054). Kept as well as handed over: `anchor` answers from
        // the same capture, so a menu opens under where its button *was drawn*
        // rather than where a fresh layout would put it.
        regions = HitTest.capture(render);
        // Before the regions, because a `Located` widget is told what clips it and
        // "nothing clips me" resolves to this (ADR-0119).
        router.windowBounds(
                LogicalRect.of(0, 0, frame.size().width(), frame.size().height()));
        router.updateRegions(regions);

        // **After the regions**, which is the whole of why this is a flag and not
        // a call in the resize handler: `anchor(id)` answers from the capture the
        // last paint produced, and during the resize handler that capture is
        // still the *old* window's. Re-placing there would put every menu back
        // where its heading used to be, which is the bug rather than the fix
        // ([ADR-0231]).
        //
        // And on **every** frame while something is anchored by id, which is the
        // other half of the same claim: a `popover` hanging off a widget in a
        // `scroll` has to travel with it, and a scroll is a frame rather than an
        // event anybody reports. Guarded by the id, so a window with no anchored
        // popup open pays nothing ([ADR-0270]).
        if (replaceAfterPaint || followsAnAnchor()) {
            replaceAfterPaint = false;
            replacePopups();
        }

        // §1.7's "the frame loop is fully idle when no animation is active": ask
        // for another frame *only* while something is moving.
        if (renderer().isAnimating()) {
            window.repaint();
        }

        if (io.github.digitalsmile.goldberry.widget.FrameTrace.ENABLED) {
            traceFrame(beganAt, builtAt, styledAt, laidOutAt, rasterizedAt, damage.size());
        }

        painted++;
        // **Between frames, not inside this one.** On Windows and macOS
        // `SDL_SetWindowSize` takes effect on the spot, so a request made from
        // inside the painter changes the size under the frame being painted and
        // the platform then refuses it -- every frame of the run, each counted
        // late. A zero-delay timer runs on the next pump, after this frame has
        // been presented. And from where the window actually is, so a manager
        // that clamped or lagged the last request is walked from its answer
        // rather than from the ask; the resize itself asks for the next frame.
        if (resizeWalk != null) {
            after(java.time.Duration.ZERO, () -> {
                if (window.isOpen()) {
                    window.resize(resizeWalk.next(window.size()));
                }
            });
        }
        if (options.frames() > 0) {
            if (painted >= options.frames()) {
                LOG.info("painted {} frame(s); exiting", painted);
                Goldberry.stop();
            } else {
                window.repaint();
            }
        }
    }

    /// One line per frame that did something, for `-Dgoldberry.trace.frames=true`.
    ///
    /// The stage timings say which part of a frame is expensive and the counts
    /// say why: a `style` of 12ms next to `resolved 74` is a cascade running on
    /// the whole tree, and the `subtree walks` line names the node and the state
    /// that asked for it (ADR-0151).
    ///
    /// Quiet frames are skipped unless `=all`, because an idle loop at 60fps
    /// otherwise writes a line a frame saying nothing happened — and the frames
    /// worth reading are the ones next to the click.
    private void traceFrame(
            long beganAt, long builtAt, long styledAt, long laidOutAt, long rasterizedAt, int damageRects) {
        var trace = tree.trace();
        if (trace.isQuiet() && !io.github.digitalsmile.goldberry.widget.FrameTrace.ALL_FRAMES) {
            return;
        }
        LOG.info(
                "frame {} | build {} style {} layout {} raster {} ms | {} | damage {}",
                painted,
                millis(builtAt - beganAt),
                millis(styledAt - builtAt),
                millis(laidOutAt - styledAt),
                millis(rasterizedAt - laidOutAt),
                trace,
                damageRects);
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    // --- context menus ------------------------------------------------------

    /// This launcher's registration for "the hover or the focus moved", closed
    /// when the window goes ([ADR-0230]).
    private io.github.digitalsmile.goldberry.bind.Subscription pointing;

    /// What an application does when a widget that named a menu is right-clicked
    /// — see [Host#onContextMenu].
    private ContextMenuHandler contextMenus;

    /// Finds the menu a right-click asked for, and asks for it to be opened.
    ///
    /// Walked upwards from what is under the pointer, for the tooltip's reason: a
    /// right-click on a button's *label* is a right-click on the button.
    ///
    /// @return whether anything was opened
    private boolean openContextMenu(float x, float y) {
        // A zero-sized rectangle at the pointer: a context menu is anchored to
        // where the click was, not to the widget, which is what every desktop
        // does and what makes two right-clicks in one list open two menus in two
        // places.
        return openContextMenu(router.hovered(), new LogicalRect(new LogicalPoint(x, y), new LogicalSize(0, 0)));
    }

    /// Finds the menu the **keyboard** asked for, and asks for it to be opened.
    ///
    /// The menu key, and `Shift+F10` on the keyboards that have no menu key
    /// (ADR-0208).
    /// Two things differ from the pointer's half and both follow from there being
    /// no pointer.
    ///
    /// It starts at the **focused** element rather than the hovered one, which is
    /// what "the keyboard's position" means — and it is the reason this is worth
    /// building at all: a right-click is a thing only a pointer can do, and §2.2
    /// requires everything to be reachable.
    ///
    /// And it is anchored to that element's **painted rectangle** rather than to
    /// a point, because there is no point to anchor to. The menu therefore hangs
    /// off the bottom of whatever has the focus ring, which is where a reader is
    /// already looking. The rectangle comes from the last painted frame for
    /// [#anchor]'s reason (ADR-0054): a key event has no way to reach the
    /// geometry, and the geometry is what a placement needs.
    ///
    /// A focused element that has not been painted yet has no rectangle, and
    /// nothing opens — the same answer a right-click over nothing gives.
    ///
    /// @return whether anything was opened
    private boolean openContextMenuForFocus() {
        var focused = router.focused();
        if (focused == null) {
            return false;
        }
        var anchor = anchorOf(focused);
        return anchor.isPresent() && openContextMenu(focused, anchor.get());
    }

    /// The half the two share: walk up from `from` to the nearest widget that
    /// named a menu, and open it against `anchor`.
    ///
    /// One walk rather than two, because "a right-click on a button's label is a
    /// right-click on the button" is the same rule as "the menu key on a focused
    /// button is that button's menu" — and a second copy of it would be a second
    /// chance for the two to disagree about which ancestor wins.
    private boolean openContextMenu(io.github.digitalsmile.goldberry.widget.Element from, LogicalRect anchor) {

        if (contextMenus == null) {
            return false;
        }
        // The deepest widget on the walk that can make itself the subject, held
        // rather than told: a menu that never opens must not move a selection,
        // because a selection that changed with nothing to show for it is a
        // gesture with no visible cause (ADR-0224).
        io.github.digitalsmile.goldberry.input.handler.Selects subject = null;
        for (var node = from;
                node != null;
                node = node.parent() instanceof io.github.digitalsmile.goldberry.widget.Element parent
                        ? parent
                        : null) {
            if (subject == null
                    && node.widget() instanceof io.github.digitalsmile.goldberry.input.handler.Selects selects) {
                subject = selects;
            }
            var named = node.widget() instanceof io.github.digitalsmile.goldberry.widget.attr.Attributed<?> a
                    ? a.attributes().contextMenu()
                    : null;
            if (named != null) {
                // Before the menu, so the row is already drawn as the selection
                // in the frame the menu opens over — and so an application
                // building the menu from its own selection reads the new one.
                if (subject != null) {
                    subject.selectForContextMenu();
                }
                contextMenus.open(named, anchor);
                return true;
            }
        }
        return false;
    }

    // --- tooltips -----------------------------------------------------------

    /// How long the pointer has to rest on something before its tooltip appears,
    /// when no stylesheet says otherwise.
    ///
    /// Long enough not to fire while the pointer is crossing a toolbar on its way
    /// somewhere, short enough that someone who stopped to read is not left
    /// waiting. `docs/core-widgets.md` §7 says "after delay" and does not say how
    /// long; `design-system.md` §3's `tooltip` row does, and this is that number
    /// as the **fallback** rather than as the figure.
    ///
    /// The token is [#TOOLTIP_DELAY_TOKEN] and it ships in `controls.css`, which
    /// is `:widgets`' — the same arrangement `--gb-list-row-height` has, and for
    /// the same reason: a `:core` default must not need the catalog to exist
    /// ([ADR-0262]).
    private static final double TOOLTIP_DELAY_MS = 500;

    /// How long a tooltip waits when one is **already showing** and the pointer
    /// has moved to a different node.
    ///
    /// §3's row is two numbers — "delay 500ms show / 100ms move-between" — and
    /// only the first had ever been built. The second is what makes a row of
    /// toolbar buttons readable: having decided to read one tooltip, a user
    /// reading the next should not serve the full sentence of hover intent
    /// again. §3.1 gives the same move "instant reposition, never slides", which
    /// is the drawing half of the same sentence.
    private static final double TOOLTIP_MOVE_DELAY_MS = 100;

    /// §3's `tooltip` row, as component-token defaults.
    ///
    /// Durations rather than lengths, which is what
    /// [io.github.digitalsmile.goldberry.widget.BuildContext#duration] is for: a
    /// component metric measured in milliseconds is still a component metric, and
    /// §3 says those ship as tokens.
    private static final String TOOLTIP_DELAY_TOKEN = "--gb-tooltip-delay";

    private static final String TOOLTIP_MOVE_DELAY_TOKEN = "--gb-tooltip-delay-move";

    /// The tooltip that is showing, or null.
    private Popup tooltip;

    /// When the last tooltip was opened, in `System.nanoTime` units, or 0.
    ///
    /// **X11 reports that the pointer left a window when another window is mapped
    /// over it**, which is exactly what opening a tooltip does — and delivering
    /// that exit clears the hover and the cursor of the widget the tooltip is
    /// describing. Headlessly the cursor survives, which is how this was pinned
    /// on the platform rather than on the router ([ADR-0111]).
    private long tooltipOpenedAt;

    /// How long after opening a tooltip an exit is treated as the toolkit's own
    /// doing.
    ///
    /// Bounded rather than a flag that waits for the next exit: on a driver that
    /// sends no spurious exit at all, a flag would swallow the user's *real* one
    /// whenever it eventually came.
    private static final long SPURIOUS_EXIT_NANOS =
            java.time.Duration.ofMillis(250).toNanos();

    /// Whether the exit that just arrived is the one opening a tooltip provokes.
    private boolean swallowExit() {
        return tooltipOpenedAt != 0 && System.nanoTime() - tooltipOpenedAt < SPURIOUS_EXIT_NANOS;
    }

    /// The node it belongs to, so a hover that returns to the same widget does
    /// not close and reopen it.
    private io.github.digitalsmile.goldberry.widget.Element tooltipOwner;

    /// The delay in flight, cancelled by anything that moves.
    private EventLoop.Timer tooltipTimer;

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
        // Read **before** the hide, because the hide is what makes it false: §3's
        // shorter delay is for moving *between* tooltips, and by the time the old
        // one has been closed there is no longer any evidence that there was one.
        var moving = tooltip != null;
        hideTooltip();
        tooltipOwner = target;
        if (target == null) {
            return;
        }
        // A fresh delay per node, cancelled by the next move. The pointer
        // crossing five buttons on its way to a sixth schedules five timers and
        // fires none of them.
        tooltipTimer = after(tooltipDelay(target, moving), this::showTooltip);
    }

    /// §3's `tooltip` row, resolved against the node the tooltip is for.
    ///
    /// Asked of the **target** rather than of the window, so a panel may set the
    /// token for what is inside it — which is what a custom property inheriting
    /// down the tree already means, and is the only reading that does not make
    /// this a global setting wearing a token's clothes.
    ///
    /// A tooltip already showing takes the shorter of the two. The full delay is
    /// hover *intent* — the question "did you mean to stop here?" — and a user
    /// who is reading tooltips has already answered it.
    private java.time.Duration tooltipDelay(io.github.digitalsmile.goldberry.widget.Element target, boolean moving) {

        var millis = moving
                ? target.duration(TOOLTIP_MOVE_DELAY_TOKEN, TOOLTIP_MOVE_DELAY_MS)
                : target.duration(TOOLTIP_DELAY_TOKEN, TOOLTIP_DELAY_MS);
        // Clamped at zero rather than refused: a negative delay is a stylesheet
        // being wrong about something that cannot fail, and "show it at once" is
        // the only reading of it that draws anything.
        return java.time.Duration.ofMillis((long) Math.max(0, millis));
    }

    /// The node whose tooltip should show: what the pointer is on, or — when the
    /// pointer is on nothing — what the **keyboard** is on.
    ///
    /// The pointer wins because it is the more recent statement of intent: a
    /// keyboard user who reaches for the mouse is looking at where the mouse is.
    ///
    /// ## Keyboard focus, and not merely focus
    ///
    /// §7 says a tooltip shows "on hover *and on keyboard focus*", and the second
    /// half of that has to mean what it says. **Clicking a button focuses it** —
    /// so a fallback that asked only [io.github.digitalsmile.goldberry.input.PointerRouter#focused()]
    /// kept the tooltip alive after the pointer left the thing that had just been
    /// clicked: `hovered` went null, focus was still the button, the target had
    /// not changed, and `pointingChanged` returned early without hiding anything.
    /// The tooltip then sat there until something else took the focus
    /// ([ADR-0308]).
    ///
    /// `focusedFromKeyboard()` is the distinction the router already keeps for
    /// `:focus-visible` ([ADR-0054]), and it is the same distinction for the same
    /// reason: focus that arrived by pointer is a side effect of the click, not a
    /// statement about where the user is working. **A tooltip follows the focus
    /// ring**, which is one sentence and is also exactly what the code now does.
    private io.github.digitalsmile.goldberry.widget.Element tooltipTarget() {
        var hovered = withTooltip(router.hovered());
        if (hovered != null) {
            return hovered;
        }
        return router.focusedFromKeyboard() ? withTooltip(router.focused()) : null;
    }

    /// `element`, or the nearest ancestor of it that has a tooltip.
    ///
    /// Walked upwards because a tooltip on a `button` has to survive the pointer
    /// being over the button's *label*, which is a different element and the one a
    /// hit test reports.
    private io.github.digitalsmile.goldberry.widget.Element withTooltip(
            io.github.digitalsmile.goldberry.widget.Element element) {
        for (var node = element;
                node != null;
                node = node.parent() instanceof io.github.digitalsmile.goldberry.widget.Element parent
                        ? parent
                        : null) {
            if (tooltipTextOf(node) != null) {
                return node;
            }
        }
        return null;
    }

    private static String tooltipTextOf(io.github.digitalsmile.goldberry.widget.Element element) {
        return element.widget() instanceof io.github.digitalsmile.goldberry.widget.attr.Attributed<?> a
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
        tooltip = tooltipPopup(new io.github.digitalsmile.goldberry.widget.root.TooltipPanel(text), anchor.get())
                .orElse(null);
        tooltipOpenedAt = tooltip == null ? 0 : System.nanoTime();
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
        tooltipOpenedAt = 0;
    }

    /// The painted rectangle of an element, by identity — [#anchor] by id, for
    /// the case where the caller has the element itself.
    private java.util.Optional<LogicalRect> anchorOf(io.github.digitalsmile.goldberry.widget.Element element) {
        for (var region : regions) {
            if (region.owner() == element) {
                return java.util.Optional.of(region.bounds());
            }
        }
        return java.util.Optional.empty();
    }

    /// The popup on top that **wants the keyboard** — the last one opened that is
    /// still open and has not declined keys ([Popup#keyboard(boolean)]).
    ///
    /// Separate from [#topmostPopup()] because the two questions are different and
    /// only one of them is about keys: a press outside dismisses whatever is
    /// topmost, panel or not, while a key belongs to the topmost thing that asked
    /// for one. A panel over a menu therefore leaves the menu operable by arrows,
    /// which is what "a panel is not in the keyboard's way" has to mean if it
    /// means anything ([ADR-0319]).
    private Popup topmostKeyboardPopup() {
        for (var i = popups.size() - 1; i >= 0; i--) {
            var popup = popups.get(i);
            if (popup.isOpen() && popup.wantsKeyboard()) {
                return popup;
            }
        }
        return null;
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

    /// The pending "has the application really lost focus" check, or null.
    private EventLoop.Timer focusCheck;

    /// How long to wait before believing a focus-lost.
    ///
    /// **Deferred rather than acted on**, and this is the whole mechanism. The
    /// platform reports focus per window: opening a popup sends a lost for the
    /// owner and then a gained for the popup, in that order, so a menu that
    /// closed on the first of those would close as it opened. One turn of the
    /// event loop later, the pair has been seen and the question "is any window
    /// of ours focused" has its real answer.
    ///
    /// Short enough not to leave a menu floating over another application while
    /// anybody notices, long enough to cover a pair of events the compositor
    /// delivers in two batches.
    ///
    /// **60 ms is one number covering every driver**, and the first driver to
    /// deliver that pair more slowly makes a menu look like it closes as it
    /// opens. It cannot be derived — the pair is the compositor's own scheduling
    /// and nothing reports what it will be — so what can be done about it is to
    /// let it be told: [#SETTLE_PROPERTY] overrides it without a rebuild, which
    /// is what turns "this driver is broken" into a flag somebody can set
    /// ([ADR-0144]).
    ///
    /// **An instance field and not a constant**: read when the launcher is built
    /// rather than when the class is loaded, so a test that sets the property can
    /// set it in the test rather than in the JVM that runs the suite.
    private final java.time.Duration focusSettle = focusSettle();

    /// How long to disbelieve a focus-lost for, in milliseconds.
    ///
    /// A tuning flag of the same kind as `goldberry.frame.rate`: rarely needed,
    /// and the one thing that makes a compositor nobody here has run against
    /// somebody else's afternoon rather than a bug report.
    static final String SETTLE_PROPERTY = "goldberry.popup.settle";

    /// The default, or what [#SETTLE_PROPERTY] says.
    ///
    /// Clamped rather than trusted: zero would act on the *first* of the pair
    /// every driver sends and close every menu as it opened, which is the exact
    /// bug this delay exists for. A value that will not parse is ignored rather
    /// than fatal, for the reason a malformed frame rate is — a tuning flag
    /// should not stop an application starting.
    static java.time.Duration focusSettle() {
        var raw = System.getProperty(SETTLE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return java.time.Duration.ofMillis(60);
        }
        try {
            return java.time.Duration.ofMillis(Math.clamp(Long.parseLong(raw.trim()), 1L, 2_000L));
        } catch (NumberFormatException e) {
            LOG.warn("{}=\"{}\" is not a number of milliseconds; using 60", SETTLE_PROPERTY, raw);
            return java.time.Duration.ofMillis(60);
        }
    }

    /// Some window's focus changed. If the application ends up with none of it,
    /// every light-dismissed popup goes away.
    ///
    /// A menu left floating over the application the user switched *to* is the
    /// symptom this exists for, and it is worse than it sounds: a popup is
    /// always-on-top by kind, so it stays visible over the other application's
    /// window (ADR-0144).
    private void focusMayHaveLeft() {
        if (popups.isEmpty()) {
            return;
        }
        if (focusCheck != null) {
            focusCheck.cancel();
        }
        focusCheck = after(focusSettle, () -> {
            focusCheck = null;
            if (!GoldberryRuntime.get().anyWindowFocused()) {
                dismissPopups();
            }
        });
    }

    /// Closes the innermost light-dismissed popup — what `Escape` does.
    ///
    /// The topmost one that will actually go, which is not always the topmost
    /// one: a tooltip is `lightDismiss(false)` and refuses, and stopping at it
    /// would leave `Escape` doing nothing while a menu was open underneath.
    ///
    /// @return whether anything closed, which is what makes the key handled
    private boolean dismissTopmostPopup() {
        for (var i = popups.size() - 1; i >= 0; i--) {
            if (popups.get(i).dismissedByInput()) {
                popups.removeIf(popup -> !popup.isOpen());
                return true;
            }
        }
        return false;
    }

    /// Closes every light-dismissed popup. Copied first: closing one removes it
    /// from the list it is being iterated over.
    ///
    /// @return whether this actually closed one, which is what makes the press
    ///         that did it a dismissal rather than a click — see the watcher
    private boolean dismissPopups() {
        if (popups.isEmpty()) {
            return false;
        }
        var wasOpen = popups.stream().anyMatch(Popup::isOpen);
        for (var popup : List.copyOf(popups)) {
            popup.dismissedByInput();
        }
        popups.removeIf(popup -> !popup.isOpen());
        return wasOpen && popups.stream().noneMatch(Popup::isOpen);
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
        // The registration for "the hover or the focus moved". Given back rather
        // than left to die with the router, because a returned handle nobody keeps
        // is the shape that makes a leak invisible — and this is the first caller
        // of a facility that now hands one out ([ADR-0230]).
        if (pointing != null) {
            pointing.close();
            pointing = null;
        }
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
    public io.github.digitalsmile.goldberry.motion.Clock clock() {
        return clock;
    }

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
    public void shortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void shortcut(
            io.github.digitalsmile.goldberry.input.key.Shortcut accelerator, Runnable action, Object owner) {
        router.shortcut(accelerator, action, owner);
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void modifierTap(
            io.github.digitalsmile.goldberry.input.tap.ModifierKey modifier, Runnable action, Object owner) {
        // The window's rather than the router's: a tap is read from the platform
        // keycode, which is the one thing the router never sees (ADR-0223).
        window.modifierTaps().bind(modifier, action, owner);
    }

    @Override
    public void removeModifierTap(io.github.digitalsmile.goldberry.input.tap.ModifierKey modifier, Object owner) {
        window.modifierTaps().unbind(modifier, owner);
    }

    @Override
    public void removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator) {
        router.removeShortcut(accelerator);
    }

    @Override
    public void removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut accelerator, Object owner) {
        router.removeShortcut(accelerator, owner);
    }

    @Override
    public void removeShortcut(String accelerator) {
        router.removeShortcut(io.github.digitalsmile.goldberry.input.key.Shortcut.of(accelerator));
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner) {
        return overlay(widget, corner, Overlay.WINDOW_MARGIN);
    }

    @Override
    public Overlay fill(Widget widget) {
        return attach(Overlay.filling(widget));
    }

    @Override
    public Overlay overlay(Widget widget, Corner corner, float margin) {
        return attach(Overlay.of(widget, corner, margin));
    }

    private Overlay attach(Overlay entry) {
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

    /// The application's system-theme listeners, in the order they were added.
    ///
    /// A list rather than one slot, because an application may reasonably have two
    /// — the shell that swaps the stylesheet, and a settings screen showing what
    /// the desktop currently says. Nothing removes one: a listener lives as long as
    /// the window, which is what [Host#onSystemThemeChanged] promises.
    private final List<java.util.function.Consumer<SystemTheme>> systemThemeListeners = new ArrayList<>();

    @Override
    public java.util.Optional<SystemTheme> systemTheme() {
        return window.systemTheme();
    }

    @Override
    public void onSystemThemeChanged(java.util.function.Consumer<SystemTheme> listener) {
        systemThemeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// Tells every listener, over a copy: a listener that reacts by adding another
    /// one — a screen that appears because the theme changed — must not be a
    /// `ConcurrentModificationException`.
    private void notifySystemTheme(SystemTheme theme) {
        for (var listener : List.copyOf(systemThemeListeners)) {
            listener.accept(theme);
        }
    }

    @Override
    public java.util.Optional<HitTest.Region> anchor(String id) {
        Objects.requireNonNull(id, "id");
        for (var region : regions) {
            if (region.owner() instanceof io.github.digitalsmile.goldberry.widget.Element element
                    && element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                    && id.equals(styled.id())) {
                return java.util.Optional.of(region);
            }
        }
        return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<Popup> popup(Widget content, LogicalPoint at, LogicalSize size) {
        return popup(content, at, size, PopupKind.MENU);
    }

    @Override
    public java.util.Optional<Popup> tooltip(Widget content, LogicalPoint at, LogicalSize size) {
        return popup(content, at, size, PopupKind.TOOLTIP);
    }

    private java.util.Optional<Popup> popup(Widget content, LogicalPoint at, LogicalSize size, PopupKind kind) {

        Objects.requireNonNull(content, "content");
        return open(new ElementTree(content, this), RenderTree.create(), new PopupSpec(at, size, kind));
    }

    /// Asks the backend for the window and wires the trees to it.
    private java.util.Optional<Popup> open(ElementTree tree, RenderTree render, PopupSpec spec) {

        var backend = GoldberryRuntime.get().backend().createPopup(window.backendWindow(), spec);
        if (backend.isEmpty()) {
            // The driver has none. The caller's fallback is the overlay layer,
            // clipped to the window, and saying so is more use than an empty
            // Optional on its own (ADR-0102).
            tree.unmount();
            render.close();
            LOG.info("this platform has no popup windows; a {} will have to be an overlay", spec.kind());
            return java.util.Optional.empty();
        }

        var popup = new Popup(
                backend.get(),
                Window.over(backend.get()),
                tree,
                render,
                this::renderer,
                () -> popups.removeIf(open -> !open.isOpen()));
        popups.add(popup);
        return java.util.Optional.of(popup);
    }

    @Override
    public java.util.Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement) {
        return placed(content, anchor, placement, PopupKind.MENU, 0, null);
    }

    @Override
    public java.util.Optional<Popup> popup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth) {
        return placed(content, anchor, placement, PopupKind.MENU, minimumWidth, null);
    }

    @Override
    public java.util.Optional<Popup> popup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        return placed(content, anchor, placement, PopupKind.MENU, minimumWidth, fit);
    }

    @Override
    public java.util.Optional<Popup> attachedPopup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        // TOOLTIP is the *kind*, not the widget: what it buys here is
        // `NOT_FOCUSABLE`, so the keyboard stays on the field this hangs off
        // (ADR-0186).
        return placed(content, anchor, placement, PopupKind.ATTACHED, minimumWidth, fit);
    }

    /// Measure, place, open — the three steps `popover` is made of (ADR-0104),
    /// shared by the menu form and the tooltip one because only the kind differs.
    ///
    /// A [Host.Fit] sits between the first two: it is handed what the content
    /// measured and answers with what to open, which is nearly always the same
    /// widget ([ADR-0179]).
    private java.util.Optional<Popup> placed(
            Widget content, LogicalRect anchor, Placement placement, PopupKind kind, float minimumWidth, Fit fit) {

        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(placement, "placement");

        // Built once and handed to the popup: measuring throws the layout away
        // otherwise, and the element tree is what carries state, so a second one
        // would also be a second lot of `initState`.
        var tree = new ElementTree(content, this);
        var render = RenderTree.create();
        var size = measure(tree, render, minimumWidth);

        if (fit != null) {
            var refitted = fit.fit(content, size, placeableArea());
            Objects.requireNonNull(refitted, "a Fit answered with no content at all");
            if (refitted != content) {
                // The content changed, so everything measured against the old one
                // is worthless -- including the element tree, which is why this
                // is the expensive branch and why it is only taken when a caller
                // actually rewrote what it is opening. Nearly every popup fits
                // and never comes in here.
                tree.unmount();
                render.close();
                tree = new ElementTree(refitted, this);
                render = RenderTree.create();
                size = measure(tree, render, minimumWidth);
            }
        }

        var placed = placement.place(anchor, size, placeableArea());
        var opened = open(tree, render, new PopupSpec(placed.at(), size, kind));
        // Remembered so a resize can put it back — see [#replacePopups]. The id
        // half is filled in by the overloads that were given one, which is the
        // only place it is known.
        opened.ifPresent(popup -> placements.put(popup, new Placed(null, anchor, placement)));
        // How it measures itself again when its content changes -- the same two
        // passes and the same `Fit`, so a tree that expands grows the window and
        // gets a viewport when it outgrows the screen (ADR-0186).
        var floor = minimumWidth;
        var refit = fit;
        opened.ifPresent(popup -> popup.measuredBy((liveTree, liveRender) -> {
            var measured = measure(liveTree, liveRender, floor);
            if (refit == null) {
                return measured;
            }
            var shown = liveTree.root().widget();
            var answer = refit.fit(shown, measured, placeableArea());
            if (answer == shown) {
                return measured;
            }
            liveTree.update(answer);
            return measure(liveTree, liveRender, floor);
        }));
        return opened;
    }

    /// A tooltip's popup: above by preference, never light-dismissed.
    ///
    /// Not light-dismissed because a tooltip is dismissed by the pointer leaving,
    /// and a press that closed it would fire in the same gesture as the click on
    /// whatever it is describing — closing it a moment before it was going to
    /// close anyway, and taking the *next* tooltip's timer with it.
    private java.util.Optional<Popup> tooltipPopup(Widget content, LogicalRect anchor) {
        return placed(content, anchor, Placement.ABOVE.align(Placement.Align.CENTER), PopupKind.TOOLTIP, 0, null)
                .map(popup -> popup.lightDismiss(false));
    }

    @Override
    public java.util.Optional<Popup> popup(Widget content, String anchorId, Placement placement) {
        Objects.requireNonNull(anchorId, "anchorId");
        var anchor = anchor(anchorId);
        if (anchor.isEmpty()) {
            LOG.warn("nothing with id \"{}\" has been painted, so there is nothing to anchor to", anchorId);
            return java.util.Optional.empty();
        }
        // `painted()` and not `bounds()`: a menu belongs under where its button
        // was **drawn**, and a button inside a `scroll` is laid out where it
        // always was and drawn a long way from there ([ADR-0270]). The two are
        // the same rectangle for anything nothing transformed, which is nearly
        // every anchor there has ever been — which is why this was not wrong
        // until something scrolled.
        var opened = popup(content, anchor.get().painted(), placement);
        // Upgraded from a rectangle to a **name**, which is what makes a resize
        // able to follow the anchor rather than merely re-clamp against the new
        // work area: the id is re-resolved against the frame the resize produced
        // ([ADR-0231]).
        opened.ifPresent(popup -> placements.computeIfPresent(
                popup, (key, placed) -> new Placed(anchorId, placed.anchor(), placed.placement())));
        return opened;
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
    private LogicalSize measure(ElementTree tree, RenderTree render, float minimumWidth) {
        var box = renderer().render(tree);
        var natural = render.measure(box, window.scale(), Float.NaN, Float.NaN);
        var cap = window.size().width();
        // A floor under the width, for a dropdown that must be at least as wide
        // as the control it drops from (ADR-0145). Bounded by the cap, because a
        // popup wider than the window it belongs to is not what any anchor meant.
        var floor = Math.min(minimumWidth, cap);
        if (natural.width() >= floor && natural.width() <= cap) {
            return natural;
        }
        // One more pass with a definite width, which is what makes the content
        // *fill* the floor rather than merely be placed in a wider window --
        // and the same pass the cap already needed.
        var target = Math.max(floor, Math.min(natural.width(), cap));
        var laid = render.measure(box, window.scale(), target, Float.NaN);
        // Content that will not stretch -- something with a width of its own --
        // still gets the window the floor asked for, because the floor is about
        // where the popup's *edges* are and not about what is drawn in it.
        return new LogicalSize(Math.max(laid.width(), floor), laid.height());
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
    @Override
    public LogicalRect placeableArea() {
        var backendWindow = window.backendWindow();
        var origin = backendWindow.position();
        var area = backendWindow.workArea();
        if (origin.isEmpty() || area.isEmpty()) {
            return new LogicalRect(LogicalPoint.ZERO, window.size());
        }
        return area.get().offsetBy(-origin.get().x(), -origin.get().y());
    }

    @Override
    public void onContextMenu(ContextMenuHandler handler) {
        this.contextMenus = handler;
    }

    @Override
    public boolean focus(String id, boolean fromKeyboard) {
        return router.focusById(id, fromKeyboard);
    }

    @Override
    public EventLoop.Timer after(java.time.Duration delay, Runnable action) {
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
    public Clipboard clipboard() {
        return GoldberryRuntime.get().backend().clipboard();
    }

    @Override
    public FileDialogs fileDialogs() {
        return GoldberryRuntime.get().backend().fileDialogs();
    }

    /// The dialog is modal for **this** window, which is the one difference from
    /// the default on [Host]: a launcher is the only implementation that has a
    /// window to name, and a dialog with no owner is a second top-level window on
    /// Windows and macOS — one the user can lose behind the one that opened it.
    ///
    /// The repaint is the default's, and the reason is written there.
    @Override
    public void fileDialog(FileDialogSpec spec, Consumer<FileChoice> onChoice) {
        fileDialogs().show(window.backendWindow(), spec, choice -> {
            try {
                onChoice.accept(choice);
            } finally {
                repaint();
            }
        });
    }

    /// The tray is the backend's, like the clipboard, because both belong to the
    /// application rather than to the window this launcher runs.
    ///
    /// **Every row is given the window's repaint**, and without it a tray menu
    /// looks broken in a way nothing reports: a row's handler runs inside the
    /// platform's own pump and produces no event, so nothing asks for a frame,
    /// so the model sweep at the top of [#paint] never runs and a handler that
    /// set a field changed nothing anybody looks at. It is the only input in the
    /// toolkit that arrives without an event behind it, which is why this is the
    /// one call site that has to say so
    /// (ADR-0191).
    @Override
    public java.util.Optional<io.github.digitalsmile.goldberry.render.tray.BackendTray> tray(
            io.github.digitalsmile.goldberry.render.tray.TraySpec spec) {
        return GoldberryRuntime.get().backend().createTray(spec.andThen(this::repaint));
    }

    @Override
    public void textInput(boolean active) {
        window.backendWindow().textInput(active);
    }

    @Override
    public Window window() {
        return window;
    }
}
