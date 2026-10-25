package dev.goldberry.media.platform.vulkan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;

/// The Decoder SPI over a [CodecDecoder]: packets in, pictures out in output
/// order, each lent until the next call.
///
/// Decoding is synchronous, a picture at a time, so [#send] always takes the
/// packet; the pictures it made wait here until [#receive] hands them out.
final class VulkanVideoDecoder implements Decoder {

    private final CodecDecoder codec;
    private final HostPictures pictures;
    private final Deque<CodecDecoder.Output> ready = new ArrayDeque<>();
    private CodecDecoder.@Nullable Output lent;
    private boolean ending;
    private boolean closed;

    VulkanVideoDecoder(CodecDecoder codec, HostPictures pictures) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.pictures = Objects.requireNonNull(pictures, "pictures");
    }

    @Override
    public boolean send(Packet packet) {
        requireOpen();
        giveBack();
        ready.addAll(codec.decode(packet));
        return true;
    }

    @Override
    public void sendEnd() {
        requireOpen();
        giveBack();
        ready.addAll(codec.drain());
        ending = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        giveBack();
        var next = ready.pollFirst();
        if (next == null) {
            return ending ? Received.ENDED : Received.NEEDS_INPUT;
        }
        lent = next;
        var picture = next.picture();
        return new Received.Decoded(new VideoFrame(
                picture.format(),
                picture.width(),
                picture.height(),
                List.of(picture.luma(), picture.chroma()),
                List.of(picture.stride(), picture.stride()),
                next.matrix(),
                next.fullRange(),
                next.ptsNanos(),
                next.colour()));
    }

    @Override
    public void flush() {
        requireOpen();
        giveBack();
        ready.forEach(output -> pictures.recycle(output.picture()));
        ready.clear();
        codec.reset();
        ending = false;
    }

    /// The picture lent last goes back to the pool: the SPI's caller is done
    /// with it by its next call.
    private void giveBack() {
        var last = lent;
        lent = null;
        if (last != null) {
            pictures.recycle(last.picture());
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        codec.close();
        pictures.close();
    }
}
