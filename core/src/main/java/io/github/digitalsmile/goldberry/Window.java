package io.github.digitalsmile.goldberry;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.input.tap.ModifierTaps;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.log.Startup;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.render.popup.BackendPopup;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;
import io.github.digitalsmile.goldberry.stats.FrameRing;
import io.github.digitalsmile.goldberry.stats.FrameStats;

/// A window on the screen.
///
/// This is the front door. Opening one starts the backend and the event loop if
/// they are not already running, so an application never names a backend, builds
/// an event loop, or writes a `switch` over backend events:
///
/// ```java
/// var window = Window.open("Hello", 960, 640);
/// window.onPaint(frame -> frame.fill(0xFF2E3440));
/// Goldberry.run();
/// ```
///
/// Everything below is still there — [io.github.digitalsmile.goldberry.backend]
/// is exported, and an application that wants to drive the SPI directly can. This
/// is the path for the ones that do not.
///
/// Confined to the UI thread, which is the thread that opened the first window.
/// Work that is not instant belongs on [Goldberry#async]; its result comes back
/// here automatically.
public final class Window implements AutoCloseable {

    private static final Logger LOG = Logs.of(Window.class);

    private final GoldberryRuntime runtime;
    private final BackendWindow window;

    private Consumer<Frame> painter = frame -> {};
    private Consumer<LogicalSize> resizeHandler = size -> {};
    private Consumer<LogicalPoint> moveHandler = position -> {};
    private Consumer<DisplayScale> scaleHandler = scale -> {};
    private BooleanSupplier closeHandler = () -> true;

    /// [BackendWindow#lateFrames()] as of the last frame, so the difference is
    /// what belongs to *this* one — the backend's counter is monotonic and this
    /// ring is a window over sixty frames ([ADR-0271]).
    private long backendLateFrames;

    /// Frames painted and then refused by the platform, waiting for the next
    /// frame to bank them. Counted here because this is where the refusal is
    /// caught, and a frame nobody saw is late whatever the reason.
    private long refusedFrames;

    /// Whether this window has ever reached the screen. The first time is what
    /// the start-up timeline is measuring.
    private boolean everPresented;

    /// Reused between frames while the size holds. Repainting is the common case
    /// and a fresh multi-megabyte buffer per frame is a lot of garbage for
    /// something the backend copies out immediately.
    private @Nullable PixelBuffer cached;

    /// What this window's frame loop has been managing lately (§1.7's frame
    /// budget, made visible). Written once per painted frame and read by whatever
    /// is watching — a `hud`, or a test.
    private final FrameRing frames = new FrameRing();

    private Window(GoldberryRuntime runtime, BackendWindow window) {
        this.runtime = runtime;
        this.window = window;
    }

    /// Opens a window of the given logical size.
    ///
    /// The first call starts the toolkit on the calling thread, which becomes the
    /// UI thread for the process.
    public static Window open(String title, float width, float height) {
        return open(WindowSpec.of(title, LogicalSize.of(width, height)));
    }

    /// The window behind this one, for the calls that are the backend's rather
    /// than this class's — opening a popup parented to it.
    BackendWindow backendWindow() {
        return window;
    }

    /// Wraps a window the backend has already made — today, a popup.
    ///
    /// Everything above the SPI is the same for a popup as for a window: it is
    /// painted, it presents, its events arrive through the same pump and its
    /// pointer goes through a router. This is where that sameness is taken
    /// advantage of rather than reimplemented ([ADR-0102]).
    static Window over(BackendWindow backendWindow) {
        Objects.requireNonNull(backendWindow, "backendWindow");
        var runtime = GoldberryRuntime.get();
        var window = new Window(runtime, backendWindow);
        runtime.register(backendWindow, window);
        window.repaint();
        return window;
    }

    /// Opens a window from a full specification — for the cases that need to say
    /// something about resizability or decorations.
    public static Window open(WindowSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var runtime = GoldberryRuntime.get();
        var backendWindow = runtime.backend().createWindow(spec);
        var window = new Window(runtime, backendWindow);
        runtime.register(backendWindow, window);
        LOG.info(
                "window \"{}\" opened: {} at {} -> {}",
                spec.title(),
                backendWindow.size(),
                backendWindow.scale(),
                backendWindow.physicalSize());
        Startup.mark("window \"" + spec.title() + "\" open");
        window.repaint();
        return window;
    }

    /// Sets what to draw. Called on the UI thread whenever the window needs a
    /// frame, and never for a window that is not visible.
    public Window onPaint(Consumer<Frame> painter) {
        this.painter = Objects.requireNonNull(painter, "painter");
        return repaintAnd();
    }

    /// Called after the window's logical size changes. A repaint is already
    /// scheduled; this is for anything else that has to react.
    public Window onResize(Consumer<LogicalSize> handler) {
        this.resizeHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Called after the window's top-left corner moves on the desktop.
    ///
    /// **No repaint is scheduled, and that is the difference from
    /// [#onResize].** Nothing inside the window moved, so its last frame is
    /// still correct; what moved is the window's own coordinate space relative
    /// to the screen's edges, which is a question only something placing another
    /// window against them has to ask ([ADR-0270]).
    public Window onMove(Consumer<LogicalPoint> handler) {
        this.moveHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Called when the window moves to a display with a different scale. The
    /// logical size is unchanged — only the resolution it is drawn at.
    public Window onScaleChange(Consumer<DisplayScale> handler) {
        this.scaleHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Decides what happens when the user asks to close the window.
    ///
    /// Return `false` to keep it open — which is how an "unsaved changes?" prompt
    /// works. The default closes.
    public Window onCloseRequest(BooleanSupplier handler) {
        this.closeHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Asks for a repaint. Coalesced: calling it ten times before the next frame
    /// draws once.
    public void repaint() {
        if (window.isOpen()) {
            window.requestFrame();
        }
    }

    /// The window's size in logical pixels — the units to lay out in.
    public LogicalSize size() {
        return window.size();
    }

    /// The size of the frame buffer behind the window.
    public PhysicalSize pixelSize() {
        return window.physicalSize();
    }

    /// The scale of the display the window is on. Fractional in the ordinary
    /// case: 125% and 150% are what most laptops ship with.
    public DisplayScale scale() {
        return window.scale();
    }

    public String title() {
        return window.title();
    }

    /// Sets the window title.
    public Window title(String title) {
        window.setTitle(title);
        return this;
    }

    public boolean isOpen() {
        return window.isOpen();
    }

    /// Closes the window. The event loop ends when the last one closes.
    @Override
    public void close() {
        if (!window.isOpen()) {
            return;
        }
        if (window instanceof BackendPopup) {
            // A popup opens and closes as often as a menu is used; at INFO it
            // would be the only thing in an application's log.
            LOG.debug("popup closed");
        } else {
            LOG.info("window \"{}\" closed", window.title());
        }
        runtime.forget(window);
        window.close();
        cached = null;
    }

    /// What the painter reported as changed since the last frame, or empty for
    /// "assume everything".
    ///
    /// Set from inside the paint callback by an application that keeps a
    /// `RenderTree` — it is the only thing that knows what moved. Cleared after
    /// every present, so a frame that says nothing gets the whole window, which
    /// is the safe answer and the one every caller had before this existed.
    /// Null, not empty. An **empty** list is a caller saying "nothing changed,
    /// upload nothing", which is what a still window reports and is the whole
    /// point; null is a caller that has not said anything, which has to mean the
    /// whole frame.
    private @Nullable List<DamageRect> damage;

    /// Reports which regions of this frame differ from the last one.
    ///
    /// Call inside `onPaint`, after painting:
    ///
    /// ```java
    /// render.update(frame, renderer.render(tree));
    /// render.paint(frame);
    /// window.damaged(render.damage(frame));
    /// ```
    ///
    /// The backend uploads only these rectangles where the platform lets it. It
    /// is advisory in the strict sense — the pixels outside them are still
    /// whatever was painted — so a caller that gets it wrong shows a stale
    /// region rather than a corrupt one.
    ///
    /// **Ignored on a frame that could not be repainted in part.** If
    /// [#canRepaintPartially()] said no, the buffer had no previous contents to
    /// build on, the painter was expected to draw everything, and uploading a
    /// subset of it would leave the rest of the window showing whatever the
    /// platform had there. So the whole frame goes up and what is reported here
    /// is not consulted (ADR-0158). A painter therefore reports what *changed*
    /// and never has to reason about what the surface underneath it still holds.
    public Window damaged(List<DamageRect> regions) {
        this.damage = List.copyOf(Objects.requireNonNull(regions, "regions"));
        return this;
    }

    /// The buffer the previous frame was painted into, by identity.
    ///
    /// A backend may promise it retains contents and still hand back a *different*
    /// buffer — rotating between two, or reallocating after a resize. Identity is
    /// what catches that, and it is checked separately from the promise because
    /// they fail independently.
    private @Nullable PixelBuffer lastTarget;

    /// Whether the frame currently being painted may be repainted in part.
    private boolean partialRepaint;

    /// Whether only the damaged region of this frame needs repainting.
    ///
    /// **Valid only inside the paint callback.** Three things have to hold, and
    /// they fail independently:
    ///
    /// 1. the backend promises the buffer keeps its contents
    ///    ([BackendWindow#retainsFrameContents()]);
    /// 2. it is the *same* buffer as last frame, not a rotated or reallocated one;
    /// 3. there was a last frame at this size at all.
    ///
    /// When it is false the answer is a full repaint, which is what every frame
    /// did before this existed:
    ///
    /// ```java
    /// var damage = render.damage(frame);
    /// if (window.canRepaintPartially()) {
    ///     render.paint(frame, damage);
    /// } else {
    ///     render.paint(frame);
    /// }
    /// window.damaged(damage);
    /// ```
    ///
    /// `damaged` is called the same way either way: what is reported is what
    /// changed, and a frame this returned false for is uploaded whole regardless
    /// (ADR-0158).
    public boolean canRepaintPartially() {
        return partialRepaint;
    }

    /// What this window's frame loop has been managing over the last frames.
    ///
    /// Live: the same object every call, updated as frames are painted, so a
    /// widget that keeps it keeps something current rather than a snapshot that
    /// went stale the moment it was taken.
    ///
    /// Reading it costs the arithmetic of a mean over at most
    /// [FrameStats#capacity] frames and allocates nothing, which is what makes it
    /// safe to ask inside a `build` that runs every frame.
    public FrameStats frames() {
        return frames;
    }

    /// The same ring, as the thing that can be written to.
    ///
    /// Package-private, because [#frames] is the reading half and everything
    /// outside this package is a reader — the one writer is the launcher's
    /// painter, which times the stages a `hud` shows (ADR-0146).
    FrameRing frameRing() {
        return frames;
    }

    void paint() {
        if (!window.isOpen()) {
            return;
        }
        var size = window.physicalSize();
        if (size.isEmpty()) {
            // Minimized, or mid-resize on a compositor that reports zero. There
            // is nothing to draw into and nothing to show.
            return;
        }
        var traced = LOG.isTraceEnabled();
        // Ungated, unlike the four readings below it: this pair is what
        // [#frames] is made of, and a frame rate that only exists at TRACE would
        // be a diagnostic you have to change the logging configuration to see —
        // and would then be measuring a loop that is also writing a line per
        // frame. Two `nanoTime` calls against a frame is not a cost worth an if.
        var started = System.nanoTime();

        // Paint into the platform's own buffer when it lends one. Otherwise
        // rasterize into ours and let the backend copy -- which is a full frame of
        // memory traffic per frame, so it is the fallback rather than the plan.
        var borrowed = window.acquireFrame();
        PixelBuffer target;
        if (borrowed.isPresent()) {
            target = borrowed.get();
            if (!target.size().equals(size)) {
                // The platform's buffer disagrees with the size just read: a
                // resize landed between the two. Its size is the authoritative
                // one, since it is what will be shown.
                size = target.size();
            }
        } else {
            if (cached == null || !cached.size().equals(size)) {
                LOG.debug("allocating a {} frame buffer", size);
                cached = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
            }
            target = cached;
        }
        // Decided before the painter runs, because the clip has to be in place
        // before anything is drawn -- and recorded on the window rather than
        // passed, because the painter is an application's lambda and this is one
        // more argument it should not have to thread through.
        // Two sources, two answers. A buffer the backend lent is the backend's
        // to promise about. The fallback buffer is *this* window's -- allocated
        // here, reused here, and disturbed by nothing between frames -- so it
        // retains by construction, whatever the backend says about its own.
        partialRepaint = (borrowed.isEmpty() || window.retainsFrameContents())
                && target == lastTarget
                && target.size().equals(size);
        lastTarget = target;

        var allocated = traced ? System.nanoTime() : 0L;

        // Ended before the timestamp and before presenting, in that order: a
        // Blend2D context may still have work in flight, and presenting pixels
        // it has not finished with shows a half-drawn frame. The `finally` is
        // what keeps a painter that throws from leaving the context attached to
        // the platform's surface.
        var frame = Frame.over(target, window.scale());
        var built = traced ? System.nanoTime() : 0L;
        long drawn;
        try {
            painter.accept(frame);
        } finally {
            drawn = traced ? System.nanoTime() : 0L;
            frame.end();
        }
        var painted = System.nanoTime();

        // What went missing between the last frame and this one, from the two
        // things that can tell: the backend's pacer, whose counter is monotonic
        // and so is read as a difference, and the refusals this window caught
        // itself ([ADR-0271]). Before `record`, which is what banks it.
        var lateNow = window.lateFrames();
        frames.late(Math.max(0L, lateNow - backendLateFrames) + refusedFrames);
        backendLateFrames = lateNow;
        refusedFrames = 0;

        // Before `present`, and counted even when that throws: the frame was
        // painted, and a frame the platform then refused because the window was
        // resized under it is a frame this loop still spent (see the catch).
        frames.record(started, painted);
        // Asked of the backend on every frame and cached there: it changes only
        // when the window moves monitors, and a `hud` needs it to know what a
        // frame's budget actually is (ADR-0153).
        frames.displayHertz(window.refreshRate());

        var frameSize = size;
        try {
            // Clamped to the whole frame when this one could not be repainted in
            // part, whatever the painter reported.
            //
            // The two are not the same question and it took a resize to show it.
            // What `damaged` reports is which regions *changed*; what `present`
            // uploads has to be every region that is not already correct on the
            // platform's surface. At a steady size those coincide, because the
            // surface holds the last frame. After a reallocation -- a resize, a
            // rotated buffer -- the surface holds nothing, the painter was told to
            // repaint everything and did, and uploading only what changed leaves
            // the rest of the window showing whatever the compositor had there.
            // Which is black, and during a live resize it is black that flickers
            // (ADR-0158).
            //
            // Here rather than at the call site, because a painter reporting what
            // changed is right and there is nothing for it to do differently --
            // this window is the only thing that knows whether the buffer it is
            // about to present had valid contents to begin with.
            window.present(target, damage == null || !partialRepaint ? List.of(DamageRect.all(frameSize)) : damage);
            damage = null;
            if (!everPresented) {
                everPresented = true;
                Startup.mark("first frame presented");
                Startup.summarize();
            }
            if (traced) {
                var done = System.nanoTime();
                // Where a slow frame went. During a resize this is the difference
                // between "the toolkit is slow" and "the platform is".
                // Paint is split three ways because the split is what said where
                // a frame's time actually goes: attaching the context, the
                // painter's own work, and `end` waiting for Blend2D's workers
                // (ADR-0042, ADR-0045).
                LOG.trace(
                        "frame {} in {}us: buffer {}, paint {} (begin {}, draw {}, end {})," + " present {}",
                        frameSize,
                        (done - started) / 1_000,
                        (allocated - started) / 1_000,
                        (painted - allocated) / 1_000,
                        (built - allocated) / 1_000,
                        (drawn - built) / 1_000,
                        (painted - drawn) / 1_000,
                        (done - painted) / 1_000);
            }
        } catch (RuntimeException e) {
            // A window can be resized between the size being read above and the
            // frame reaching the platform -- which during a drag is common, not
            // exotic. The backend refuses a frame that no longer matches its
            // surface, and rightly: a mismatched blit is corruption. But that is
            // a dropped frame, not a failure, and letting it out of here would
            // end the event loop mid-resize.
            var current = window.isOpen() ? window.physicalSize() : frameSize;
            if (!current.equals(frameSize)) {
                LOG.debug("dropped a {} frame: the window became {} while it was painted", frameSize, current);
                // A frame nobody saw. Banked by the next one, which is the frame
                // whose interval contains the gap this left ([ADR-0271]) — and
                // it is asked for on the line below, so there will be one.
                refusedFrames++;
                repaint();
                return;
            }
            throw e;
        }
    }

    void handleResize(LogicalSize size) {
        LOG.debug("window resized to {} -> {}", size, window.physicalSize());
        // The buffer is NOT dropped here. paint() reallocates when the size no
        // longer matches, which is the same test one step later -- and dropping
        // it eagerly means a multi-megabyte allocation for every resize event a
        // compositor sends, which during a drag is per pointer motion.
        resizeHandler.accept(size);
        repaint();
    }

    void handleMoved(LogicalPoint position) {
        LOG.trace("window moved to {}", position);
        // No repaint. The frame on screen is still the right one -- see
        // [#onMove].
        moveHandler.accept(position);
    }

    void handleScaleChange(DisplayScale scale) {
        LOG.info("window moved to a {} display -> {}", scale, window.physicalSize());
        scaleHandler.accept(scale);
        repaint();
    }

    /// See [#inputWatcher].
    private @Nullable InputWatcher inputWatcher;

    /// Input this window's router will not deliver, for whoever needs it anyway.
    ///
    /// There is one caller and one reason: a [Popup] is dismissed by a press
    /// outside it or by `Escape`, and neither reaches a widget — the press
    /// usually lands on nothing, and `Escape` belongs to no control in
    /// particular. Package-private, because "see every press before anything
    /// else does" is not a thing an application should be handed.
    void inputWatcher(InputWatcher watcher) {
        this.inputWatcher = watcher;
    }

    /// See [#inputWatcher(InputWatcher)].
    interface InputWatcher {

        /// A pointer button went down somewhere in this window.
        ///
        /// The button and the position, because the two things watching this want
        /// different halves: light dismissal only needs to know *that* a press
        /// happened, and a context menu needs to know it was the secondary button
        /// and where it landed ([ADR-0108]).
        ///
        /// @return true when the press has been dealt with — a context menu
        ///         opening takes it, so the press does not also travel to whatever
        ///         it landed on
        boolean pressed(io.github.digitalsmile.goldberry.input.event.PointerEvent.Button button, float x, float y);

        /// A key went down somewhere in this window.
        ///
        /// Returning `true` **takes** the key: this window's own router does not
        /// see it. That is what makes a menu keyboard-operable without the
        /// platform having moved focus into it — arrows and `Enter` reach the
        /// popup's router rather than moving the selection in the window
        /// underneath, which is what they would otherwise do while a menu is
        /// open ([ADR-0104]).
        ///
        /// @return true when the key has been dealt with
        boolean keyPressed(
                io.github.digitalsmile.goldberry.input.key.Key key,
                io.github.digitalsmile.goldberry.input.key.Modifiers modifiers,
                boolean repeat);

        /// The pointer left this window — or the platform says it did.
        ///
        /// Returning `true` swallows it, and there is one caller and one reason:
        /// **opening a window over the pointer makes X11 report that the pointer
        /// left the window underneath**, which it did not. Delivered, that clears
        /// the hover and the cursor of the very widget a tooltip is describing
        /// ([ADR-0111]).
        ///
        /// @return true when the exit is the toolkit's own doing
        default boolean exited() {
            return false;
        }
    }

    /// See [#modifierTaps()].
    private final ModifierTaps modifierTaps = new ModifierTaps();

    /// The taps of a bare modifier key this window recognises (ADR-0223).
    ///
    /// Here rather than on the router because the rule is written in **platform
    /// keycodes**: `Key` names no modifier, so `Alt` reaches the router as
    /// `Key.UNKNOWN` and is indistinguishable there from every letter that
    /// arrives as text. This is the last place the distinction exists.
    ///
    /// Package-private for the same reason [#inputWatcher(InputWatcher)] is: an
    /// application registers one through [Host#modifierTap], which is where the
    /// ownership rule that makes it safe for a widget to bind lives.
    ModifierTaps modifierTaps() {
        return modifierTaps;
    }

    /// Where pointer events go.
    ///
    /// Null until an application asks for one. A window with no router does the
    /// hit testing work of nothing at all, which is what a window painting with
    /// a plain `onPaint` callback should cost.
    private io.github.digitalsmile.goldberry.input.@Nullable PointerRouter router;

    /// Routes this window's pointer events through `router`.
    ///
    /// The window feeds it positions; keeping the hit-test snapshot up to date
    /// after each paint is the caller's job, because only the caller knows what
    /// box tree it painted.
    public Window pointerRouter(io.github.digitalsmile.goldberry.input.PointerRouter router) {
        this.router = router;
        if (router != null) {
            // The router decides the shape and knows nothing about the platform;
            // this is the one wire between the two (§7.3).
            router.onCursorChange(window::setCursor);
            // And the other platform effect focus has: an input method is on
            // while something typed-into has the keyboard, and off otherwise
            // (ADR-0285).
            router.onTextInputChange(window::textInput);
            router.onCaretAreaChange(window::textInputArea);
        }
        return this;
    }

    /// Sets the shape of the pointer over this window (§7.3).
    ///
    /// For an application that decides for itself. A window with a
    /// [#pointerRouter] has this set from the widget under the pointer on every
    /// move, so the two do not mix: whichever spoke last wins, and the router
    /// speaks on the next pointer motion.
    public Window cursor(Cursor cursor) {
        window.setCursor(Objects.requireNonNull(cursor, "cursor"));
        return this;
    }

    void handlePointerMoved(float x, float y, int modifiers) {
        if (router != null) {
            router.pointerMoved(x, y, Modifiers.fromSdl(modifiers));
            repaintIfRestyled();
        }
    }

    /// Repaints when input changed something a stylesheet reacts to.
    ///
    /// `:hover` moving is the ordinary case, and it happens on pointer motion —
    /// which arrives whether or not anything asked for a frame. Without this
    /// every application would have to remember to ask after every event, and
    /// the one that forgot would have a window whose buttons never light up.
    ///
    /// Coalesced by [#repaint()], so a drag across a row of buttons costs one
    /// frame per frame rather than one per event. Costs nothing when nothing
    /// changed: `takeStylesDirty` clears on read, so the question it answers is
    /// exactly "did a pseudo-class change since the last time anyone asked".
    private void repaintIfRestyled() {
        if (router.takeStylesDirty()) {
            repaint();
        }
    }

    void handlePointerPressed(float x, float y, int button, int clickCount, int modifiers) {
        // A press ends whatever keyboard gesture was in progress: `Alt` down,
        // click, `Alt` up is a modified click and not a tap (ADR-0223).
        modifierTaps.interrupted();
        // Before the router, and unconditionally: light dismissal needs to know a
        // press happened *anywhere* in this window, and the router cannot say so
        // — it dispatches to the widget under the pointer, and a press on nothing
        // dispatches to nothing, which is precisely the case a menu has to close
        // on.
        if (inputWatcher != null && inputWatcher.pressed(toButton(button), x, y)) {
            repaintIfRestyled();
            return;
        }
        if (router != null) {
            router.pointerPressed(x, y, toButton(button), clickCount, Modifiers.fromSdl(modifiers));
            repaintIfRestyled();
        }
    }

    void handlePointerReleased(float x, float y, int button, int clickCount, int modifiers) {
        if (router != null) {
            router.pointerReleased(x, y, toButton(button), clickCount, Modifiers.fromSdl(modifiers));
            repaintIfRestyled();
        }
    }

    void handlePointerWheel(float x, float y, float deltaX, float deltaY, int ticksX, int ticksY, int modifiers) {
        // `Alt`+wheel is a gesture on three platforms, so the wheel spoils a tap
        // exactly as a press does.
        modifierTaps.interrupted();
        if (router != null) {
            router.pointerWheel(x, y, deltaX, deltaY, ticksX, ticksY, Modifiers.fromSdl(modifiers));
        }
    }

    /// Whether this window has the keyboard, as the platform last said.
    private boolean focused;

    /// The platform's last word on whether this window is maximized — see
    /// [#isMaximized].
    private boolean maximized;

    /// Whether this window has the keyboard focus.
    ///
    /// A fact about the platform rather than about the widget tree: the element
    /// the *router* has focused is a different question, and a window that is not
    /// focused still has one (ADR-0144).
    public boolean isFocused() {
        return focused;
    }

    /// Whether the window is maximized **as the platform last reported it**.
    ///
    /// Not "whether [#maximize] was called", and that is the decision rather than
    /// an implementation detail ([ADR-0252]). Maximizing is a request a window
    /// manager may refuse, delay or grant in part — a tiling compositor has its
    /// own idea — so a flag set on the way out would be a lie the moment one did.
    /// This answers what `SDL_EVENT_WINDOW_MAXIMIZED` and `…_RESTORED` last said,
    /// which is also what makes the interesting half work: an application can
    /// find out that *the user* maximized it, which is what a "remember my window
    /// size" preference actually needs.
    ///
    /// The cost is stated: between [#maximize] and the event, this still answers
    /// `false`. That is a window that has been asked and has not yet agreed, and
    /// there is no third answer that is true.
    public boolean isMaximized() {
        return maximized;
    }

    /// Asks the window manager to maximize the window.
    ///
    /// A request; see [#isMaximized] for what comes back and when.
    public void maximize() {
        if (window.isOpen()) {
            window.setMaximized(true);
        }
    }

    /// Asks for the window's ordinary size back — [#maximize]'s undo.
    ///
    /// Also un-minimizes, because that is what SDL's `RESTORED` means and there
    /// is no separate ask.
    public void restore() {
        if (window.isOpen()) {
            window.setMaximized(false);
        }
    }

    void handleMaximizedChanged(boolean value) {
        maximized = value;
    }

    void handleFocusChanged(boolean value) {
        focused = value;
        // The `Alt` that reached the compositor's window switcher comes back as a
        // release this window never saw the press of. Firing on it would open a
        // menu on the way *back* from another application (ADR-0223).
        modifierTaps.interrupted();
    }

    void handlePointerExited() {
        if (inputWatcher != null && inputWatcher.exited()) {
            return;
        }
        if (router != null) {
            router.pointerExited();
            repaintIfRestyled();
        }
    }

    /// SDL numbers buttons from 1, left first. Anything past the three the
    /// toolkit names is dropped rather than guessed at.
    private static io.github.digitalsmile.goldberry.input.event.PointerEvent.@Nullable Button toButton(int sdlButton) {
        return switch (sdlButton) {
            case 1 -> io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.PRIMARY;
            case 2 -> io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.MIDDLE;
            case 3 -> io.github.digitalsmile.goldberry.input.event.PointerEvent.Button.SECONDARY;
            default -> null;
        };
    }

    void handleKeyPressed(int keycode, int modifiers, boolean repeat) {
        // Before the watcher and before the router, and told the **raw** keycode:
        // what arms a tap is a modifier going down, and what disarms one is any
        // other key doing so — including the keys a popup swallows (ADR-0223).
        modifierTaps.keyPressed(keycode, repeat);
        if (inputWatcher != null
                && inputWatcher.keyPressed(
                        io.github.digitalsmile.goldberry.input.key.Key.fromSdl(keycode),
                        Modifiers.fromSdl(modifiers),
                        repeat)) {
            repaintIfRestyled();
            return;
        }
        if (router != null) {
            router.keyPressed(
                    io.github.digitalsmile.goldberry.input.key.Key.fromSdl(keycode),
                    io.github.digitalsmile.goldberry.input.key.Modifiers.fromSdl(modifiers),
                    repeat);
            repaintIfRestyled();
        }
    }

    void handleKeyReleased(int keycode, int modifiers) {
        // Fired before the release is dispatched, and the release is dispatched
        // either way: a widget that tracks a held modifier — a slider snapping
        // while `Shift` is down — has to see the key come up whether or not the
        // gesture was also a tap.
        modifierTaps.keyReleased(keycode);
        if (router != null) {
            router.keyReleased(
                    io.github.digitalsmile.goldberry.input.key.Key.fromSdl(keycode),
                    io.github.digitalsmile.goldberry.input.key.Modifiers.fromSdl(modifiers));
        }
    }

    void handleTextInput(String text) {
        if (router != null) {
            router.textInput(text);
        }
    }

    /// The composition an input method is assembling — `docs/gaps.md` G15.
    ///
    /// Routed to the focused node exactly as committed text is, and separately
    /// from it: a preedit is not an edit, and the widget that draws it is the one
    /// that has the caret (ADR-0289).
    void handlePreedit(String text, int start, int length) {
        if (router != null) {
            router.preedit(text, start, length);
        }
    }

    void handleCloseRequest() {
        if (closeHandler.getAsBoolean()) {
            close();
        }
    }

    private Window repaintAnd() {
        repaint();
        return this;
    }
}
