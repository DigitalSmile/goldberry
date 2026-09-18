package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// An [ImageView]'s load: which variant it asked for, and what came back.
///
/// **A load that finishes after its question changed is ignored.** Each request
/// carries a generation; a view whose `src` moved, or whose window moved to a
/// display of another scale, asks again, and the answer to the old question —
/// still decoding on its virtual thread — is dropped when it arrives rather than
/// drawn over the new one (ADR-0358).
final class ImageState extends State<ImageView> {

    private static final Logger LOG = Logs.of(ImageState.class);

    private ImageLoad load = new ImageLoad.Loading();
    private @Nullable Variant requested;
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
        var scale = host == null ? 1.0 : host.displayScale();
        var variant = Variant.pick(view.variants(), scale);
        if (!variant.equals(requested)) {
            request(view.loader(), variant);
        }
        if (load instanceof ImageLoad.Failed && errorIcon == null) {
            errorIcon = Icon.bundled(ImageView.ERROR_ICON, ImageView.ERROR_ICON_SIZE);
        }
        var onMeasured = ImagePaint.whenChanged(measuredWidth, width -> {
            if (isMounted()) {
                setState(() -> measuredWidth = width);
                repaint();
            }
        });
        return view.decorative()
                ? new ImageBox(load, view.alt(), view.fit(), measuredWidth, onMeasured, errorIcon, view.attributes())
                : new ImageFigure(
                        load, view.alt(), view.fit(), measuredWidth, onMeasured, errorIcon, view.attributes());
    }

    private void request(ImageLoader loader, Variant variant) {
        requested = variant;
        var mine = ++generation;
        var future = loader.load(variant.source());
        if (future.isDone()) {
            // Already there -- the cache, a decoded source, or a loader with no
            // thread. Read it now, so the first frame draws the picture rather
            // than a placeholder for one frame.
            load = future.handle((image, failure) -> settle(variant, image, failure))
                    .join();
            return;
        }
        load = new ImageLoad.Loading();
        var _ = future.whenComplete((image, failure) -> {
            if (mine != generation || !isMounted()) {
                return;
            }
            setState(() -> load = settle(variant, image, failure));
            repaint();
        });
    }

    private ImageLoad settle(Variant variant, @Nullable Image image, @Nullable Throwable failure) {
        if (failure != null || image == null) {
            var cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                    ? wrapped.getCause()
                    : failure;
            var reason = cause == null ? "no image" : String.valueOf(cause.getMessage());
            // A source with no key is one the cache never held, so there is nothing
            // to deduplicate against and nothing shared to name -- `ImageSource.of`
            // is the only one, and the image it carries is already decoded, so this
            // branch is reached for it only when a loader was replaced. It is
            // described by the record instead, which is what `toString` is for.
            var named = variant.source().key();
            if (named == null) {
                LOG.warn("image {} did not load: {}", variant.source(), reason);
            } else if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(named)) {
                LOG.warn("image {} did not load: {}", named, reason);
            }
            return new ImageLoad.Failed(reason);
        }
        return new ImageLoad.Ready(image, variant.scale());
    }

    /// The sources whose failure has been reported.
    ///
    /// One picture shown four ways is four views, four loads and — before this —
    /// four identical lines about one missing file. `OverflowLog`'s argument and
    /// `OverflowLog`'s answer: a bounded set of what has already been said
    /// ([ADR-0395]).
    ///
    /// Keyed on the **source**, not the view, because that is what failed. The
    /// reason cannot differ between two views of one key: they share a cache
    /// entry.
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /// How many distinct failures are remembered before the deduplication gives
    /// up and lets them all through — `OverflowLog`'s cap, for its reason.
    private static final int REPORT_LIMIT = 256;

    /// Forgets what has been reported, for a test that loads the same broken
    /// source twice. `OverflowLog.forget`'s reason exactly.
    static void forgetReported() {
        REPORTED.clear();
    }

    /// Whether anything has been reported, for the test — there is no appender on
    /// the classpath to read the log back from, so the set is what an assertion
    /// can see.
    static int reportedCount() {
        return REPORTED.size();
    }

    private void repaint() {
        if (host != null) {
            host.repaint();
        }
    }
}
