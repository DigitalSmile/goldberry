package dev.goldberry.media.picture;

import java.util.Optional;

import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.codec.PixelFormat;

/// Memory a view's planes are written into where they are decoded: a view
/// that uploads planes to the GPU offers its upload memory, and the video
/// thread copies each picture there instead of into a buffer of its own, so
/// the view has nothing left to copy on its thread.
///
/// A view offers one with [MediaPlayer.Attachment#setPlaneStore]. The player
/// uses it while that view is the only one attached and draws planes; with
/// none, or two views, it keeps the planes itself as before. Either way the
/// view is handed [VideoPlanes]: a staged picture says so with
/// [VideoPlanes#staged()].
///
/// [#obtain] is called on the video thread, and must not wait: a store with
/// no memory to give at once answers empty, and the picture is kept by the
/// player. The planes of a [PlaneBuffer] are written there, read by the view
/// on its thread, and given back with [PlaneBuffer#release] when the player no
/// longer keeps the picture.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
public interface PlaneStore {

    /// Memory for one picture of `format`, `width` by `height`, free to write,
    /// or empty when there is none to give now. Any thread; never waits.
    Optional<PlaneBuffer> obtain(PixelFormat format, int width, int height);
}
