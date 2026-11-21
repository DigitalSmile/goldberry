package dev.goldberry.media.view;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.media.picture.PictureSource;
import dev.goldberry.media.view.gpu.GpuVideo;
import dev.goldberry.media.view.gpu.VideoPresenter;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.image.Fit;

/// The pictures of any [PictureSource], drawn as a `video-view` draws a
/// player's: a call's tile showing frames the application pushes, a camera, a
/// codec of the application's own.
///
/// ```java
/// var tile = new PushedPictures();
/// new PictureView(tile, Fit.COVER)
/// ```
///
/// The same node to a stylesheet as `video-view`, and drawn the same way: on
/// the GPU as a layer where the frame shows one, and on the CPU otherwise. It
/// draws again when the source says a picture arrived, at most once a frame
/// however many arrived, or, for a source that knows when its next picture is
/// due, when that time comes.
///
/// A [dev.goldberry.media.MediaPlayer] is a source too, but its own
/// `video-view` also tells the player which form to decode into, which this
/// does not: a player is better shown by a [VideoView].
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
///
/// @param source     where the pictures come from
/// @param fit        how a picture fills a box of another shape
/// @param attributes id and classes
public record PictureView(PictureSource source, Fit fit, Attributes attributes)
        implements Widget.Stateful, Attributed<PictureView> {

    public PictureView {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `source`'s pictures, letterboxed.
    public PictureView(PictureSource source) {
        this(source, Fit.CONTAIN, Attributes.NONE);
    }

    /// `source`'s pictures, placed by `fit`.
    public PictureView(PictureSource source, Fit fit) {
        this(source, fit, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new PictureViewState();
    }

    @Override
    public PictureView withAttributes(Attributes attributes) {
        return new PictureView(source, fit, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Listens to the source, and builds the surface.
    static final class PictureViewState extends State<PictureView> {

        private @Nullable AutoCloseable subscription;

        private @Nullable VideoPresenter presenter;

        private EventLoop.@Nullable Timer due;

        /// Whether a redraw is already on its way to the UI thread, so a
        /// source pushing faster than the display costs one rebuild a frame.
        private final AtomicBoolean pending = new AtomicBoolean();

        @Override
        protected void initState() {
            listen(widget().source());
            presenter = GpuVideo.presenter(_ -> {});
        }

        @Override
        protected void didUpdateWidget(PictureView previous) {
            if (previous.source() != widget().source()) {
                unlisten();
                listen(widget().source());
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var view = widget();
            var wait = view.source().untilNextPicture();
            if (wait.isPresent() && due == null) {
                var delay = wait.get().compareTo(FollowingState.PICTURE_MIN_WAIT) < 0
                        ? FollowingState.PICTURE_MIN_WAIT
                        : wait.get();
                context.host()
                        .ifPresent(host -> due = host.after(delay, () -> {
                            due = null;
                            refresh();
                        }));
            }
            return new VideoSurface(view.source(), view.fit(), view.attributes(), null, presenter);
        }

        private void listen(PictureSource source) {
            subscription =
                    source.onPicture(coalesced(pending, task -> Goldberry.ui().execute(task), this::refresh));
        }

        /// What a source's listener does: hand `refresh` to `ui` once, and not
        /// again until it has run, however many pictures arrive in between.
        static Runnable coalesced(AtomicBoolean pending, Executor ui, Runnable refresh) {
            return () -> {
                if (pending.compareAndSet(false, true)) {
                    ui.execute(() -> {
                        pending.set(false);
                        refresh.run();
                    });
                }
            };
        }

        private void refresh() {
            if (isMounted()) {
                setState(() -> {});
            }
        }

        private void unlisten() {
            var current = subscription;
            subscription = null;
            if (current != null) {
                try {
                    current.close();
                } catch (Exception e) {
                    throw new IllegalStateException("the source refused to let go of a listener", e);
                }
            }
        }

        @Override
        protected void dispose() {
            unlisten();
            if (due != null) {
                due.cancel();
                due = null;
            }
            var gpu = presenter;
            presenter = null;
            if (gpu != null) {
                gpu.close();
            }
            super.dispose();
        }
    }
}
