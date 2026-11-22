package dev.goldberry.media.view;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.image.Image;
import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.media.picture.YuvConverter;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A picture's planes converted to BGRA on the CPU, for a view that draws
/// planes where no GPU layer shows them: a pushed call tile, a camera, in a
/// window presented on the CPU or in an offscreen picture.
///
/// One buffer, reused, and the picture last converted into it: a frame that
/// draws the same picture again converts nothing. The converter is the media
/// natives' libyuv, made on the first picture; where those natives cannot be
/// loaded the view draws nothing, as it did before, and says so once.
///
/// Safe from any thread, one conversion at a time.
final class PlanesOnCpu implements AutoCloseable {

    private static final Logger LOG = Logs.of(PlanesOnCpu.class);

    private @Nullable YuvConverter converter;
    private boolean unavailable;
    private boolean closed;
    private @Nullable ByteBuffer pixels;
    private @Nullable VideoPlanes converted;
    private @Nullable Image image;

    /// `planes` as an image the size of the picture, or null where it cannot
    /// be converted: no natives, memory gone with its device, or closed.
    synchronized @Nullable Image image(VideoPlanes planes) {
        Objects.requireNonNull(planes, "planes");
        if (planes == converted) {
            return image;
        }
        var yuv = converter();
        if (yuv == null) {
            return null;
        }
        var width = planes.width();
        var height = planes.height();
        var stride = width * 4;
        var bytes = (long) stride * height;
        var target = pixels;
        if (target == null || target.capacity() < bytes) {
            target = ByteBuffer.allocateDirect(Math.toIntExact(bytes));
            pixels = target;
        }
        try {
            yuv.toBgra(frame(planes), MemorySegment.ofBuffer(target), stride);
        } catch (IllegalStateException | MediaException | IllegalArgumentException e) {
            LOG.debug("a {}×{} {} picture was not converted", width, height, planes.format(), e);
            converted = null;
            image = null;
            return null;
        }
        converted = planes;
        image = Image.of(new PixelBuffer(
                new PhysicalSize(width, height),
                PixelFormat.BGRA32_PREMULTIPLIED,
                stride,
                target.duplicate().clear().limit(Math.toIntExact(bytes))));
        return image;
    }

    /// The planes as the converter reads them: each plane's memory, in place.
    static VideoFrame frame(VideoPlanes planes) {
        var format = planes.format();
        var segments = new ArrayList<MemorySegment>(format.planes());
        var strides = new ArrayList<Integer>(format.planes());
        for (var index = 0; index < format.planes(); index++) {
            segments.add(MemorySegment.ofBuffer(planes.plane(index)));
            strides.add(planes.stride(index));
        }
        return new VideoFrame(
                format,
                planes.width(),
                planes.height(),
                segments,
                strides,
                planes.matrix(),
                planes.fullRange(),
                planes.ptsNanos(),
                planes.colour());
    }

    private @Nullable YuvConverter converter() {
        if (closed || unavailable) {
            return null;
        }
        var current = converter;
        if (current == null) {
            try {
                current = new YuvConverter();
            } catch (MediaException | LinkageError e) {
                unavailable = true;
                LOG.warn(
                        "pictures of planes are not drawn on the CPU: the media natives did not load ({})",
                        e.getMessage());
                return null;
            }
            converter = current;
        }
        return current;
    }

    /// Lets go of the buffer and the converter. Idempotent.
    @Override
    public synchronized void close() {
        closed = true;
        var current = converter;
        converter = null;
        if (current != null) {
            current.close();
        }
        pixels = null;
        converted = null;
        image = null;
    }
}
