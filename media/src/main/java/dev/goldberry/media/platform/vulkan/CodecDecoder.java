package dev.goldberry.media.platform.vulkan;

import java.util.List;
import java.util.Objects;

import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.VideoFrame;

/// One codec's half of a Vulkan Video decoder: what turns packets into decoded
/// pictures, in output order, through a [VideoSession].
///
/// The codec reads its headers, keeps its decoded picture buffer and its
/// picture order, makes the session when the stream's first header says what
/// it needs, and copies each picture into [HostPictures] as it is decoded.
/// [VulkanVideoDecoder] does the rest of the SPI.
public interface CodecDecoder extends AutoCloseable {

    /// A picture ready to be handed out.
    ///
    /// @param picture   its planes, in host memory
    /// @param ptsNanos  its time, from the packet it was decoded from
    /// @param matrix    its matrix
    /// @param fullRange whether luma spans the whole code range
    /// @param colour    its primaries, transfer function and light levels
    record Output(
            HostPictures.Picture picture,
            long ptsNanos,
            VideoFrame.ColorMatrix matrix,
            boolean fullRange,
            ColorDescription colour) {

        public Output {
            Objects.requireNonNull(picture, "picture");
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(colour, "colour");
        }
    }

    /// Decodes what `packet` holds; returns the pictures now due for output, in
    /// order.
    ///
    /// @throws RuntimeException when the stream is one this decoder does not
    ///                          decode, or the device fails: the Engine moves to
    ///                          the next rung of the ladder
    List<Output> decode(Packet packet);

    /// Every picture still waiting, in order: the end of the stream.
    List<Output> drain();

    /// Forgets every picture and reference, for a seek: the next packet is a
    /// keyframe.
    void reset();

    /// Gives back what was made on the device. Idempotent.
    @Override
    void close();
}
