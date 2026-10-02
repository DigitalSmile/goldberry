package dev.goldberry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.bind.Property;
import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.frame.FrameSequence;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Selects;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.input.tap.ModifierKey;
import dev.goldberry.motion.Clock;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.tree.RenderTree;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.clipboard.PrimarySelection;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.desktop.menubar.AppMenuItem;
import dev.goldberry.render.desktop.notify.Notification;
import dev.goldberry.render.dialog.FileChoice;
import dev.goldberry.render.dialog.FileDialogSpec;
import dev.goldberry.render.dialog.FileDialogs;
import dev.goldberry.render.display.Display;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.popup.PopupKind;
import dev.goldberry.render.popup.PopupSpec;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TraySpec;
import dev.goldberry.render.web.BackendWebView;
import dev.goldberry.render.web.WebViewSpec;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.FrameTrace;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.root.TooltipPanel;
import dev.goldberry.widget.root.WindowRoot;
import dev.goldberry.widget.style.Corner;
import dev.goldberry.widget.style.Styled;

/// One window of a running [Application], and the [Host] that window answers
/// to: its element tree, render tree and router, the overlays floating over
/// it, its popups and tooltips, its accelerators and its frame.
///
/// What belongs to the application rather than to a window — the stylesheets,
/// the fonts, the models and the clock — is the [Launcher]'s, and every window
/// shares it. The first window is the one the launcher opens; every other one
/// comes from [Host#openWindow], and is a second tree in a second window, which
/// is also what a popup has always been.
///
/// Not public, for the launcher's reason: what an application holds is a
/// [Host], or the [WindowHost] a second window hands back.
///
/// Read more: [More than one window](https://goldberry.dev/docs/guide/windows.html#more-than-one-window).
final class HostedWindow implements WindowHost {

    private static final Logger LOG = LoggerFactory.getLogger(HostedWindow.class);

    /// What every window of the application shares.
    private final Launcher app;

    private final Window window;

    /// The window this one belongs to, or null for one of its own.
    private final @Nullable HostedWindow owner;

    /// Whether this window blocks [#owner] while it is open.
    private final boolean modal;

    private final PointerRouter router;

    // Built by [#mount] rather than here: an application's `start` runs
    // between the two, and a tree mounted before it would describe a root the
    // application has not finished preparing. Non-null from there on.
    @SuppressWarnings("NullAway.Init")
    private ElementTree tree;

    @SuppressWarnings("NullAway.Init")
    private RenderTree render;

    /// The steps a frame runs, in the order they have to run in — shared with
    /// `Offscreen`, so a window and a buffer run the same steps in the same order.
    ///
    /// What is *not* in it is what makes a window a window: the damage pass, the
    /// frame ring, the HUD's stage timings and the model sweep are all below, and
    /// the sequence knows about none of them.
    @SuppressWarnings("NullAway.Init")
    private FrameSequence sequence;

    /// Null until [#renderer()] first builds one.
    private @Nullable WidgetRenderer renderer;

    /// Set when the stylesheets must be re-read — see [#restyle()].
    private boolean stylesDirty = true;

    /// How many frames this window has painted.
    private int painted;

    /// Run after every frame this window paints — the launcher's, for its first
    /// window, which is where `--frames=` and `--resize=` are counted.
    private Runnable afterPaint = () -> {};

    /// The modal windows of this one that are open, the newest last: while
    /// there is one, this window takes no input.
    private final List<HostedWindow> modals = new ArrayList<>();

    /// Told once this window is closed and its tree gone.
    private final List<Runnable> closeListeners = new ArrayList<>();

    private boolean closing;

    private boolean disposed;

    /// Wires a window that is open and not yet showing anything.
    ///
    /// @param owner the window this one belongs to, or null
    /// @param modal whether it blocks `owner` while it is open
    HostedWindow(Launcher app, Window window, @Nullable HostedWindow owner, boolean modal) {
        this.app = Objects.requireNonNull(app, "app");
        this.window = Objects.requireNonNull(window, "window");
        this.owner = owner;
        this.modal = modal && owner != null;

        router = new PointerRouter();
        window.pointerRouter(router);
        // A tooltip is an attribute, shown on hover *and* on keyboard focus after
        // a delay. The router knows when either moved and opens nothing; this
        // class owns the window, so it is where the two meet.
        // Held rather than dropped: the registration outlives this line, and a
        // returned handle nobody keeps is the shape that makes a leak invisible.
        pointing = router.onPointingChanged(this::pointingChanged);

        // A popup is positioned as an offset from its owner, so **moving** the
        // window carries it along and only **resizing** moves what it was
        // anchored to. Re-placing here is what stops a menu opened at the bottom
        // of a short window from hanging off a taller one, and what keeps a
        // right-aligned heading's menu under the heading.
        // In the launcher's own hook and not the application's slot: this runs
        // before `start`, and the application's `onResize` must stay its own.
        window.launcherOnResize(resized -> replaceAfterPaint = true);

        // A **move** does not move the anchor and does not need a paint: the
        // frame on screen is still the right one, and `anchor(id)` answers from
        // it. What moved is the work area *in this window's coordinates*, so a
        // menu that was flipped or shifted against a screen edge has to be asked
        // again — immediately, from the capture that is already current.
        window.launcherOnMove(position -> replacePopups());

        // The desktop's light-or-dark setting, forwarded to whoever asked for it.
        // Installed unconditionally rather than on the first listener: there is one
        // handler slot per window, and taking it here means nothing else can be
        // wired into it later and quietly win.
        window.onSystemThemeChanged(this::notifySystemTheme);

        // Whatever belongs to this window goes before it does.
        window.launcherOnClose(this::closing);

        // A press on nothing, or an Escape, closes whatever is open over this
        // window. Neither reaches a widget, which is why it is watched here
        // rather than handled by one.
        window.inputWatcher(new Window.InputWatcher() {
            @Override
            public boolean pressed(PointerEvent.@Nullable Button button, float x, float y) {
                // **A press that dismissed something is a dismissal and not a
                // click**, which is what every desktop does: with a menu open,
                // the click that puts it away does not also press the button it
                // landed on. The rule for the secondary button below is the
                // general one.
                //
                // Without it a control that opens its own popup cannot be closed
                // by clicking it again: the press dismisses the list and the
                // release then reads as "open it", so a `select` toggles twice
                // and stays open.
                if (dismissPopups()) {
                    return true;
                }
                // The secondary button, on a widget that named a menu: the press
                // is *taken*, so it does not also reach whatever it landed on —
                // right-clicking a button should open its menu, not press it.
                return button == PointerEvent.Button.SECONDARY && openContextMenu(x, y);
            }

            /// An exit that arrives within a moment of the toolkit opening a
            /// window over the pointer is the window, not the pointer.
            @Override
            public boolean exited() {
                return swallowExit();
            }

            @Override
            public boolean keyPressed(Key key, Modifiers modifiers, boolean repeat) {
                // The topmost popup that wants keys at all, which is not always
                // the topmost popup: a *panel* is open over the window the whole
                // time something is selected and must take nothing from it.
                var top = topmostKeyboardPopup();
                if (top == null) {
                    // `Escape` is still a dismissal, because declining keys and
                    // refusing to close are different promises — a panel that must
                    // survive one says so with `lightDismiss(false)`, and then
                    // this returns false and the key reaches the window.
                    if (key == Key.ESCAPE && topmostPopup() != null && dismissTopmostPopup()) {
                        return true;
                    }
                    // **The keyboard's right-click**, and only while nothing is
                    // open over the window: with a menu already showing, the
                    // menu key belongs to the menu.
                    //
                    // `Shift+F10` beside the menu key rather than instead of it.
                    // A Mac keyboard has no menu key at all, and a PC one that
                    // does still has users who reach for the pair — so the two
                    // are companions everywhere, which is what every other
                    // desktop toolkit binds.
                    if (key == Key.MENU && modifiers.none()) {
                        return openContextMenuForFocus();
                    }
                    if (key == Key.F10 && modifiers.shift() && !modifiers.control() && !modifiers.alt()) {
                        return openContextMenuForFocus();
                    }
                    return false;
                }
                if (key == Key.ESCAPE) {
                    // **The innermost, not the stack.** A press *outside* a chain
                    // dismisses the whole thing, because the user pointed at
                    // something else; `Escape` steps back out of it one menu at a
                    // time, which is what every desktop does and what makes a
                    // submenu escapable without losing the menu that opened it.
                    return dismissTopmostPopup();
                }
                // While a menu is open the keyboard belongs to it, whether or not
                // the platform moved focus there — otherwise an arrow would move
                // the selection in the window *underneath* the menu.
                return top.handleKey(key, modifiers, repeat);
            }
        });
    }

    /// Mounts `root` under the window's own node and starts painting it.
    ///
    /// The root goes *under* the window's node from the first frame, whether or
    /// not anything is floating yet: a layer that appeared with the first
    /// overlay would re-parent the whole application to show a toast, and
    /// re-parenting is what throws state away.
    void mount(Widget root) {
        tree = new ElementTree(new WindowRoot(root, overlays), this);
        // A `setState` anywhere in the tree asks for a frame. Without it the
        // change waits for some unrelated event to paint, which is a widget that
        // reacts one interaction late.
        tree.onDirty(window::repaint);
        router.focusRoot(tree.root());

        // Held for the life of the window, which is the whole point of it: the
        // Yoga nodes, their layout cache and the measure callbacks behind every
        // paragraph survive from frame to frame, so a frame where nothing changed
        // re-lays out nothing.
        render = RenderTree.create();

        // The three objects a frame walks, and the order it walks them in. Built
        // once beside them because all three outlive a frame.
        sequence = FrameSequence.over(tree, render, router);

        window.onPaint(this::paint);
    }

    /// Run after every frame — see [#afterPaint].
    void afterPaint(Runnable action) {
        this.afterPaint = Objects.requireNonNull(action, "action");
    }

    /// How many frames this window has painted.
    int painted() {
        return painted;
    }

    /// The window this one belongs to, or null.
    @Nullable
    HostedWindow owner() {
        return owner;
    }

    /// Asks for the stylesheets to be read again before the next frame — what
    /// a [#restyle()] on any window does to every window.
    void stylesChanged() {
        stylesDirty = true;
        window.repaint();
    }

    /// A modal window of this one opened: input stops here until it closes.
    void modalOpened(HostedWindow modalWindow) {
        modals.add(modalWindow);
        dismissPopups();
        hideTooltip();
        window.blockInput(this::raiseModal);
    }

    /// A modal window of this one closed: input comes back once none is left.
    void modalClosed(HostedWindow modalWindow) {
        modals.remove(modalWindow);
        if (modals.isEmpty()) {
            window.blockInput(null);
        }
    }

    /// A press on this window while a modal one is open brings that one back
    /// to the front, and says why nothing happened here.
    private void raiseModal() {
        if (modals.isEmpty()) {
            return;
        }
        var top = modals.getLast().window();
        if (!top.raise()) {
            top.requestAttention(dev.goldberry.render.window.Attention.BRIEFLY);
        }
    }

    /// The window began to close: what belongs to it closes first, its owner
    /// gets its input back, and the tree is taken down on the next turn of
    /// the loop rather than under whatever handler asked for the close.
    private void closing() {
        if (closing) {
            return;
        }
        closing = true;
        app.closing(this);
        if (modal && owner != null) {
            owner.modalClosed(this);
        }
        hideTooltip();
        GoldberryRuntime.get().loop().ui().execute(this::dispose);
    }

    /// Takes this window's trees down, in the reverse of the order they were
    /// built, and tells whoever asked. Idempotent.
    ///
    /// Leaves the window itself alone: a window still open when the loop ended
    /// is the backend's to close as it shuts down, which is the order the
    /// launcher has always kept.
    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        hideTooltip();
        if (focusCheck != null) {
            focusCheck.cancel();
            focusCheck = null;
        }
        // The registration for "the hover or the focus moved". Given back rather
        // than left to die with the router, because a returned handle nobody keeps
        // is the shape that makes a leak invisible.
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
        placements.clear();
        // Null when the application's `start` threw before there was a root.
        //noinspection ConstantValue
        if (tree != null) {
            tree.unmount();
        }
        //noinspection ConstantValue
        if (render != null) {
            render.close();
        }
        app.disposed(this);
        for (var listener : List.copyOf(closeListeners)) {
            listener.run();
        }
        closeListeners.clear();
    }

    /// Whether this window's tree has been taken down.
    boolean isDisposed() {
        return disposed;
    }

    // --- WindowHost -----------------------------------------------------------

    @Override
    public void close() {
        window.close();
    }

    @Override
    public boolean isOpen() {
        return window.isOpen() && !closing;
    }

    @Override
    public Subscription onClose(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (disposed) {
            action.run();
            return () -> {};
        }
        // An object of its own, removed by identity: the same action registered
        // twice is two registrations.
        var registration = new Runnable() {
            @Override
            public void run() {
                action.run();
            }
        };
        closeListeners.add(registration);
        return () -> closeListeners.remove(registration);
    }

    @Override
    public List<Display> displays() {
        return GoldberryRuntime.get().backend().displays();
    }

    @Override
    public Optional<WindowHost> openWindow(WindowSpec spec, Widget root) {
        return app.openWindow(spec, root, this);
    }

    /// What is floating over the content, watched by [WindowRoot] rather than
    /// handed to it: the root widget of an element tree cannot be swapped, so a
    /// list that changes has to be one the root *reads*.
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
    private record Placed(@Nullable String anchorId, LogicalRect anchor, Placement placement) {}

    /// What [#replacePopups] needs, per open popup.
    ///
    /// An `IdentityHashMap` rather than a `Map`, and the declaration is the
    /// documentation: a `Popup` is looked up by *which popup it is*, and two
    /// popups over the same anchor are two entries.
    private final IdentityHashMap<Popup, Placed> placements = new IdentityHashMap<>();

    /// Set when the window is resized, acted on at the end of the next paint —
    /// see [#replacePopups] for why the two are not the same moment.
    private boolean replaceAfterPaint;

    /// Puts every open popup back where its anchor now is.
    ///
    /// Called from the three things that move an anchor without moving the popup
    /// with it:
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
            if (!popup.isOpen()) {
                // Closed by an earlier turn of this very loop: a popup that
                // followed a vanishing anchor takes the ones stacked on it with
                // it, and those are entries in this map too.
                continue;
            }
            LogicalRect anchor;
            if (placed.anchorId() == null) {
                anchor = placed.anchor();
            } else {
                var region = anchor(placed.anchorId()).filter(this::stillOnScreen);
                if (region.isEmpty()) {
                    // The anchor is gone — scrolled out of the viewport that
                    // confines it, or not painted at all. Following it out of
                    // sight would leave a menu pointing at a widget nobody can
                    // see, and the placement would clamp it back to the work
                    // area next to something it does not belong to.
                    closeFrom(popup);
                    continue;
                }
                anchor = region.get().painted();
            }
            var at = placed.placement()
                    .place(anchor, popup.bounds().size(), placeableArea())
                    .at();
            if (!at.equals(popup.offset())) {
                popup.move(at);
            }
        }
    }

    /// Whether an anchor is still somewhere a user could look at it.
    ///
    /// Two questions, because one rectangle can leave by two routes and the
    /// clip only knows about the first:
    ///
    /// - [HitTest.Region#isVisible()] — has it left the viewport that clips it,
    ///   which is what a scroll does to it;
    /// - the window's own rectangle, for an anchor that left by some other route.
    ///   A box with no clipping ancestor is captured under an infinite clip,
    ///   which admits everything, so the clip alone would call it visible for
    ///   ever — see
    /// [dev.goldberry.paint.Clip#NONE].
    ///
    /// The window and **not** [#placeableArea()]: the work area is where a popup
    /// may be *placed*, and it is routinely larger than the window. Whether the
    /// anchor is drawn is a question about the window it is drawn in.
    private boolean stillOnScreen(HitTest.Region region) {
        if (!region.isVisible()) {
            return false;
        }
        var painted = region.painted();
        var size = window.size();
        return painted.right() > 0
                && painted.left() < size.width()
                && painted.bottom() > 0
                && painted.top() < size.height();
    }

    /// Closes `popup` and every popup opened after it.
    ///
    /// A submenu is anchored to a rectangle **inside** the menu it came from, so
    /// it has no name to be re-resolved and would not notice its root going. The
    /// open order is the containment order here — a popup opened while another
    /// was up is either its submenu or something standing on it — so the stack
    /// above the one that lost its anchor goes with it, which is what
    /// [#dismissPopups()] already does for a press.
    ///
    /// `lightDismiss(false)` is not consulted. It says that *input* does not
    /// close this popup; an anchor that stopped being drawn is not input, and a
    /// tooltip outliving the menu it was describing is the same orphan by a
    /// shorter route.
    private void closeFrom(Popup popup) {
        var from = popups.indexOf(popup);
        if (from < 0) {
            popup.close();
            return;
        }
        for (var other : List.copyOf(popups.subList(from, popups.size()))) {
            if (other.isOpen()) {
                other.close();
            }
        }
        popups.removeIf(open -> !open.isOpen());
        placements.keySet().removeIf(open -> !open.isOpen());
    }

    /// Whether any open popup was anchored to an **id**, which is the only kind
    /// that can follow anything.
    ///
    /// A popup opened against a rectangle a caller computed has nothing to be
    /// re-resolved against: the rectangle is all there ever was, and re-placing
    /// it every frame would put it back where it already is. An id is a question
    /// the last paint can answer again, and the answer moves when the widget
    /// does.
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

    /// The renderer for this frame, built if the stylesheets have moved.
    ///
    /// Called from the paint path and from [#popup], which is why it is a method:
    /// a popup opened from `Application#start` has to measure its content, and
    /// measuring needs a renderer before the first frame has asked for one.
    ///
    /// A new renderer means new resolved styles — but **not** new animations,
    /// which live on the elements a restyle does not touch, so a transition in
    /// flight when the theme changes carries on into the new colours rather than
    /// snapping.
    private WidgetRenderer renderer() {
        if (stylesDirty || renderer == null) {
            renderer = new WidgetRenderer(app.stylesheets(), app.fonts())
                    .frames(window.frames())
                    .clock(app.clock())
                    // The desktop's reduce-motion switch, obeyed rather than
                    // merely offered: the desktop is asked once and a renderer
                    // starts where it said. Empty is "animate", because a default
                    // is not an instruction — and an application that disagrees
                    // calls `reducedMotion` on its own renderer afterwards.
                    .reducedMotion(window.reducedMotion().orElse(false))
                    // What `@media (prefers-color-scheme: …)` is asked about.
                    // Light where the desktop does not say, which is CSS's
                    // reading of "no preference".
                    .colorScheme(window.systemTheme().orElse(SystemTheme.LIGHT));
            stylesDirty = false;
            app.lintStylesheets();
        }
        return renderer;
    }

    private void paint(Frame frame) {
        // Four timestamps rather than four `if (traced)` pairs: the stages are
        // what a `hud` shows, so they are measured on every frame or the number
        // on screen would be a different frame's. Five `nanoTime` calls against a
        // frame that costs hundreds of microseconds is not a cost worth a branch.
        var beganAt = System.nanoTime();
        if (FrameTrace.ENABLED) {
            tree.trace().reset();
        }

        // Before the build rather than after it, so a change a sweep notices is a
        // change *this* frame shows. A listener marks an element dirty, and the
        // flush below is what a dirty element is waiting for -- the other order
        // would show it one frame late.
        //
        // Nothing at all for a woven model: `refresh` returns false without
        // looking, which is what makes this line free in a native image.
        for (var model : app.models()) {
            Models.refresh(model);
        }

        // Prepare, flush, render, lay out -- the four steps whose order is the
        // load-bearing part, and they are not written here. `Offscreen` runs the
        // same four in the same order from the same sequence, and the reasons
        // each step is in front of the next one live there with them.
        // `renderer()` is lazy, so this call is also what creates it on the first
        // frame -- once.
        var stages = sequence.layOut(frame, renderer(), beganAt);
        var builtAt = stages.builtAt();
        var styledAt = stages.styledAt();
        var laidOutAt = stages.laidOutAt();

        // What differs from the last frame, computed before painting because the
        // clip has to be in place before anything is drawn.
        //
        // **This half stays here**, and it is the step the shared sequence does
        // not own: a window paints the damaged rectangles because the backend
        // promises last frame's pixels are still there, and a buffer has no last
        // frame to promise anything about.
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
        // can see. Kept as well as handed over: `anchor` answers from the same
        // capture, so a menu opens under where its button *was drawn* rather
        // than where a fresh layout would put it. The window bounds go in first,
        // and that ordering is the sequence's too.
        regions = sequence.captureRegions(frame, router);

        // **After the regions**, which is the whole of why this is a flag and not
        // a call in the resize handler: `anchor(id)` answers from the capture the
        // last paint produced, and during the resize handler that capture is
        // still the *old* window's. Re-placing there would put every menu back
        // where its heading used to be, which is the bug rather than the fix.
        //
        // And on **every** frame while something is anchored by id, which is the
        // other half of the same claim: a `popover` hanging off a widget in a
        // `scroll` has to travel with it, and a scroll is a frame rather than an
        // event anybody reports. Guarded by the id, so a window with no anchored
        // popup open pays nothing.
        if (replaceAfterPaint || followsAnAnchor()) {
            replaceAfterPaint = false;
            replacePopups();
        }

        // The frame loop is idle when no animation is active: ask for another
        // frame *only* while something is moving.
        if (renderer().isAnimating()) {
            window.repaint();
        }

        if (FrameTrace.ENABLED) {
            traceFrame(beganAt, builtAt, styledAt, laidOutAt, rasterizedAt, damage.size());
        }

        painted++;
        afterPaint.run();
    }

    /// One line per frame that did something, for `-Dgoldberry.trace.frames=true`.
    ///
    /// The stage timings say which part of a frame is expensive and the counts
    /// say why: a `style` of 12ms next to `resolved 74` is a cascade running on
    /// the whole tree, and the `subtree walks` line names the node and the state
    /// that asked for it.
    ///
    /// Quiet frames are skipped unless `=all`, because an idle loop at 60fps
    /// otherwise writes a line a frame saying nothing happened — and the frames
    /// worth reading are the ones next to the click.
    private void traceFrame(
            long beganAt, long builtAt, long styledAt, long laidOutAt, long rasterizedAt, int damageRects) {
        var trace = tree.trace();
        if (trace.isQuiet() && !FrameTrace.ALL_FRAMES) {
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

    /// This window's registration for "the hover or the focus moved", closed
    /// when the window goes.
    private @Nullable Subscription pointing;

    /// What an application does when a widget that named a menu is right-clicked
    /// — see [Host#onContextMenu].
    private @Nullable ContextMenuHandler contextMenus;

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
    /// The menu key, and `Shift+F10` on the keyboards that have no menu key.
    /// Two things differ from the pointer's half and both follow from there being
    /// no pointer.
    ///
    /// It starts at the **focused** element rather than the hovered one, which is
    /// what "the keyboard's position" means — and it is the reason this is worth
    /// building at all: a right-click is a thing only a pointer can do, and
    /// everything a pointer can do has to be reachable from the keyboard.
    ///
    /// And it is anchored to that element's **painted rectangle** rather than to
    /// a point, because there is no point to anchor to. The menu therefore hangs
    /// off the bottom of whatever has the focus ring, which is where a reader is
    /// already looking. The rectangle comes from the last painted frame for
    /// [#anchor]'s reason: a key event has no way to reach the
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
    private boolean openContextMenu(@Nullable Element from, LogicalRect anchor) {

        if (contextMenus == null) {
            return false;
        }
        // The deepest widget on the walk that can make itself the subject, held
        // rather than told: a menu that never opens must not move a selection,
        // because a selection that changed with nothing to show for it is a
        // gesture with no visible cause.
        Selects subject = null;
        for (var node = from; node != null; node = node.parent() instanceof Element parent ? parent : null) {
            if (subject == null && node.widget() instanceof Selects selects) {
                subject = selects;
            }
            var named =
                    node.widget() instanceof Attributed<?> a ? a.attributes().contextMenu() : null;
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
    /// waiting. The design system's `tooltip` row gives this number, and it is
    /// the **fallback** rather than the figure.
    ///
    /// The token is [#TOOLTIP_DELAY_TOKEN] and it ships in `controls.css`, which
    /// is `:widgets`' — the same arrangement `--gb-list-row-height` has, and for
    /// the same reason: a `:core` default must not need the catalog to exist.
    private static final double TOOLTIP_DELAY_MS = 500;

    /// How long a tooltip waits when one is **already showing** and the pointer
    /// has moved to a different node.
    ///
    /// The design system's `tooltip` row is two numbers — 500ms to show and
    /// 100ms to move between — and the second is what makes a row of toolbar
    /// buttons readable: having decided to read one tooltip, a user reading the
    /// next should not serve the full sentence of hover intent again. The same
    /// move repositions at once and never slides, which is the drawing half of
    /// the same rule.
    private static final double TOOLTIP_MOVE_DELAY_MS = 100;

    /// The design system's `tooltip` row, as component-token defaults.
    ///
    /// Durations rather than lengths, which is what
    /// [dev.goldberry.widget.BuildContext#duration] is for: a
    /// component metric measured in milliseconds is still a component metric, and
    /// component metrics ship as tokens.
    private static final String TOOLTIP_DELAY_TOKEN = "--gb-tooltip-delay";

    private static final String TOOLTIP_MOVE_DELAY_TOKEN = "--gb-tooltip-delay-move";

    /// The tooltip that is showing, or null.
    private @Nullable Popup tooltip;

    /// When the last tooltip was opened, in `System.nanoTime` units, or 0.
    ///
    /// **X11 reports that the pointer left a window when another window is mapped
    /// over it**, which is exactly what opening a tooltip does — and delivering
    /// that exit clears the hover and the cursor of the widget the tooltip is
    /// describing. Headlessly the cursor survives, so the fault is the platform's
    /// and not the router's.
    private long tooltipOpenedAt;

    /// How long after opening a tooltip an exit is treated as the toolkit's own
    /// doing.
    ///
    /// Bounded rather than a flag that waits for the next exit: on a driver that
    /// sends no spurious exit at all, a flag would swallow the user's *real* one
    /// whenever it eventually came.
    private static final long SPURIOUS_EXIT_NANOS = Duration.ofMillis(250).toNanos();

    /// Whether the exit that just arrived is the one opening a tooltip provokes.
    private boolean swallowExit() {
        return tooltipOpenedAt != 0 && System.nanoTime() - tooltipOpenedAt < SPURIOUS_EXIT_NANOS;
    }

    /// The node it belongs to, so a hover that returns to the same widget does
    /// not close and reopen it.
    private @Nullable Element tooltipOwner;

    /// The delay in flight, cancelled by anything that moves.
    private EventLoop.@Nullable Timer tooltipTimer;

    /// The pointer moved to a different node, or focus did.
    ///
    /// Either can summon a tooltip and either can dismiss one, which is why there
    /// is one handler: a keyboard user tabbing along a toolbar gets the same
    /// tooltips a pointer user does.
    private void pointingChanged() {
        var target = tooltipTarget();
        if (target == tooltipOwner) {
            return;
        }
        // Read **before** the hide, because the hide is what makes it false: the
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

    /// The tooltip delay, resolved against the node the tooltip is for.
    ///
    /// Asked of the **target** rather than of the window, so a panel may set the
    /// token for what is inside it — which is what a custom property inheriting
    /// down the tree already means, and is the only reading that does not make
    /// this a global setting wearing a token's clothes.
    ///
    /// A tooltip already showing takes the shorter of the two. The full delay is
    /// hover *intent* — the question "did you mean to stop here?" — and a user
    /// who is reading tooltips has already answered it.
    private Duration tooltipDelay(Element target, boolean moving) {

        var millis = moving
                ? target.duration(TOOLTIP_MOVE_DELAY_TOKEN, TOOLTIP_MOVE_DELAY_MS)
                : target.duration(TOOLTIP_DELAY_TOKEN, TOOLTIP_DELAY_MS);
        // Clamped at zero rather than refused: a negative delay is a stylesheet
        // being wrong about something that cannot fail, and "show it at once" is
        // the only reading of it that draws anything.
        return Duration.ofMillis((long) Math.max(0, millis));
    }

    /// The node whose tooltip should show: what the pointer is on, or — when the
    /// pointer is on nothing — what the **keyboard** is on.
    ///
    /// The pointer wins because it is the more recent statement of intent: a
    /// keyboard user who reaches for the mouse is looking at where the mouse is.
    ///
    /// ## Keyboard focus, and not merely focus
    ///
    /// A tooltip shows on hover *and on keyboard focus*, and the second half of
    /// that has to mean what it says. **Clicking a button focuses it** — so a
    /// fallback that asked only [dev.goldberry.input.PointerRouter#focused()]
    /// would keep the tooltip alive after the pointer left the thing that had
    /// just been clicked: `hovered` goes null, focus is still the button, the
    /// target has not changed, and `pointingChanged` returns early without hiding
    /// anything. The tooltip would sit there until something else took the focus.
    ///
    /// `focusedFromKeyboard()` is the distinction the router already keeps for
    /// `:focus-visible`, and it is the same distinction for the same reason:
    /// focus that arrived by pointer is a side effect of the click, not a
    /// statement about where the user is working. **A tooltip follows the focus
    /// ring**, which is one sentence and is also exactly what the code does.
    private @Nullable Element tooltipTarget() {
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
    private @Nullable Element withTooltip(@Nullable Element element) {
        for (var node = element; node != null; node = node.parent() instanceof Element parent ? parent : null) {
            if (tooltipTextOf(node) != null) {
                return node;
            }
        }
        return null;
    }

    private static @Nullable String tooltipTextOf(Element element) {
        return element.widget() instanceof Attributed<?> a ? a.attributes().tooltip() : null;
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
        tooltip = tooltipPopup(new TooltipPanel(text), anchor.get()).orElse(null);
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
    private Optional<LogicalRect> anchorOf(Element element) {
        for (var region : regions) {
            if (region.owner() == element) {
                return Optional.of(region.bounds());
            }
        }
        return Optional.empty();
    }

    /// The popup on top that **wants the keyboard** — the last one opened that is
    /// still open and has not declined keys ([Popup#keyboard(boolean)]).
    ///
    /// Separate from [#topmostPopup()] because the two questions are different and
    /// only one of them is about keys: a press outside dismisses whatever is
    /// topmost, panel or not, while a key belongs to the topmost thing that asked
    /// for one. A panel over a menu therefore leaves the menu operable by arrows,
    /// which is what "a panel is not in the keyboard's way" has to mean if it
    /// means anything.
    private @Nullable Popup topmostKeyboardPopup() {
        for (var i = popups.size() - 1; i >= 0; i--) {
            var popup = popups.get(i);
            if (popup.isOpen() && popup.wantsKeyboard()) {
                return popup;
            }
        }
        return null;
    }

    /// The popup on top: the last one opened that is still open.
    private @Nullable Popup topmostPopup() {
        for (var i = popups.size() - 1; i >= 0; i--) {
            if (popups.get(i).isOpen()) {
                return popups.get(i);
            }
        }
        return null;
    }

    /// The pending "has the application really lost focus" check, or null.
    private EventLoop.@Nullable Timer focusCheck;

    /// Some window's focus changed. If the application ends up with none of it,
    /// or another of its own windows has it, every light-dismissed popup of this
    /// one goes away.
    ///
    /// A menu left floating over the application the user switched *to* is the
    /// symptom this exists for, and it is worse than it sounds: a popup is
    /// always-on-top by kind, so it stays visible over the other application's
    /// window. A menu left open over one window while the user works in a
    /// second is the same orphan by a shorter route.
    void focusMayHaveLeft() {
        if (popups.isEmpty()) {
            return;
        }
        if (focusCheck != null) {
            focusCheck.cancel();
        }
        focusCheck = after(app.popupSettle(), () -> {
            focusCheck = null;
            if (!GoldberryRuntime.get().anyWindowFocused() || app.anotherWindowFocused(this)) {
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
        // Each popup's own answer, added up. The question is "did this press put
        // something away", and it was asked as "is everything shut now" —
        // which a `lightDismiss(false)` popup answers no to for as long as it is
        // open, whatever the press did. A tooltip is exactly that, and a tooltip
        // is open over precisely the control a menu is most likely to be
        // dismissed by: the press closed the menu and was then let through as a
        // click on the button under it, which is the double activation this
        // rule exists to prevent.
        var dismissed = false;
        for (var popup : List.copyOf(popups)) {
            dismissed |= popup.dismissedByInput();
        }
        popups.removeIf(popup -> !popup.isOpen());
        return dismissed;
    }

    // --- Host ---------------------------------------------------------------

    @Override
    public Clock clock() {
        return app.clock();
    }

    @Override
    public void repaint() {
        window.repaint();
    }

    /// Every window's, not this one's: the stylesheets are the application's,
    /// and a theme switched from a settings window has to reach the first one.
    @Override
    public void restyle() {
        app.restyleAll();
    }

    @Override
    public void title(String title) {
        window.title(title);
    }

    @Override
    public void shortcut(Shortcut accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void shortcut(Shortcut accelerator, Runnable action, Object owner) {
        router.shortcut(accelerator, action, owner);
    }

    @Override
    public void shortcut(String accelerator, Runnable action) {
        router.shortcut(accelerator, action);
    }

    @Override
    public void modifierTap(ModifierKey modifier, Runnable action, Object owner) {
        // The window's rather than the router's: a tap is read from the platform
        // keycode, which is the one thing the router never sees.
        window.modifierTaps().bind(modifier, action, owner);
    }

    @Override
    public void removeModifierTap(ModifierKey modifier, Object owner) {
        window.modifierTaps().unbind(modifier, owner);
    }

    @Override
    public void removeShortcut(Shortcut accelerator) {
        router.removeShortcut(accelerator);
    }

    @Override
    public void removeShortcut(Shortcut accelerator, Object owner) {
        router.removeShortcut(accelerator, owner);
    }

    @Override
    public void removeShortcut(String accelerator) {
        router.removeShortcut(Shortcut.of(accelerator));
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
        // Never null, for WindowRoot.children's reason.
        //noinspection DataFlowIssue
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
    /// the desktop currently says. Each registration is removed by the
    /// subscription it returned, which a tray following the setting closes with
    /// itself.
    private final List<Consumer<SystemTheme>> systemThemeListeners = new ArrayList<>();

    @Override
    public Optional<SystemTheme> systemTheme() {
        return window.systemTheme();
    }

    @Override
    public Optional<Boolean> reducedMotion() {
        return window.reducedMotion();
    }

    @Override
    public Subscription onSystemThemeChanged(Consumer<SystemTheme> listener) {
        Objects.requireNonNull(listener, "listener");
        // An object of its own per registration, and removed by identity: the
        // same listener registered twice is two registrations, closing one
        // leaves the other, and closing it again finds nothing. A lambda here
        // would not promise a fresh instance.
        var registration = new Consumer<SystemTheme>() {
            @Override
            public void accept(SystemTheme theme) {
                listener.accept(theme);
            }
        };
        systemThemeListeners.add(registration);
        return () -> systemThemeListeners.remove(registration);
    }

    /// Tells every listener, over a copy: a listener that reacts by adding another
    /// one — a screen that appears because the theme changed — must not be a
    /// `ConcurrentModificationException`.
    private void notifySystemTheme(SystemTheme theme) {
        // The cascade first: a sheet with `@media (prefers-color-scheme: dark)`
        // follows the desktop without the application doing anything. A
        // renderer built later asks the window itself.
        if (renderer != null) {
            renderer.colorScheme(theme);
            window.repaint();
        }
        for (var listener : List.copyOf(systemThemeListeners)) {
            listener.accept(theme);
        }
    }

    @Override
    public Optional<HitTest.Region> anchor(String id) {
        Objects.requireNonNull(id, "id");
        for (var region : regions) {
            if (region.owner() instanceof Element element
                    && element.widget() instanceof Styled styled
                    && id.equals(styled.id())) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalPoint at, LogicalSize size) {
        return popup(content, at, size, PopupKind.MENU);
    }

    @Override
    public Optional<Popup> tooltip(Widget content, LogicalPoint at, LogicalSize size) {
        return popup(content, at, size, PopupKind.TOOLTIP);
    }

    private Optional<Popup> popup(Widget content, LogicalPoint at, LogicalSize size, PopupKind kind) {

        Objects.requireNonNull(content, "content");
        return open(new ElementTree(content, this), RenderTree.create(), new PopupSpec(at, size, kind));
    }

    /// Asks the backend for the window and wires the trees to it.
    private Optional<Popup> open(ElementTree tree, RenderTree render, PopupSpec spec) {

        var backend = GoldberryRuntime.get().backend().createPopup(window.backendWindow(), spec);
        if (backend.isEmpty()) {
            // The driver has none. The caller's fallback is the overlay layer,
            // clipped to the window, and saying so is more use than an empty
            // Optional on its own.
            tree.unmount();
            render.close();
            LOG.trace("this platform has no popup windows; a {} will have to be an overlay", spec.kind());
            return Optional.empty();
        }

        var popup = new Popup(
                backend.get(),
                Window.over(backend.get()),
                tree,
                render,
                this::renderer,
                () -> popups.removeIf(open -> !open.isOpen()));
        popups.add(popup);
        return Optional.of(popup);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement) {
        return placed(content, anchor, placement, PopupKind.MENU, 0, null);
    }

    @Override
    public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement, float minimumWidth) {
        return placed(content, anchor, placement, PopupKind.MENU, minimumWidth, null);
    }

    @Override
    public Optional<Popup> popup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, @Nullable Fit fit) {
        return placed(content, anchor, placement, PopupKind.MENU, minimumWidth, fit);
    }

    @Override
    public Optional<Popup> attachedPopup(
            Widget content, LogicalRect anchor, Placement placement, float minimumWidth, Fit fit) {
        // TOOLTIP is the *kind*, not the widget: what it buys here is
        // `NOT_FOCUSABLE`, so the keyboard stays on the field this hangs off.
        return placed(content, anchor, placement, PopupKind.ATTACHED, minimumWidth, fit);
    }

    /// Measure, place, open — the three steps `popover` is made of, shared by the
    /// menu form and the tooltip one because only the kind differs.
    ///
    /// A [Host.Fit] sits between the first two: it is handed what the content
    /// measured and answers with what to open, which is nearly always the same
    /// widget.
    private Optional<Popup> placed(
            Widget content,
            LogicalRect anchor,
            Placement placement,
            PopupKind kind,
            float minimumWidth,
            @Nullable Fit fit) {

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
        // gets a viewport when it outgrows the screen.
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
    private Optional<Popup> tooltipPopup(Widget content, LogicalRect anchor) {
        return placed(content, anchor, Placement.ABOVE.align(Placement.Align.CENTER), PopupKind.TOOLTIP, 0, null)
                .map(popup -> popup.lightDismiss(false));
    }

    @Override
    public Optional<Popup> popup(Widget content, String anchorId, Placement placement) {
        return popup(content, anchorId, placement, 0, null);
    }

    @Override
    public Optional<Popup> popup(
            Widget content, String anchorId, Placement placement, float minimumWidth, @Nullable Fit fit) {

        Objects.requireNonNull(anchorId, "anchorId");
        var anchor = anchor(anchorId);
        if (anchor.isEmpty()) {
            LOG.warn("nothing with id \"{}\" has been painted, so there is nothing to anchor to", anchorId);
            return Optional.empty();
        }
        // `painted()` and not `bounds()`: a menu belongs under where its button
        // was **drawn**, and a button inside a `scroll` is laid out where it
        // always was and drawn a long way from there. The two are the same
        // rectangle for anything nothing transformed, which is nearly every
        // anchor.
        var opened = placed(content, anchor.get().painted(), placement, PopupKind.MENU, minimumWidth, fit);
        // Upgraded from a rectangle to a **name**, which is what makes a resize
        // able to follow the anchor rather than merely re-clamp against the new
        // work area: the id is re-resolved against the frame the resize produced
        // A menu and a dropdown get it through this overload, which takes a name
        // *and* the two things they also need.
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
        // as the control it drops from. Bounded by the cap, because a
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
        // The open popups first, topmost first, then the window. A widget inside
        // a popup is built with this host, so its own request by name -- a tree's
        // typeahead moving to a row, a select's list -- has to be able to reach
        // the popup's router rather than only the window's. Topmost wins for the
        // reason a key goes to it.
        for (var i = popups.size() - 1; i >= 0; i--) {
            var popup = popups.get(i);
            if (popup.isOpen() && popup.focusById(id, fromKeyboard)) {
                return true;
            }
        }
        return router.focusById(id, fromKeyboard);
    }

    @Override
    public EventLoop.Timer after(Duration delay, Runnable action) {
        return GoldberryRuntime.get().loop().after(delay, action);
    }

    @Override
    public FrameStats frames() {
        return window.frames();
    }

    @Override
    public boolean openExternal(String url) {
        Objects.requireNonNull(url, "url");
        return GoldberryRuntime.get().backend().openUrl(url);
    }

    @Override
    public Fonts fonts() {
        return app.fonts();
    }

    @Override
    public Clipboard clipboard() {
        return GoldberryRuntime.get().backend().clipboard();
    }

    @Override
    public Optional<PrimarySelection> primarySelection() {
        return GoldberryRuntime.get().backend().primarySelection();
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
    /// application rather than to any one of its windows.
    ///
    /// **Every row is given every window's repaint**, and without it a tray menu
    /// looks broken in a way nothing reports: a row's handler runs inside the
    /// platform's own pump and produces no event, so nothing asks for a frame,
    /// so the model sweep at the top of [#paint] never runs and a handler that
    /// set a field changed nothing anybody looks at. It is the only input in the
    /// toolkit that arrives without an event behind it, which is why this is the
    /// one call site that has to say so.
    @Override
    public Optional<BackendTray> tray(TraySpec spec) {
        return GoldberryRuntime.get().backend().createTray(spec.andThen(app::repaintAll));
    }

    /// Opens a web page in a window of the engine's own.
    ///
    /// No `andThen` and no repaint, which is the difference from the tray above: a
    /// tray row runs an application's handler and the frame that handler changed
    /// has to be asked for, while nothing a page does touches this window's tree.
    /// What it draws is WebKit's, in a window of its own.
    ///
    /// The page is **not** registered with the launcher and is not closed when
    /// this window is. It belongs to whoever opened it.
    ///
    /// Read more: [The web view](https://goldberry.dev/docs/components/content.html#the-web-view).
    @Override
    public Optional<BackendWebView> webView(WebViewSpec spec) {
        return GoldberryRuntime.get().backend().createWebView(spec);
    }

    /// Opens a web page inside this window, where the window system allows a
    /// child window.
    ///
    /// The rectangle is in this window's **logical** coordinates, which is what a
    /// widget knows about itself; the platform wants its own pixels, so it is
    /// scaled here rather than at every call site.
    @Override
    public Optional<BackendWebView> embeddedWebView(WebViewSpec spec, LogicalRect bounds) {
        Objects.requireNonNull(bounds, "bounds");
        var scale = window.scale().factor();
        return GoldberryRuntime.get()
                .backend()
                .createEmbeddedWebView(
                        spec,
                        window.backendWindow(),
                        Math.round(bounds.origin().x() * scale),
                        Math.round(bounds.origin().y() * scale),
                        Math.max(1, Math.round(bounds.size().width() * scale)),
                        Math.max(1, Math.round(bounds.size().height() * scale)));
    }

    @Override
    public void textInput(boolean active) {
        window.backendWindow().textInput(active);
    }

    @Override
    public Window window() {
        return window;
    }

    /// True while the window is open: every backend with a window answers the
    /// ask, the headless one included, and one that has nothing to do about it
    /// simply sends no event back.
    @Override
    public boolean canFullscreen() {
        return window.isOpen();
    }

    @Override
    public boolean isFullscreen() {
        return window.isFullscreen();
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        window.setFullscreen(fullscreen);
    }

    @Override
    public Subscription onFullscreenChanged(Consumer<Boolean> listener) {
        return window.onFullscreenChanged(listener);
    }

    @Override
    public double displayScale() {
        return window.scale().factor();
    }

    /// The router's answer, which is the only one there is: the deepest mounted
    /// modal is found once per frame beside the hit-test regions, and this reads
    /// that rather than walking the tree again.
    @Override
    public boolean isModal() {
        return router.isModal();
    }

    /// Posted under the application's title, with this window's repaint after
    /// its action for the tray's reason: a click on a notification arrives with
    /// no event behind it.
    @Override
    public boolean notify(Notification notification) {
        return app.notifications().post(app.title(), notification.andThen(this::repaint));
    }

    @Override
    public boolean badge(@Nullable String label) {
        return app.notifications().badge(label);
    }

    /// The backend's menu bar, with the repaint after every row's action, for
    /// the same reason as a notification's.
    @Override
    public boolean applicationMenu(List<AppMenuItem> headings) {
        var bar = GoldberryRuntime.get().backend().menuBar();
        if (headings.isEmpty()) {
            bar.clear();
            return false;
        }
        return bar.show(
                app.title(),
                headings.stream().map(heading -> heading.andThen(this::repaint)).toList());
    }
}
