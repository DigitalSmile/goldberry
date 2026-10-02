package dev.goldberry.natives.webview;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/// A sign-in, the whole of what [WebviewSignInTest] runs in a child JVM: open a
/// page at a server's login URL, let the server set an HttpOnly cookie and
/// redirect to a custom scheme, catch that redirect, read the cookie, and then
/// refuse one navigation outright.
///
/// A child JVM for [WebviewExitTest]'s reason — WebKitGTK takes the thread
/// that first starts GTK as its main thread for the life of the process — and
/// because the frame loop is not here: this pumps the engine itself.
///
/// What it saw goes to standard output, one fact per line, for the test to
/// read. Exit codes: 0 done, 3 no page on this machine, 4 timed out.
public final class WebviewSignInProbe {

    static final int NO_PAGE = 3;
    static final int TIMED_OUT = 4;

    private WebviewSignInProbe() {}

    public static void main(String[] args) throws Exception {
        var base = args[0];
        var opened = Webview.open(false);
        if (opened.isEmpty()) {
            System.exit(NO_PAGE);
            return;
        }
        try (var page = opened.get()) {
            var callback = new String[1];
            var hooked = page.onNavigate(uri -> {
                System.out.println("navigate " + uri);
                if (uri.startsWith("myapp:")) {
                    callback[0] = uri;
                    return false;
                }
                return !uri.endsWith("/blocked");
            });
            System.out.println("hooked " + hooked);

            page.navigate(base + "/login");
            if (!pumpUntil(() -> callback[0] != null)) {
                System.exit(TIMED_OUT);
            }
            System.out.println("callback " + callback[0]);

            var cookies = page.cookies(base + "/");
            if (!pumpUntil(cookies::isDone)) {
                System.exit(TIMED_OUT);
            }
            for (var cookie : cookies.get(1, TimeUnit.SECONDS)) {
                System.out.println("cookie " + cookie.getName() + "=" + cookie.getValue() + " httponly="
                        + cookie.isHttpOnly() + " path=" + cookie.getPath());
            }

            // Refused outright: the server counts requests, and this one must
            // never reach it.
            page.navigate(base + "/blocked");
            var settle = Instant.now().plus(Duration.ofSeconds(2));
            pumpUntil(() -> Instant.now().isAfter(settle));
            System.out.println("done");
        }
    }

    /// Drives the engine until `condition` holds, for at most twenty seconds.
    private static boolean pumpUntil(BooleanSupplier condition) throws InterruptedException {
        var deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                return false;
            }
            Webview.pump();
            Thread.sleep(5);
        }
        return true;
    }
}
