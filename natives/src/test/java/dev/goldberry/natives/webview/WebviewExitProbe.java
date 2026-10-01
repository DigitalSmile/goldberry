package dev.goldberry.natives.webview;

import java.time.Duration;
import java.time.Instant;

/// A process that opens a page, lets it load, closes it and returns from `main`
/// — the whole of what [WebviewExitTest] runs in a child JVM.
///
/// It returns rather than calling `System.exit`, because the defect was in how
/// the stock launcher ends a process: `main` runs on a thread of its own and the
/// C exit handlers run on the first one, which is not WebKit's (ADR-0507).
///
/// Exit codes: 0 for a page opened, loaded and closed; 3 for no page on this
/// machine, which the test reads as a reason to skip rather than a failure.
public final class WebviewExitProbe {

    static final int NO_PAGE = 3;

    private WebviewExitProbe() {}

    public static void main(String[] args) {
        var opened = Webview.open(false);
        if (opened.isEmpty()) {
            System.exit(NO_PAGE);
            return;
        }
        var page = opened.get();
        page.html("<!doctype html><title>exit</title><p>A page that is about to be closed.");
        var deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (page.loadState() != LoadState.FINISHED && Instant.now().isBefore(deadline)) {
            Webview.pump();
            Thread.onSpinWait();
        }
        page.close();
    }
}
