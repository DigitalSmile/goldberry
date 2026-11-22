package dev.goldberry.camera.backend;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.camera.CameraStatus;
import dev.goldberry.log.Logs;
import dev.goldberry.media.picture.Picture;

/// What every camera keeps for the views over it: the picture shown, the
/// status, and who to tell when either changes. Shared by a device's camera
/// and a synthetic one.
///
/// Safe from any thread. A final status is never left.
public final class CameraFeed {

    private static final Logger LOG = Logs.of(CameraFeed.class);

    private final List<Runnable> pictureListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<? super CameraStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private volatile @Nullable Picture shown;
    private volatile CameraStatus status;

    /// A feed in `status`, showing nothing yet.
    public CameraFeed(CameraStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /// The picture to show, or empty before the first.
    public Optional<Picture> shown() {
        return Optional.ofNullable(shown);
    }

    /// Shows `picture`, and tells every picture listener.
    public void show(Picture picture) {
        shown = Objects.requireNonNull(picture, "picture");
        for (var listener : pictureListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.warn("a picture listener failed", e);
            }
        }
    }

    /// The status now.
    public CameraStatus status() {
        return status;
    }

    /// Moves to `next` and tells every status listener, unless the status is
    /// already `next` or final.
    ///
    /// @return whether it moved
    public boolean change(CameraStatus next) {
        Objects.requireNonNull(next, "next");
        synchronized (this) {
            if (status.isFinal() || status.equals(next)) {
                return false;
            }
            status = next;
        }
        for (var listener : statusListeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                LOG.warn("a status listener failed", e);
            }
        }
        return true;
    }

    /// Tells `listener` of each picture until the subscription is closed.
    public AutoCloseable onPicture(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        pictureListeners.add(listener);
        return () -> pictureListeners.remove(listener);
    }

    /// Tells `listener` of each status until the subscription is closed.
    public AutoCloseable onStatus(Consumer<? super CameraStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        statusListeners.add(listener);
        return () -> statusListeners.remove(listener);
    }
}
