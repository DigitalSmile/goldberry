package io.github.digitalsmile.goldberry.widgets.core.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.web.WebLoad;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.spinner.Spinner;
import io.github.digitalsmile.goldberry.widgets.core.canvas.Canvas;
import io.github.digitalsmile.goldberry.widgets.core.web.WebView.WebViewState;

/// Where a page goes when it must not be seen — [ADR-0444] and [ADR-0445].
///
/// A page is a platform window above the frame, so nothing can be composited
/// over it: not a `dialog`, and not a "still loading" indicator either. The only
/// thing that can move is the page, and this is the arithmetic that moves it.
///
/// Against [WebViewState#pageBox] and [WebViewState#parkedAway] rather than
/// against a mounted widget on purpose. What can be wrong here is the geometry:
/// a park that resizes instead of moving reflows the document, and a park that
/// is not actually outside the parent's box leaves the page on screen. Both are
/// arithmetic. That the widget *calls* it on the right frame is a platform fact
/// with no offscreen answer — no golden image can contain a page at all — and it
/// is checked by running the showcase's web tab.
@DisplayName("a page held out of sight")
class WebViewParkingTest {

    private static final LogicalRect BOX = LogicalRect.of(24, 80, 640, 400);

    @Nested
    @DisplayName("while nothing is modal")
    class Placed {

        @Test
        @DisplayName("sits over its box, in the parent window's own pixels")
        void sitsOverItsBox() {
            assertEquals(new WebViewState.PageBox(24, 80, 640, 400), WebViewState.pageBox(BOX, 1f, false));
        }

        @Test
        @DisplayName("scaled by the display, because the platform wants device pixels")
        void scales() {
            assertEquals(new WebViewState.PageBox(48, 160, 1280, 800), WebViewState.pageBox(BOX, 2f, false));
        }

        /// A box that rounds to zero would be a page the platform refuses to
        /// size, so the floor is one pixel rather than none.
        @Test
        @DisplayName("and never smaller than one pixel either way")
        void neverDegenerate() {
            var box = WebViewState.pageBox(LogicalRect.of(0, 0, 0.1f, 0.1f), 1f, false);

            assertTrue(box.width() >= 1 && box.height() >= 1, box.toString());
        }
    }

    @Nested
    @DisplayName("while a modal is up")
    class Parked {

        /// The whole of the technique. `goldberry_webview_set_bounds` calls
        /// `gtk_window_resize` as well as `XMoveResizeWindow`, deliberately, so
        /// that the WebKit widget inside lays out to the new size — which means
        /// a page parked at 1x1 reflows the document to one CSS pixel of width
        /// and reflows it back from there, losing the scroll position. Keeping
        /// the size is what makes parking free.
        @Test
        @DisplayName("keeps exactly the size it had, so the document does not reflow")
        void keepsItsSize() {
            var placed = WebViewState.pageBox(BOX, 1f, false);
            var parked = WebViewState.pageBox(BOX, 1f, true);

            assertEquals(placed.width(), parked.width(), "parking must not resize the page");
            assertEquals(placed.height(), parked.height(), "parking must not resize the page");
        }

        /// The page is a **child** of the application's window, so an origin
        /// left of and above the parent's own clips every pixel of it. Past the
        /// corner by its own size, not merely to a negative number: a page at
        /// `(-1, -1)` is very nearly all still on screen.
        @Test
        @DisplayName("moves fully outside the parent's box, by more than its own size")
        void movesFullyOutside() {
            var parked = WebViewState.pageBox(BOX, 1f, true);

            assertTrue(parked.x() + parked.width() < 0, "the right edge is still on screen: " + parked);
            assertTrue(parked.y() + parked.height() < 0, "the bottom edge is still on screen: " + parked);
        }

        /// Derived from the size rather than a fixed `-32768`, because an X11
        /// window position is an `INT16` and a constant chosen to be safely
        /// off-screen should not be one the protocol has to be trusted not to
        /// wrap.
        @Test
        @DisplayName("to a coordinate an X11 INT16 can carry, at any scale")
        void staysWithinAnInt16() {
            for (var scale : new float[] {1f, 1.25f, 1.5f, 2f, 3f}) {
                var parked = WebViewState.pageBox(LogicalRect.of(0, 0, 3840, 2160), scale, true);

                assertTrue(
                        parked.x() >= Short.MIN_VALUE && parked.y() >= Short.MIN_VALUE,
                        "at scale " + scale + ": " + parked);
            }
        }

        @Test
        @DisplayName("and comes back to exactly where it was")
        void comesBack() {
            var before = WebViewState.pageBox(BOX, 1.5f, false);
            WebViewState.pageBox(BOX, 1.5f, true);
            var after = WebViewState.pageBox(BOX, 1.5f, false);

            assertEquals(before, after, "restoring is the same call as placing; parking keeps no state");
        }
    }

    @Nested
    @DisplayName("is held off until it has something to show")
    class UntilLoaded {

        /// The state every caller actually asks about, decided in one place so
        /// that "the engine would not say" does not have to be remembered at
        /// each call site.
        @Test
        @DisplayName("and a page that has not finished loading is not ready")
        void loadingIsNotReady() {
            assertFalse(WebLoad.LOADING.isReady());
            assertFalse(WebLoad.IDLE.isReady(), "created and never navigated is as unready as still fetching");
            assertTrue(WebLoad.FINISHED.isReady());
        }

        /// A build that cannot answer must behave as every build did before
        /// there was a question to ask. The alternative is a page that never
        /// appears at all on a platform where the shim has no implementation,
        /// which is a far worse failure than a white flash.
        @Test
        @DisplayName("but an engine that will not say counts as ready, not as loading for ever")
        void unknownShowsThePage() {
            assertTrue(WebLoad.UNKNOWN.isReady());
        }

        /// The page is opened *already* out of sight, because the shim maps and
        /// reparents the window inside the call that creates it — so a page
        /// created over its box is visible, and empty, before anything in the
        /// widget could move it.
        @Test
        @DisplayName("so the rectangle it is opened at is the parked one, at the box's size")
        void opensParked() {
            var away = WebViewState.parkedAway(BOX);

            assertEquals(BOX.size(), away.size(), "opening parked must not change the size it opens at");
            assertTrue(away.origin().x() + away.size().width() < 0, away.toString());
            assertTrue(away.origin().y() + away.size().height() < 0, away.toString());
        }

        /// One notion of "out of sight", used by the call that opens the page
        /// (logical units, scaled by the launcher) and the call that moves it
        /// (the parent's own pixels). They would drift if there were two.
        @Test
        @DisplayName("and it is the same place a modal parks it")
        void oneParkingSpace() {
            assertEquals(
                    WebViewState.pageBox(WebViewState.parkedAway(BOX), 1f, false), WebViewState.pageBox(BOX, 1f, true));
        }
    }

    /// The spinner the page is held off *for* — [ADR-0445].
    ///
    /// The half of that decision that is a widget rather than a platform fact,
    /// and therefore the half a test can hold. The other half is that the flag
    /// below reaches [WebViewState#build] at the right moment, which is a fact
    /// about frames: it is read in `build`, so it may only change through
    /// `setState`, and the first version of this wrote it straight from the
    /// painter. That set the flag, rebuilt nothing, and then skipped the
    /// `setState` that was the only thing left to do — so the spinner was never
    /// built at all, and with nothing animating, the loop went idle with the
    /// page still parked. It came back when the pointer moved.
    @Nested
    @DisplayName("shows a spinner in the box it will occupy")
    class Indicator {

        private static final Widget SURFACE = new Canvas((frame, size) -> {});

        @Test
        @DisplayName("while the page has nothing to show")
        void spinnerWhileLoading() {
            var stage = WebViewState.stage(SURFACE, true);

            assertTrue(
                    stage.children().stream().anyMatch(Spinner.class::isInstance),
                    "loading and no spinner in the tree: " + stage.children());
        }

        @Test
        @DisplayName("and takes it away once there is one")
        void noSpinnerOnceLoaded() {
            var stage = WebViewState.stage(SURFACE, false);

            assertTrue(
                    stage.children().stream().noneMatch(Spinner.class::isInstance),
                    "the page is up and the spinner is still there: " + stage.children());
        }

        /// The surface stays **first** and stays in flow, which is what a
        /// `stack` promises: it is the box the page is kept over and the one
        /// carrying the id `Host.anchor` resolves. A spinner that pushed it out
        /// of first place would move the page.
        @Test
        @DisplayName("without displacing the surface the page is kept over")
        void surfaceStaysFirst() {
            assertEquals(SURFACE, WebViewState.stage(SURFACE, true).children().getFirst());
            assertEquals(SURFACE, WebViewState.stage(SURFACE, false).children().getFirst());
        }
    }
}
