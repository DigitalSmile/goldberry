package dev.goldberry.natives.webview;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/// A page whose own script reads what the application's init script defined:
/// what [WebviewInitTest] runs in a child JVM, for [WebviewSignInProbe]'s
/// reasons.
///
/// The init script defines `window.Host`; the document's inline script, which
/// runs as the document is parsed, reports through a binding what it found.
/// Then an `eval` from the application calls the same binding. One fact per
/// line on standard output. Exit codes: 0 done, 3 no page, 4 timed out.
public final class WebviewInitProbe {

    static final int NO_PAGE = 3;
    static final int TIMED_OUT = 4;

    private WebviewInitProbe() {}

    public static void main(String[] args) throws Exception {
        var opened = Webview.open(false);
        if (opened.isEmpty()) {
            System.exit(NO_PAGE);
            return;
        }
        try (var page = opened.get()) {
            var heard = new StringBuilder();
            page.bind("report", arguments -> {
                synchronized (heard) {
                    heard.append(arguments).append('\n');
                }
                System.out.println("report " + arguments);
                return "";
            });
            page.init("window.Host = { version: 'gb-9' };");
            page.html("<!doctype html><script>report(typeof window.Host === 'object' ? Host.version : 'missing')"
                    + "</script>");
            if (!pumpUntil(() -> contains(heard, "gb-9") || contains(heard, "missing"))) {
                System.exit(TIMED_OUT);
            }
            page.eval("report('from eval')");
            if (!pumpUntil(() -> contains(heard, "from eval"))) {
                System.exit(TIMED_OUT);
            }
            System.out.println("done");
        }
    }

    private static boolean contains(StringBuilder heard, String text) {
        synchronized (heard) {
            return heard.indexOf(text) >= 0;
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
