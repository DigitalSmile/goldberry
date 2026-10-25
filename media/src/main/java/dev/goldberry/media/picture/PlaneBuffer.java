package dev.goldberry.media.picture;

import java.nio.ByteBuffer;

import dev.goldberry.media.codec.PixelFormat;

/// One picture's planes in a [PlaneStore]'s memory: written by the video
/// thread, read by the view that offered the store, and given back by the
/// player with [#release].
///
/// A plane may become unreadable while the buffer is held, when the memory it
/// lives in goes with its device: [#plane] then throws
/// [IllegalStateException], and the picture is not drawn.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
public interface PlaneBuffer {

    /// The plane layout.
    PixelFormat format();

    /// Width in pixels.
    int width();

    /// Height in pixels.
    int height();

    /// Bytes from one row of plane `index` to the next: at least the row's.
    int stride(int index);

    /// Plane `index`, little-endian, from its first row: writable by the
    /// holder. A fresh view each call.
    ///
    /// @throws IllegalStateException when the memory is not there now
    ByteBuffer plane(int index);

    /// Gives the memory back to the store. Idempotent; any thread.
    void release();
}
