package io.github.digitalsmile.goldberry.widgets;

import java.util.List;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessPopup;

/// The two moves a test makes when it drives the **real** frame loop.
///
/// ## What these tests are
///
/// A handful of tests here do not build an [io.github.digitalsmile.goldberry.widget.ElementTree]
/// and poke it: they start the launcher against a [HeadlessBackend], post backend
/// events at it, and assert on the windows that came out. That is the shipping
/// path — a real router hit-testing a frame a real popup painted — and it is the
/// only way to test the half of the toolkit that is a *window* rather than a
/// tree.
///
/// Five of them wrote [#later] out for themselves and four wrote [#popups], all
/// identical, because both are consequences of that setup rather than of what any
/// one of them is testing.
///
/// Their `TestApp`s are **not** here, and deliberately: they differ in root
/// widget, in window size and in stylesheets, which is each test's own business.
public final class TestLoop {

    private TestLoop() {}

    /// Runs `action` on the UI thread after `millis`.
    ///
    /// The wait is the point, and it is why this cannot be a virtual clock: what
    /// these tests are waiting for is a **frame**, and the frame is produced by a
    /// loop on another thread that a test cannot step. Long enough for the popup
    /// to have painted is long enough for its router to have something to
    /// hit-test.
    ///
    /// @param millis how long to wait before running `action`
    /// @param action what to run, on the UI thread
    public static void later(long millis, Runnable action) {
        Goldberry.async(() -> {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                })
                .thenRun(action);
    }

    /// Every popup window `backend` is currently showing, in the order it opened
    /// them.
    ///
    /// A popup is a window like any other to the backend, so "is the menu open"
    /// and "did closing one close the stack" are both questions about which of
    /// its windows are popups.
    ///
    /// @param backend the backend the launcher was installed against
    public static List<HeadlessPopup> popups(HeadlessBackend backend) {
        return backend.windows().stream()
                .filter(HeadlessPopup.class::isInstance)
                .map(HeadlessPopup.class::cast)
                .toList();
    }
}
