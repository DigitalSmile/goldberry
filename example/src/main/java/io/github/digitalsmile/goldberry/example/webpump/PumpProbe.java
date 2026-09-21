package io.github.digitalsmile.goldberry.example.webpump;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.web.BackendWebView;
import io.github.digitalsmile.goldberry.render.web.WebSize;
import io.github.digitalsmile.goldberry.render.web.WebViewSpec;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// Measures how fast an embedded engine's animation actually runs.
///
/// ## What this is for
///
/// A page's frame rate is not something the toolkit can see. The engine draws
/// into its own platform window, so there are no pixels to count and
/// [io.github.digitalsmile.goldberry.stats.FrameStats] describes Goldberry's
/// frames rather than WebKit's. The only witness is the page itself, so this
/// asks it: the document counts its own `requestAnimationFrame` callbacks and
/// reports through a binding.
///
/// ## Why the answer is not the display's rate
///
/// On Linux the engine runs on GLib's main context, and **nothing drives that
/// context except Goldberry's event loop**:
/// [io.github.digitalsmile.goldberry.render.web.WebViewEngine#pump()] is called
/// once per turn of
/// [io.github.digitalsmile.goldberry.render.event.EventLoop#run].
/// So the page is serviced on a polling clock rather than when it has work, and
/// two constants bound it — the loop's wait while a page is open, and how many
/// GLib iterations one pump may run. This probe is how those two numbers are
/// turned from a guess into a measurement.
///
/// A **window** page rather than an embedded one on purpose: embedding needs a
/// native handle and so is X11-only, while the pump is process-wide and
/// identical either way. Measuring the window form removes a variable that has
/// nothing to do with the question.
public final class PumpProbe implements Application {

    private static final Logger LOG = LoggerFactory.getLogger(PumpProbe.class);

    /// How long to measure for. Long enough that the first sample — which is
    /// dropped, see [FpsLog#settled()] — is a small part of the run.
    static final String SECONDS_PROPERTY = "goldberry.webpump.seconds";

    private static final int DEFAULT_SECONDS = 12;

    private final FpsLog log = new FpsLog();

    private @Nullable BackendWebView page;

    /// Set once the summary has been logged, so closing the window by hand after
    /// the run has finished does not print it twice.
    private boolean reported;

    public static void main(String[] args) {
        Goldberry.launch(new PumpProbe(), args);
    }

    @Override
    public String title() {
        return "Goldberry — web pump probe";
    }

    @Override
    public LogicalSize size() {
        return new LogicalSize(560, 200);
    }

    @Override
    public Widget root() {
        return new Column(
                List.of(
                        new Text("Web pump probe", Attributes.NONE.id("probe-title")),
                        new Text(
                                "A page is open in a window of its own, animating and counting its"
                                        + " own frames. The summary is logged when the run ends.",
                                Attributes.NONE.id("probe-note").classes("caption"))),
                Attributes.NONE.id("probe-root"));
    }

    @Override
    public void start(Host host) {
        var spec = WebViewSpec.ofHtml(ProbeDocument.html())
                .title("pump probe")
                .sized(900, 600, WebSize.INITIAL)
                .on(ProbeDocument.CALLBACK, this::report);

        var opened = host.webView(spec);
        if (opened.isEmpty()) {
            LOG.error("no page could be opened, so there is nothing to measure");
            host.window().close();
            return;
        }
        page = opened.get();
        var seconds = seconds();
        LOG.info("measuring the page's own frame rate for {}s", seconds);
        host.after(Duration.ofSeconds(seconds), () -> finish(host));
    }

    /// One reading from the page. See [ProbeArguments] for the shape.
    private String report(String arguments) {
        var parsed = ProbeArguments.parse(arguments);
        if (parsed.isEmpty()) {
            LOG.warn("the page reported something this probe cannot read: {}", arguments);
            return "\"\"";
        }
        var values = parsed.get();
        var reading = log.add(values.rafFps(), values.timelineFps(), values.timerFps(), values.visible());
        LOG.info(
                "report {}: {} raf/s, {} timeline/s, {} timer/s, {} — {}",
                reading.index() + 1,
                "%.1f".formatted(reading.rafFps()),
                "%.1f".formatted(reading.timelineFps()),
                "%.1f".formatted(reading.timerFps()),
                reading.visible() ? "visible" : "HIDDEN",
                reading.verdict());
        // Valid JSON, because the page's call is a promise and something has to
        // resolve it. Nothing is waiting on the value.
        return "\"\"";
    }

    /// Logs what was measured and ends the run.
    private void finish(Host host) {
        summarize();
        if (page != null && !page.isClosed()) {
            page.close();
        }
        host.window().close();
    }

    @Override
    public void stop() {
        // The safety net for a run ended by closing the window rather than by
        // the timer: the samples are worth printing either way.
        summarize();
    }

    private void summarize() {
        if (reported) {
            return;
        }
        reported = true;
        LOG.info("page animation: {}", log.describe());
    }

    /// [#SECONDS_PROPERTY], or [#DEFAULT_SECONDS] when it is unset or will not
    /// parse — a malformed tuning flag should not stop the probe running.
    static int seconds() {
        var raw = System.getProperty(SECONDS_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SECONDS;
        }
        try {
            return Math.max(2, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_SECONDS;
        }
    }
}
