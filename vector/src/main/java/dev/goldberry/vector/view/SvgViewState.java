package dev.goldberry.vector.view;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.Goldberry;
import dev.goldberry.Host;
import dev.goldberry.icon.Icon;
import dev.goldberry.log.Logs;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.core.image.IntrinsicSize;

/// An [SvgView]'s load: which source it asked for, and what came back.
///
/// A load that finishes after the view's source changed is dropped when it
/// arrives, as `image`'s is: each request carries a generation.
final class SvgViewState extends State<SvgView> {

    private static final Logger LOG = Logs.of(SvgViewState.class);

    /// The sources whose failure has been said, so one missing file shown four
    /// times is one line. Bounded, as `image`'s set is.
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static final int REPORT_LIMIT = 256;

    private SvgLoad load = new SvgLoad.Loading();
    private @Nullable String requested;
    private int generation;
    private double measuredWidth;
    private @Nullable Icon errorIcon;
    private @Nullable Host host;

    @Override
    protected void dispose() {
        generation++;
        if (errorIcon != null) {
            errorIcon.close();
            errorIcon = null;
        }
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var view = widget();
        var key = view.source().key();
        if (!key.equals(requested)) {
            request(view.source());
        }
        if (load instanceof SvgLoad.Failed && errorIcon == null) {
            errorIcon = Icon.bundled(SvgView.ERROR_ICON, SvgView.ERROR_ICON_SIZE);
        }
        var onMeasured = IntrinsicSize.whenChanged(measuredWidth, width -> {
            if (isMounted()) {
                setState(() -> measuredWidth = width);
                repaint();
            }
        });
        return view.decorative()
                ? new SvgBox(load, view.alt(), view.fit(), measuredWidth, onMeasured, errorIcon, view.attributes())
                : new SvgFigure(load, view.alt(), view.fit(), measuredWidth, onMeasured, errorIcon, view.attributes());
    }

    private void request(SvgSource source) {
        requested = source.key();
        var mine = ++generation;
        var future = start(source);
        if (future.isDone()) {
            // Read in the caller: a test, or an offscreen render, whose first
            // frame should draw the document rather than a placeholder for it.
            load = future.handle((document, failure) -> settle(source, document, failure))
                    .join();
            return;
        }
        load = new SvgLoad.Loading();
        var _ = future.whenComplete((document, failure) -> {
            if (mine != generation || !isMounted()) {
                return;
            }
            setState(() -> load = settle(source, document, failure));
            repaint();
        });
    }

    /// Reads on a virtual thread while the toolkit is running, its answer on the
    /// UI thread; in the caller when it is not, where no answer would come back.
    private static CompletableFuture<SvgDocument> start(SvgSource source) {
        if (Goldberry.isUiThread()) {
            return Goldberry.async(() -> SvgDocument.read(source));
        }
        try {
            return CompletableFuture.completedFuture(SvgDocument.read(source));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static SvgLoad settle(SvgSource source, @Nullable SvgDocument document, @Nullable Throwable failure) {
        if (failure != null || document == null) {
            var cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                    ? wrapped.getCause()
                    : failure;
            var reason = cause == null ? "no document" : String.valueOf(cause.getMessage());
            if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(source.key())) {
                LOG.warn("svg-view {} did not load: {}", source.key(), reason);
            }
            return new SvgLoad.Failed(reason);
        }
        return new SvgLoad.Ready(document);
    }

    /// Forgets which failures were said, for a test.
    static void forgetReported() {
        REPORTED.clear();
    }

    private void repaint() {
        if (host != null) {
            host.repaint();
        }
    }
}
