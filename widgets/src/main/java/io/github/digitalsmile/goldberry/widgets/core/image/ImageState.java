package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.concurrent.CompletionException;

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
            LOG.warn("image {} did not load: {}", variant.source().key(), reason);
            return new ImageLoad.Failed(reason);
        }
        return new ImageLoad.Ready(image, variant.scale());
    }

    private void repaint() {
        if (host != null) {
            host.repaint();
        }
    }
}
