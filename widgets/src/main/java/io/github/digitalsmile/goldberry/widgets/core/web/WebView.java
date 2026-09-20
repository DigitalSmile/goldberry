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
import io.github.digitalsmile.goldberry.widgets.core.Column;
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
///   - **Nothing painted can cover it.** A `dialog`, `popover`, `tooltip` or
///     `toast` that overlaps the page is drawn underneath it and is invisible
///     where the two meet. An application that opens a modal over a page hides
///     the page first.
///   - **A `scroll` viewport does not clip it.** The child is clipped by the
///     *window*, not by an ancestor's box, so a page scrolled halfway out of a
///     viewport is still drawn whole. Put one in a pane that does not scroll.
///   - **`opacity`, `transform` and the frost material do not reach it**, because
///     those operate on a subtree's raster and there is no raster.
///   - **No golden image can contain it.** Every visual test of a `web-view` is a
///     test of what this widget paints when there is no page.
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
            // With no host there is no window to put a page in — an offscreen
            // render, which is every golden image. Say so rather than painting an
            // empty box: a picture of this widget can never contain a page, and a
            // blank rectangle would look like a defect instead of a fact.
            var why = host == null ? OFFSCREEN : reason;
            var children = why == null ? List.<Widget>of(placed) : List.<Widget>of(placed, notice(why));
            return new Column(children, widget().attributes().classes("web-view"));
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
            if (!bounds.equals(placed)) {
                LOG.trace("web-view \"{}\" moved to {}", id, bounds);
                placed = bounds;
                page.bounds(
                        Math.round(bounds.origin().x() * scale),
                        Math.round(bounds.origin().y() * scale),
                        Math.max(1, Math.round(bounds.size().width() * scale)),
                        Math.max(1, Math.round(bounds.size().height() * scale)));
            }
        }

        private void open(LogicalRect bounds, float scale) {
            var opened = host.embeddedWebView(widget().page().spec(), bounds);
            if (opened.isPresent()) {
                page = opened.get();
                placed = bounds;
                LOG.info("web-view: a page is open inside the window over {} (scale {})", bounds, scale);
                show(null);
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
