package dev.goldberry.example.capture;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.picture.Picture;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;

/// A picture's bytes copied into memory of its own, so it outlives the
/// source's slot, which is borrowed until two more pictures have shown; and
/// what a picture is, for the Capture screen's metrics.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
public final class PictureCopies {

    private PictureCopies() {}

    /// `picture` copied, presented at `ptsNanos`, rows packed; empty for planes
    /// that live in a GPU store, which have no bytes here to copy.
    static Optional<Picture> copy(Picture picture, long ptsNanos) {
        return switch (picture) {
            case VideoPicture bgra -> Optional.of(copy(bgra, ptsNanos));
            case VideoPlanes planes when planes.staged().isPresent() -> Optional.empty();
            case VideoPlanes planes -> Optional.of(copy(planes, ptsNanos));
        };
    }

    /// The memory a copy of `picture` takes: its rows packed.
    public static long bytes(Picture picture) {
        return switch (picture) {
            case VideoPicture bgra -> (long) bgra.width() * 4 * bgra.height();
            case VideoPlanes planes -> {
                var total = 0L;
                for (var index = 0; index < planes.format().planes(); index++) {
                    total += (long) planes.format().planeRowBytes(index, planes.width())
                            * planes.format().planeRows(index, planes.height());
                }
                yield total;
            }
        };
    }

    /// What `picture` is laid out as: `NV12 planes`, `BGRA`.
    public static String kind(Picture picture) {
        return switch (picture) {
            case VideoPicture _ -> "BGRA";
            case VideoPlanes planes -> planes.format().name() + " planes";
        };
    }

    private static VideoPicture copy(VideoPicture picture, long ptsNanos) {
        var row = picture.width() * 4;
        var target = ByteBuffer.allocateDirect(row * picture.height());
        rows(picture.pixels(), picture.stride(), target, row, picture.height());
        return new VideoPicture(picture.width(), picture.height(), row, target, ptsNanos, picture.opaque());
    }

    private static VideoPlanes copy(VideoPlanes picture, long ptsNanos) {
        var format = picture.format();
        var planes = new ArrayList<ByteBuffer>(format.planes());
        var strides = new ArrayList<Integer>(format.planes());
        for (var index = 0; index < format.planes(); index++) {
            var row = format.planeRowBytes(index, picture.width());
            var count = format.planeRows(index, picture.height());
            var target = ByteBuffer.allocateDirect(row * count);
            rows(picture.plane(index), picture.stride(index), target, row, count);
            planes.add(target);
            strides.add(row);
        }
        return new VideoPlanes(
                format,
                picture.width(),
                picture.height(),
                List.copyOf(planes),
                List.copyOf(strides),
                picture.matrix(),
                picture.fullRange(),
                ptsNanos,
                picture.colour());
    }

    /// `count` rows of `row` bytes from `source`, `stride` apart, packed into
    /// `target` from its start.
    private static void rows(ByteBuffer source, int stride, ByteBuffer target, int row, int count) {
        for (var at = 0; at < count; at++) {
            target.put(at * row, source, at * stride, row);
        }
    }
}
