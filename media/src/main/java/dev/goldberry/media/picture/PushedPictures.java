package dev.goldberry.media.picture;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

/// A [PictureSource] an application pushes pictures into: a call's tiles, a
/// camera, frames decoded by a codec of its own.
///
/// ```java
/// var tile = new PushedPictures();
/// column.add(new PictureView(tile));
/// // on the network thread, for each decoded frame:
/// tile.push(new VideoPlanes(...));
/// ```
///
/// [#push] may be called from any thread, as often as frames arrive; a view
/// shows the newest and draws once per frame however many arrived in it. A
/// [VideoPicture] is drawn on the CPU and on the GPU, and [VideoPlanes] on the
/// GPU only, so a source pushing planes into a window with no GPU shows
/// nothing.
///
/// **The borrowing rule** is [PictureSource]'s, and here it binds the
/// application: the bytes of a picture pushed must stay as they are until two
/// more pictures have been pushed after it. Three buffers used in turn keep it.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
public final class PushedPictures implements PictureSource {

    private volatile @Nullable Picture shown;

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /// A source with nothing to show yet.
    public PushedPictures() {}

    /// Makes `picture` the one to show, and tells every view.
    public void push(Picture picture) {
        shown = Objects.requireNonNull(picture, "picture");
        for (var listener : listeners) {
            listener.run();
        }
    }

    /// Shows nothing again: a call's tile whose camera was turned off.
    public void clear() {
        shown = null;
        for (var listener : listeners) {
            listener.run();
        }
    }

    @Override
    public Optional<Picture> shownPicture() {
        return Optional.ofNullable(shown);
    }

    @Override
    public AutoCloseable onPicture(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
