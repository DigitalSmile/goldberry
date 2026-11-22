package dev.goldberry.camera.view;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.camera.backend.PlanePool;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.PictureSource;
import dev.goldberry.media.picture.VideoPlanes;

/// A source's NV12 pictures, mirrored left for right, for the one view that
/// asked: the camera's own pictures, which a call sends, stay as they are.
///
/// Each picture is mirrored once, when a view first asks for it, into a pool
/// of three, so the borrowing rule holds for the copies as for the originals.
/// A picture in another layout is shown as it is.
final class MirroredPictures implements PictureSource {

    private final PictureSource source;
    private @Nullable PlanePool pool;
    private byte[] row = new byte[0];
    private @Nullable Picture from;
    private @Nullable Picture mirrored;

    MirroredPictures(PictureSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public synchronized Optional<Picture> shownPicture() {
        var shown = source.shownPicture();
        if (shown.isEmpty()) {
            return shown;
        }
        var picture = shown.get();
        if (picture == from) {
            return Optional.ofNullable(mirrored);
        }
        from = picture;
        mirrored =
                picture instanceof VideoPlanes planes && planes.format() == PixelFormat.NV12 ? mirror(planes) : picture;
        return Optional.ofNullable(mirrored);
    }

    @Override
    public AutoCloseable onPicture(Runnable listener) {
        return source.onPicture(listener);
    }

    private VideoPlanes mirror(VideoPlanes planes) {
        var width = planes.width();
        var height = planes.height();
        var current = pool;
        if (current == null || current.width() != width || current.height() != height) {
            current = new PlanePool(width, height);
            pool = current;
        }
        var slot = current.next();
        var chromaRow = PixelFormat.NV12.planeRowBytes(1, width);
        if (row.length < Math.max(width, chromaRow)) {
            row = new byte[Math.max(width, chromaRow)];
        }
        var luma = planes.plane(0);
        for (var y = 0; y < height; y++) {
            luma.get(y * planes.stride(0), row, 0, width);
            reverse(row, width, 1);
            slot.luma().put(y * slot.lumaStride(), row, 0, width);
        }
        var chroma = planes.plane(1);
        for (var y = 0; y < PixelFormat.NV12.planeRows(1, height); y++) {
            chroma.get(y * planes.stride(1), row, 0, chromaRow);
            reverse(row, chromaRow, 2);
            slot.chroma().put(y * slot.chromaStride(), row, 0, chromaRow);
        }
        return current.written(slot, planes.matrix(), planes.fullRange(), planes.ptsNanos());
    }

    /// Reverses the first `length` bytes of `row` in units of `unit` bytes:
    /// one for luma, two for a U and V pair.
    static void reverse(byte[] row, int length, int unit) {
        for (int left = 0, right = length - unit; left < right; left += unit, right -= unit) {
            for (var offset = 0; offset < unit; offset++) {
                var swap = row[left + offset];
                row[left + offset] = row[right + offset];
                row[right + offset] = swap;
            }
        }
    }
}
