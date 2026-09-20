package io.github.digitalsmile.goldberry.widgets.core.web;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.web.BackendWebView;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.spinner.Spinner;
import io.github.digitalsmile.goldberry.widgets.controls.spinner.SpinnerSize;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Stack;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Canvas;
import io.github.digitalsmile.goldberry.widgets.overlay.message.Message;
import io.github.digitalsmile.goldberry.widgets.shell.web.WebPage;

/// A web page **inside** the window — `docs/core-widgets.md` §9's `web-view` as a
/// widget rather than a window.
///
/// The page keeps a platform window of its own, and that window is made a *child*
/// of the application's, positioned over this widget's box and moved with it. So
/// it takes part in the layout: put one in a `row`, give it `flex-grow`, and it
/// is sized like anything else.
///
/// ```java
/// new WebView(WebPage.of("https://example.org"))
/// ```
///
/// ## What it is not
///
/// **It is not a raster in the frame**, and that is the whole of what it cannot
/// do. `webview/webview` has no offscreen surface, so the page is a real window
/// sitting above this one's contents rather than a layer composited into it:
///
///   - **Nothing painted can cover it.** A `popover`, `tooltip` or `toast` that
///     overlaps the page is drawn underneath it and is invisible where the two
///     meet. A `dialog` is the exception, and only because this widget gets out
///     of its way — see below.
///   - **A `scroll` viewport does not clip it.** The child is clipped by the
///     *window*, not by an ancestor's box, so a page scrolled halfway out of a
///     viewport is still drawn whole. Put one in a pane that does not scroll.
///   - **`opacity`, `transform` and the frost material do not reach it**, because
///     those operate on a subtree's raster and there is no raster.
///   - **No golden image can contain it.** Every visual test of a `web-view` is a
///     test of what this widget paints when there is no page.
///
/// ## A modal makes the page stand aside
///
/// The one item above that could not be left as a caveat. A `dialog` is a
/// *demand* — it takes the keyboard and the pointer and waits for an answer —
/// and one painted behind a page is a window the user cannot use and cannot see
/// why. Nothing can be composited over the page, so the only thing that can move
/// is the page.
///
/// So while [Host#isModal] is true, this widget **parks** its page: the child
/// window is moved off the side of the application's window, where the parent
/// clips it away entirely, and moved back when the dialog closes. Moved rather
/// than resized or hidden, which keeps the document's layout and its scroll
/// position intact — see [WebViewState#place]
/// ([ADR-0444](../../../../../../../../book/src/adr/0444-a-page-stands-aside-for-a-modal.md)).
///
/// **The page visibly disappears for as long as the dialog is up.** That is the
/// intended behaviour rather than a compromise being hidden: a modal is supposed
/// to be the only thing on screen, and the alternative is a modal nobody can
/// see. An application that would rather keep the page showing should not use a
/// `dialog` over one.
///
/// It applies to modals only. A `tooltip` or a `toast` over a page is still
/// invisible where they overlap, and blanking a page to show four words in a
/// corner would be worse than the thing it fixed.
///
/// ## And it waits out of sight until it has loaded
///
/// The same mechanism, for the other reason nothing can be drawn over a page:
/// between the engine's window being mapped and the document painting, what is
/// on screen is WebKit's default white, for as long as the network takes — and a
/// `spinner` over it would be a spinner underneath it.
///
/// So the page is **opened parked** and stays there until it reports itself
/// loaded, and the widget draws a `spinner` in the box it will occupy
/// ([ADR-0445](../../../../../../../../book/src/adr/0445-a-page-is-not-shown-before-it-can-be-seen.md)).
/// It loads the whole time, so what appears is a finished page rather than one
/// that finishes in front of the user.
///
/// A page whose server accepts the connection and never answers therefore spins
/// for ever. A page that *fails* does not: WebKit substitutes its own error
/// document, which ends the load and is shown.
///
/// ## How fast a page animates is the engine's, and may be nobody's fault
///
/// Nothing in this widget paces the page. It is a separate platform window with
/// its own compositor and its own frame clock, and the toolkit's only obligation
/// is to drain the engine's event loop often enough — which it does, capped at
/// 8 ms whenever a page is open
/// ([io.github.digitalsmile.goldberry.render.event.EventLoop]).
///
/// So a page that animates badly is a page WebKit is drawing slowly, and the
/// usual reason on Linux is that **WebKitGTK has disabled accelerated
/// compositing** for the graphics driver it found. It does that silently: it
/// pins `hardware-acceleration-policy` to `NEVER`, and every layer is then
/// composited on the CPU. Measured here on an NVIDIA proprietary driver, where
/// it is disabled, a trivial `transform` animation still ran at a clean 60 fps
/// and a deliberately heavy one — 150 blurred, rotating boxes — fell to 9.
///
/// Two things follow. A page is **capped at the engine's rate, not the
/// window's**: a Goldberry window paced to a 100 Hz display sits beside a page
/// running at 60, and the difference is visible. And there is nothing to tune
/// here — the same measurement on a plain GTK application embedding the same
/// WebKit, with no Goldberry in the process at all, behaves identically.
///
/// What an application *can* do is make the page cheaper, and the usual web
/// advice is the wrong advice here: `will-change: transform` is a hint to a
/// compositor that has been switched off, and it measured as no change at all,
/// as did removing a `blur()` filter. What moved the number was the **amount of
/// moving content** — the same page with 40 animated boxes instead of 150 went
/// from 13 fps to 24. Software rasterisation costs pixels, so fewer animated
/// pixels is the lever.
///
/// ## Wayland cannot do this, and says so
///
/// Embedding means reparenting the engine's window into the application's. X11,
/// Win32 and Cocoa allow that; **Wayland does not, and no protocol proposes it**
/// — a surface belongs to the client that made it, `xdg-foreign` is toplevel
/// *parenting* and errors on anything else, and the request has been open since
/// 2012. On a Wayland session this widget therefore opens **nothing** and paints
/// a message saying why, rather than dropping a loose window on the desktop that
/// the layout cannot move
/// ([ADR-0442](../../../../../../../../book/src/adr/0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)).
///
/// An application on a Wayland desktop that wants a page *can* have one by asking
/// SDL for the x11 driver — `-Dgoldberry.backend.videoDriver=x11` — which runs
/// the window under XWayland where reparenting works.
///
/// ## Input needs no routing
///
/// The page is a real child window, so the window system delivers its clicks and
/// keystrokes to WebKit directly. Nothing here forwards events, and the pointer
/// router never sees them — which is correct: they were never this toolkit's.
///
/// @param page       what to open
/// @param attributes id, classes and styles, as for any widget
public record WebView(WebPage page, Attributes attributes) implements Widget.Stateful, Attributed<WebView> {

    public WebView {
        Objects.requireNonNull(page, "page");
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A page with no attributes of its own.
    public WebView(WebPage page) {
        this(page, Attributes.NONE);
    }

    @Override
    public WebView withAttributes(Attributes value) {
        return new WebView(page, value);
    }

    @Override
    public State<?> createState() {
        return new WebViewState();
    }

    /// The page, and the box it is kept over.
    static final class WebViewState extends State<WebView> {

        /// Says what happened and why, because almost every way this widget can
        /// fail is invisible on screen: a page that did not open leaves a box that
        /// looks like a box, and the reasons — a session that cannot embed, an id
        /// that was never set, a build with no web view library — are all
        /// indistinguishable from "nothing happened yet" without a log line.
        private static final Logger LOG = LoggerFactory.getLogger(WebView.class);

        /// What a rendered image of this widget says, because it can never contain
        /// a page: the engine draws into a window of its own, and an `Offscreen`
        /// render has no window at all.
        private static final String OFFSCREEN =
                "A web page is drawn by the engine into a window of its own, so it cannot appear in a rendered"
                        + " image. Run the application to see one.";

        /// The open page, or null — which is the Wayland case and the
        /// no-web-view-support case, and they are told apart by [#reason].
        private @Nullable BackendWebView page;

        /// Why there is no page, or null when there is one.
        private @Nullable String reason;

        private @Nullable Host host;

        /// The rectangle the page was last put at, so an unchanged layout costs
        /// no platform call at all.
        private @Nullable LogicalRect placed;

        /// Whether opening has been attempted. One attempt per mount: a session
        /// that cannot embed cannot start being able to.
        private boolean attempted;

        /// Whether the page is currently parked off the side of the window. See
        /// [#place]; [#sync] has the two reasons.
        private boolean parked;

        /// Whether a spinner is being drawn in the box, because the page has
        /// nothing to show yet. Read in [#build], so it changes through
        /// `setState` rather than being written from the painter.
        private boolean loading;

        /// What [#loading] has been *asked* to become.
        ///
        /// Two fields for one flag, because the `setState` is deferred: it is
        /// requested from inside a paint and runs on the next turn of the loop,
        /// and every frame in between would otherwise see the old value of
        /// [#loading] and queue the same change again. Asking once is what this
        /// remembers.
        private boolean loadingWanted;

        /// Whether the page has ever had anything to show.
        ///
        /// **The first load is the only one that hides the page.** A page that
        /// vanished and came back on every navigation would be unusable — follow
        /// a link and the tab blanks — and it is not what a browser does either:
        /// the old document stays up until the new one has something to paint.
        /// The white this whole mechanism is about happens exactly once, between
        /// the window being mapped and the first document existing.
        private boolean shown;

        /// The page value this widget last told the engine to show.
        ///
        /// Compared with [WebPage#showsSameAs] rather than with `equals`, and
        /// that is not a shortcut: a page carries its callbacks, a callback is a
        /// lambda, and two rebuilds of the same `on(...)` expression are two
        /// different objects — so `equals` would say the page changed on every
        /// frame and the widget would reload it for ever.
        private @Nullable WebPage showing;

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            // The painter is the per-frame hook. `build` runs on a rebuild and a
            // layout can change without one -- a window resize moves this box and
            // rebuilds nothing -- so the box is re-read where a frame is drawn.
            // The frame carries the scale, which is what turns this widget's
            // logical box into the parent window's own pixels.
            // The id goes on the CANVAS, not on the column around it, and that is
            // not cosmetic: `Host.anchor` answers from the hit-test snapshot, and
            // only a box that is hit-testable is in it. A plain `column` is not,
            // so anchoring to one finds nothing and the page never opens — which
            // is exactly what the first attempt did.
            var id = widget().attributes().id();
            var surface = new Canvas((frame, size) -> sync(frame.scale().factor()));
            var placed = id == null
                    ? surface
                    : surface.withAttributes(Attributes.NONE.id(id).classes("web-surface"));
            // The surface goes in a `stack` so that something can be drawn over
            // it while there is no page to hide it — which is the only moment
            // anything CAN be drawn there ([ADR-0445]). A stack's first child
            // stays in flow and sizes it, so the surface is still the box the
            // page is kept over and still the one carrying the id; the spinner
            // is absolute and covers nothing.
            //
            // Always a stack, loading or not. Swapping the surface between a
            // bare canvas and a wrapped one would replace the element that
            // `Host.anchor` finds, on the frame the page is about to be placed
            // over it.
            var stage = stage(placed, loading);
            // With no host there is no window to put a page in — an offscreen
            // render, which is every golden image. Say so rather than painting an
            // empty box: a picture of this widget can never contain a page, and a
            // blank rectangle would look like a defect instead of a fact.
            var why = host == null ? OFFSCREEN : reason;
            var children = why == null ? List.<Widget>of(stage) : List.<Widget>of(stage, notice(why));
            return new Column(children, widget().attributes().classes("web-view"));
        }

        /// The surface, and the spinner over it while the page has nothing to
        /// show.
        ///
        /// Separated from [#build] so that the one thing worth asserting about
        /// it can be: **loading puts a spinner in the tree and not loading takes
        /// it out**. The flag reaching this method at the right moment is the
        /// other half and is a fact about frames rather than about widgets — see
        /// [#loadingIs].
        static Stack stage(Widget surface, boolean loading) {
            return new Stack(
                    loading ? List.of(surface, spinner()) : List.<Widget>of(surface),
                    Attributes.NONE.classes("web-stage"));
        }

        /// The activity indicator shown while the page has nothing to show.
        ///
        /// It is only ever visible because the page is **parked** at the same
        /// time: a page is a platform window above the frame, so a spinner drawn
        /// over one would be drawn underneath it. The two halves are one
        /// decision ([ADR-0445]).
        private static Widget spinner() {
            // LARGE, because it is the only thing in the box: a page's whole
            // region is not ready, and a 16px ring in the middle of a tab reads
            // as a decoration on something rather than as the subject
            // ([ADR-0447]).
            return new Spinner(SpinnerSize.LARGE).withAttributes(Attributes.NONE.classes("web-loading"));
        }

        /// What is painted when there is no page. The whole of what a golden image
        /// of this widget can ever show.
        private Widget notice(String why) {
            return new Message(Message.Kind.WARNING, why).withAttributes(Attributes.NONE.classes("web-view-notice"));
        }

        /// Opens the page on the first frame that has a box, and keeps it over
        /// that box afterwards.
        ///
        /// Driven from the painter because that is what runs every frame. The
        /// rectangle comes from [Host#anchor], which reports what was painted
        /// **last** frame — so a resize moves the page one frame late, which is
        /// the same lag a `tour` places its card with and is invisible at 60 Hz.
        private void sync(float scale) {
            if (host == null) {
                return;
            }
            var id = widget().attributes().id();
            if (id == null) {
                // Without an id there is nothing to ask `anchor` about. Said once,
                // as a notice, because it is a mistake rather than a platform
                // limit.
                if (!attempted) {
                    attempted = true;
                    LOG.warn("a web-view has no id, so there is no box to find: give it"
                            + " Attributes.NONE.id(\"...\") and the page will open over it");
                    show("a web-view needs an id, so that the toolkit can find the box to put the page over");
                }
                return;
            }
            var region = host.anchor(id);
            if (region.isEmpty()) {
                // Ordinary on the first frame: `anchor` answers from the LAST
                // frame's hit-test snapshot, so there is nothing to find until
                // this widget has been painted once.
                LOG.debug("web-view \"{}\" has not been painted yet, so there is no box to put a page over", id);
                return;
            }
            var r = region.get();
            var bounds = LogicalRect.of(r.left(), r.top(), r.width(), r.height());
            if (bounds.size().width() <= 0 || bounds.size().height() <= 0) {
                return;
            }
            if (page == null) {
                if (attempted) {
                    return;
                }
                attempted = true;
                LOG.debug("web-view \"{}\" opening a page over {} at scale {}", id, bounds, scale);
                open(bounds, scale);
                return;
            }
            // The page gives way to a modal ([ADR-0444]). A `dialog` is painted
            // into the frame and the page is a platform window above it, so a
            // dialog over a page is drawn where nobody can see it — and its
            // buttons cannot be pressed, because the press lands on WebKit. The
            // only thing that can move is the page.
            //
            // And a page with nothing to show ([ADR-0445]): between the window
            // being mapped and the document painting, WebKit draws its default
            // white, for as long as the network takes. Nothing can be painted
            // over it to say so, so the page waits out of sight and the spinner
            // goes in the box it will occupy.
            // The application asking for somewhere else. A `web-view` takes its
            // page from a value, so navigating is the ordinary thing a widget
            // does with a changed input: hold the url in state, rebuild, and the
            // page follows ([ADR-0449]).
            navigateIfAsked(page);

            // Asked until it answers yes, and never again: the first load is
            // the only one that hides the page. See [#shown].
            var ready = shown || page.loadState().isReady();
            shown |= ready;
            loadingIs(!ready);
            if (!ready) {
                // The one thing in this widget that is genuinely polled: the
                // page's own progress changes with no tree change to notice it,
                // so **nothing else will ask for the frame that looks again**.
                // Without this the loop goes idle with the page still parked,
                // and it comes back only when something unrelated causes a
                // repaint — a moved pointer, which is how this was found.
                //
                // Not left to the spinner's own animation, although a `spinner`
                // does ask for frames for ever: that would make this widget's
                // correctness depend on which indicator it happens to draw, and
                // the first frame is a deadlock either way — no frame, no
                // `setState`, no spinner, no frames.
                //
                // It stops as soon as the page is ready, which is what keeps an
                // idle application idle.
                host.repaint();
            }
            var away = !ready || host.isModal();
            if (!bounds.equals(placed) || away != parked) {
                LOG.trace("web-view \"{}\" {} {}", id, away ? "held off" : "placed over", bounds);
                placed = bounds;
                parked = away;
                place(page, bounds, scale, away);
            }
        }

        /// Follows the widget's page value when the application changes it.
        ///
        /// **The first load is not this**: `open` already loaded whatever the
        /// page named, and [#showing] is set there. This is for the rebuild
        /// after — a url typed into a field, a document swapped for another.
        ///
        /// The spinner comes back, and that is the difference between this and
        /// the page navigating **itself**. A link the user followed, or a
        /// redirect the site issued, must leave the old document up until the
        /// new one commits, which is what a browser does and why [#shown]
        /// latches on the first load. An application saying "show this instead"
        /// is a different event: what is on screen is no longer what was asked
        /// for, and several seconds of stale content with no sign of life is
        /// worse than a spinner.
        private void navigateIfAsked(BackendWebView page) {
            var wanted = widget().page();
            if (wanted.showsSameAs(showing)) {
                return;
            }
            showing = wanted;
            shown = false;
            if (wanted.url() != null) {
                LOG.debug(
                        "web-view \"{}\" navigating to {}",
                        widget().attributes().id(),
                        wanted.url());
                page.navigate(wanted.url());
            } else if (wanted.html() != null) {
                LOG.debug(
                        "web-view \"{}\" showing a document of {} characters",
                        widget().attributes().id(),
                        wanted.html().length());
                page.html(wanted.html());
            }
        }

        /// Records whether the page has anything to show, rebuilding when the
        /// answer changes.
        ///
        /// Deferred through [#show]'s mechanism and for its reason: this runs
        /// from inside a **paint**, and rebuilding the tree being painted is not
        /// a thing to do. Guarded on the change, so a loaded page costs one
        /// boolean comparison per frame rather than a `setState`.
        private void loadingIs(boolean value) {
            if (loadingWanted == value) {
                return;
            }
            loadingWanted = value;
            // Logged because it is otherwise unobservable: no golden image can
            // contain a page, so whether the spinner was ever actually built is
            // a question only a running application can answer -- and the first
            // version of this could not answer it, because it set the field
            // without the `setState` and rebuilt nothing.
            LOG.debug(
                    "web-view \"{}\": {} the loading spinner",
                    widget().attributes().id(),
                    value ? "raising" : "taking down");
            host.after(java.time.Duration.ZERO, () -> setState(() -> loading = value));
        }

        /// Puts the page where it belongs this frame: over its box, or out of
        /// sight because a modal is up.
        ///
        /// **Moved rather than resized**, and that is the whole of the technique.
        /// `goldberry_webview_set_bounds` calls `gtk_window_resize` as well as
        /// `XMoveResizeWindow`, deliberately, so that the WebKit widget inside
        /// lays out to the new size — which means parking the page at 1x1 would
        /// reflow the document to one CSS pixel of width and reflow it back from
        /// there, losing the scroll position and anything else that depended on
        /// the layout. Keeping the size and changing only the origin costs no
        /// reflow at all.
        ///
        /// Out of sight is `-(size + 64)`: the page is a **child** of the
        /// application's window, so an origin left of and above the parent's own
        /// clips it away entirely. Derived from the size rather than a constant
        /// like `-32768` because an X11 window position is an `INT16`, and a
        /// number chosen to be safely past the edge should not be one the
        /// protocol has to be trusted not to wrap.
        private void place(BackendWebView page, LogicalRect bounds, float scale, boolean parked) {
            var box = pageBox(bounds, scale, parked);
            page.bounds(box.x(), box.y(), box.width(), box.height());
        }

        /// Where the page's window goes, in the parent window's own pixels.
        ///
        /// A record and a pure function rather than four arguments computed in
        /// place, so that the one thing worth checking here can be checked: that
        /// parking changes the **origin and not the size**, at every scale.
        record PageBox(int x, int y, int width, int height) {}

        /// The arithmetic behind [#place], separated from the call that performs
        /// it. See that method for why parking moves rather than resizes.
        static PageBox pageBox(LogicalRect bounds, float scale, boolean parked) {
            var box = parked ? parkedAway(bounds) : bounds;
            return new PageBox(
                    Math.round(box.origin().x() * scale),
                    Math.round(box.origin().y() * scale),
                    Math.max(1, Math.round(box.size().width() * scale)),
                    Math.max(1, Math.round(box.size().height() * scale)));
        }

        /// The same box, put where the parent window clips every pixel of it.
        ///
        /// Past the parent's top-left corner by the page's own size, because the
        /// page is a **child** of that window. Derived from the size rather than
        /// a fixed `-32768` because an X11 window position is an `INT16`, and a
        /// number chosen to be safely off-screen should not be one the protocol
        /// has to be trusted not to wrap.
        ///
        /// In **logical** units, the same as what comes in, so that one notion
        /// of "out of sight" serves both callers: [#place], which works in the
        /// parent's pixels, and [#open], which hands a rectangle to
        /// [Host#embeddedWebView] and lets the launcher scale it.
        static LogicalRect parkedAway(LogicalRect bounds) {
            var width = bounds.size().width();
            var height = bounds.size().height();
            return LogicalRect.of(-(width + PARKING_MARGIN), -(height + PARKING_MARGIN), width, height);
        }

        /// How far past its own size a parked page is pushed. Anything positive
        /// clips it; this is a round number with room for a window manager that
        /// adds a border nobody asked for.
        private static final int PARKING_MARGIN = 64;

        private void open(LogicalRect bounds, float scale) {
            // Opened **already out of sight**, which is the difference between a
            // spinner and a spinner that arrives one frame after a white flash:
            // the shim maps the page's window and reparents it inside this call,
            // so a page created over its box is visible — and empty — before
            // anything here could move it ([ADR-0445]).
            var opened = host.embeddedWebView(widget().page().spec(), parkedAway(bounds));
            if (opened.isPresent()) {
                page = opened.get();
                placed = bounds;
                parked = true;
                // What was opened is what is showing, so the rebuild after this
                // does not navigate straight back to it.
                showing = widget().page();
                LOG.info("web-view: a page is open inside the window over {} (scale {})", bounds, scale);
                show(null);
                // `loading` is deliberately NOT set here. It is read in `build`,
                // so it may only change through `setState` — writing it straight
                // would set the flag and rebuild nothing, and [#loadingIs] would
                // then find it already true and skip the `setState` that was the
                // only thing left to do. The spinner would never be built at all.
                //
                // And the frame that does it has to be asked for. Nothing else
                // will: the tree has not changed, the page is parked and
                // invisible, and the loop is idle by design (§1.7).
                host.repaint();
                return;
            }
            // The three reasons are told apart further down — `Webview` logs which
            // one — but the message on screen is the same, because the fix is the
            // same and a user cannot act on the difference.
            LOG.warn("web-view: no page could be put inside the window."
                    + " Embedding needs a native window handle to reparent into, which X11, Win32 and Cocoa"
                    + " give and Wayland does not. Run on X11 or XWayland"
                    + " (-Dgoldberry.backend.videoDriver=x11), or check that Capability.WEB_VIEW is present");
            show("This session cannot put a web page inside a window. Wayland has no cross-client surface"
                    + " embedding and no protocol proposes one, so the page is not opened at all rather than"
                    + " dropped on the desktop where the layout cannot reach it. Running under X11 or XWayland"
                    + " — -Dgoldberry.backend.videoDriver=x11 — is what makes it work.");
        }

        /// Sets what the widget says and asks for a **rebuild**, not a repaint.
        ///
        /// The difference is the whole of why this screen was blank: `reason` is
        /// read in `build`, and `Host.repaint` redraws the tree that already
        /// exists without building a new one — so the notice was set and never
        /// appeared. `setState` is what re-runs `build`.
        ///
        /// Deferred by a zero delay because this runs from inside a **paint**, and
        /// rebuilding the tree that is being painted is not a thing to do; the
        /// next turn of the loop is.
        private void show(@Nullable String why) {
            if (java.util.Objects.equals(reason, why)) {
                return;
            }
            host.after(java.time.Duration.ZERO, () -> setState(() -> reason = why));
        }

        /// Closes the page when the widget goes.
        ///
        /// Not optional: the page's window is the platform's, so a widget that
        /// vanished without closing it would leave a browser standing over
        /// nothing.
        @Override
        protected void dispose() {
            if (page != null) {
                page.close();
                page = null;
            }
        }
    }
}
