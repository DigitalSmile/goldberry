package dev.goldberry.media.picture;

import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;

/// A picture of planes as [YuvReference] reads it: its tags, and the code of
/// any sample. A [VideoPlanes] the player hands to GPU present and a
/// [VideoFrame] a decoder hands to CPU present both are one.
///
/// A code is the sample's value in its own bit depth: P010's ten bits are
/// stored high, and are shifted down here.
public interface YuvSource {

    PixelFormat format();

    int width();

    int height();

    VideoFrame.ColorMatrix matrix();

    boolean fullRange();

    /// The primaries, transfer function and light levels: an HDR or wide-gamut
    /// picture is read through them to SDR BT.709.
    default ColorDescription colour() {
        return ColorDescription.UNSPECIFIED;
    }

    /// The code of plane `plane`'s sample at (`column`, `row`), counted in
    /// samples: an interleaved chroma plane's Cb and Cr are two columns.
    int code(int plane, int column, int row);

    /// Cb (`component` 0) or Cr (1) at chroma texel (`x`, `y`), whichever way the
    /// layout stores them.
    default int chroma(int component, int x, int y) {
        return format().planes() == 2 ? code(1, x * 2 + component, y) : code(1 + component, x, y);
    }

    /// The planes the player handed to GPU present.
    static YuvSource of(VideoPlanes planes) {
        return new YuvSource() {
            @Override
            public PixelFormat format() {
                return planes.format();
            }

            @Override
            public int width() {
                return planes.width();
            }

            @Override
            public int height() {
                return planes.height();
            }

            @Override
            public VideoFrame.ColorMatrix matrix() {
                return planes.matrix();
            }

            @Override
            public boolean fullRange() {
                return planes.fullRange();
            }

            @Override
            public ColorDescription colour() {
                return planes.colour();
            }

            @Override
            public int code(int plane, int column, int row) {
                var sample = planes.sample(plane, column, row);
                return planes.format() == PixelFormat.P010 ? sample >>> 6 : sample;
            }
        };
    }

    /// A decoded frame, as CPU present converts it.
    static YuvSource of(VideoFrame frame) {
        var sixteen = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
        return new YuvSource() {
            @Override
            public PixelFormat format() {
                return frame.format();
            }

            @Override
            public int width() {
                return frame.width();
            }

            @Override
            public int height() {
                return frame.height();
            }

            @Override
            public VideoFrame.ColorMatrix matrix() {
                return frame.matrix();
            }

            @Override
            public boolean fullRange() {
                return frame.fullRange();
            }

            @Override
            public ColorDescription colour() {
                return frame.colour();
            }

            @Override
            public int code(int plane, int column, int row) {
                var bytes = frame.format().bytesPerSample();
                var at = (long) row * frame.strides().get(plane) + (long) column * bytes;
                var segment = frame.planes().get(plane);
                if (bytes == 1) {
                    return segment.get(ValueLayout.JAVA_BYTE, at) & 0xFF;
                }
                var sample = segment.get(sixteen, at) & 0xFFFF;
                return frame.format() == PixelFormat.P010 ? sample >>> 6 : sample;
            }
        };
    }
}
