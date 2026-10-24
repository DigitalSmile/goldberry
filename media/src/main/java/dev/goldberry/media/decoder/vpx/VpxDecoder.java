package dev.goldberry.media.decoder.vpx;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.NativeConstants;
import dev.goldberry.media.ffm.NativeStructs;
import dev.goldberry.media.ffm.calls.VpxCalls;
import dev.goldberry.media.ffm.calls.YuvCalls;

/// A VP9 and VP8 decoder over libvpx: one decode call per packet, then the
/// pictures it produced, one at a time.
///
/// libvpx decodes a packet in the call that hands it over and keeps the
/// pictures of that call, a VP9 superframe's several or a hidden alt-ref
/// frame's none, until the next decode. So [#send] copies the packet into a
/// buffer of this decoder's (libvpx reads it during the call alone), decodes
/// it there and then, and refuses the next packet while a picture of the last
/// one is still to be fetched: handing it over would pull the planes from under
/// the frame a caller holds, and the SPI says a frame lives until the next
/// [#receive]. Every picture of one call carries that packet's time; that is
/// how the pictures of a superframe are timed, and the only timing libvpx's
/// decoder keeps.
///
/// Threads: one per core, libvpx's row-based threading, which changes when a
/// picture is ready and never what is in it: VP9 and VP8 decoding are bit-exact
/// by specification, and `VpxDecoderTest` holds every picture to the checksums
/// FFmpeg's libvpx produced from the same clips.
///
/// What is handed over: 8-bit 4:2:0 as [PixelFormat#I420], 10-bit 4:2:0 as
/// [PixelFormat#I010], both borrowed from libvpx. 4:4:4 and 4:2:2 are
/// subsampled by libyuv into planes this decoder owns, 8-bit to I420 and
/// 10-bit to I010. 4:4:0, 12-bit and NV12 pictures are refused, by name, until
/// the frame contract names them. A WebM sticker's alpha, a second VP9 stream
/// one frame of which rides on each packet, is decoded by a second context of
/// the same codec and handed over as the fourth plane of a
/// [PixelFormat#I420A] picture.
public final class VpxDecoder implements Decoder {

    private static final long CFG_THREADS = NativeStructs.VPX_CODEC_DEC_CFG.byteOffset(groupElement("threads"));
    private static final long CFG_W = NativeStructs.VPX_CODEC_DEC_CFG.byteOffset(groupElement("w"));
    private static final long CFG_H = NativeStructs.VPX_CODEC_DEC_CFG.byteOffset(groupElement("h"));
    private static final long IMAGE_FMT = NativeStructs.VPX_IMAGE.byteOffset(groupElement("fmt"));
    private static final long IMAGE_CS = NativeStructs.VPX_IMAGE.byteOffset(groupElement("cs"));
    private static final long IMAGE_RANGE = NativeStructs.VPX_IMAGE.byteOffset(groupElement("range"));
    private static final long IMAGE_BIT_DEPTH = NativeStructs.VPX_IMAGE.byteOffset(groupElement("bit_depth"));
    private static final long IMAGE_D_W = NativeStructs.VPX_IMAGE.byteOffset(groupElement("d_w"));
    private static final long IMAGE_D_H = NativeStructs.VPX_IMAGE.byteOffset(groupElement("d_h"));
    private static final long IMAGE_PLANES = NativeStructs.VPX_IMAGE.byteOffset(groupElement("planes"));
    private static final long IMAGE_STRIDE = NativeStructs.VPX_IMAGE.byteOffset(groupElement("stride"));
    private static final long IMAGE_SIZE = NativeStructs.VPX_IMAGE.byteSize();

    /// The decode deadline libvpx ignores for its decoders, but asks for.
    private static final long NO_DEADLINE = 0L;

    private final VpxCalls vpx;
    private final YuvCalls yuv;
    private final NativeConstants.Vpx constants;
    private final CodecId codec;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment config;
    private final MemorySegment context;
    private final MemorySegment iterator;
    private final Scratch packetBytes = new Scratch(arena);
    private final Scratch alphaBytes = new Scratch(arena);
    private final Scratch converted = new Scratch(arena);
    private @Nullable MemorySegment alphaContext;
    private @Nullable MemorySegment alphaIterator;
    /// Whether the last decode's pictures are still being fetched: [#send]
    /// refuses a packet while they are.
    private boolean picturesPending;
    /// Whether the last packet carried alpha, so that each of its pictures has
    /// one in the alpha context.
    private boolean alphaPending;
    private long pts = Frame.NO_PTS;
    private boolean ended;
    private boolean endSignalled;
    private boolean closed;

    /// Opens a decoder for `request`, a VP9 or VP8 track.
    ///
    /// @throws IllegalArgumentException when the track is neither
    /// @throws MediaException           when libvpx refuses to open
    public VpxDecoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.codec = request.codec();
        if (codec != CodecId.VP9 && codec != CodecId.VP8) {
            throw new IllegalArgumentException(request.codecName() + " is not libvpx's");
        }
        this.vpx = library.vpx();
        this.yuv = library.yuv();
        this.constants = library.constants().vpx();
        try {
            this.config = arena.allocate(NativeStructs.VPX_CODEC_DEC_CFG);
            config.set(JAVA_INT, CFG_THREADS, Runtime.getRuntime().availableProcessors());
            config.set(JAVA_INT, CFG_W, 0);
            config.set(JAVA_INT, CFG_H, 0);
            this.context = openContext();
            this.iterator = arena.allocate(ADDRESS);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// A context of this decoder's codec, initialised with its configuration.
    private MemorySegment openContext() {
        var opened = arena.allocate(NativeStructs.VPX_CODEC_CTX);
        var iface = codec == CodecId.VP8 ? vpx.vp8Dx().call() : vpx.vp9Dx().call();
        var result = vpx.decInitVer().call(opened, iface, config, 0L, constants.decoderAbiVersion());
        if (result != constants.codecOk()) {
            throw failure("vpx_codec_dec_init", result, opened);
        }
        return opened;
    }

    @Override
    public boolean send(Packet packet) {
        requireOpen();
        if (ended) {
            throw new IllegalStateException("a packet after the end");
        }
        if (picturesPending) {
            return false;
        }
        var data = packet.data();
        if (data.byteSize() == 0) {
            return true;
        }
        decode(context, iterator, packetBytes, data);
        pts = packet.ptsNanos();
        picturesPending = true;
        var alpha = packet.alpha();
        alphaPending = alpha.byteSize() > 0;
        if (alphaPending) {
            if (alphaContext == null) {
                alphaContext = openContext();
                alphaIterator = arena.allocate(ADDRESS);
            }
            decode(alphaContext, Objects.requireNonNull(alphaIterator), alphaBytes, alpha);
        }
        return true;
    }

    /// Copies `data` into `scratch` and decodes it in `target`, starting that
    /// context's iteration over.
    private void decode(MemorySegment target, MemorySegment targetIterator, Scratch scratch, MemorySegment data) {
        var size = Math.toIntExact(data.byteSize());
        var buffer = scratch.ensure(size);
        MemorySegment.copy(data, 0, buffer, 0, size);
        var result = vpx.decode().call(target, buffer, size, MemorySegment.NULL, NO_DEADLINE);
        targetIterator.set(ADDRESS, 0, MemorySegment.NULL);
        if (result != constants.codecOk()) {
            throw failure("vpx_codec_decode", result, target);
        }
    }

    @Override
    public void sendEnd() {
        requireOpen();
        ended = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        while (true) {
            if (picturesPending) {
                var image = vpx.getFrame().call(context, iterator);
                if (!image.equals(MemorySegment.NULL)) {
                    var alpha = alphaPending
                            ? vpx.getFrame()
                                    .call(Objects.requireNonNull(alphaContext), Objects.requireNonNull(alphaIterator))
                            : MemorySegment.NULL;
                    return new Received.Decoded(frame(image, alpha));
                }
                picturesPending = false;
                alphaPending = false;
            }
            if (!ended) {
                return Received.NEEDS_INPUT;
            }
            if (endSignalled) {
                return Received.ENDED;
            }
            // A decode of nothing tells libvpx the stream is over; what it still
            // holds, if anything, is fetched on the next round.
            endSignalled = true;
            var result = vpx.decode().call(context, MemorySegment.NULL, 0, MemorySegment.NULL, NO_DEADLINE);
            iterator.set(ADDRESS, 0, MemorySegment.NULL);
            if (result != constants.codecOk()) {
                throw failure("vpx_codec_decode", result, context);
            }
            picturesPending = true;
        }
    }

    /// Drops the pictures still to be fetched and forgets the end. libvpx has no
    /// flush for a decoder and needs none: the Engine seeks to a keyframe, which
    /// refers to nothing before it.
    @Override
    public void flush() {
        requireOpen();
        picturesPending = false;
        alphaPending = false;
        ended = false;
        endSignalled = false;
        pts = Frame.NO_PTS;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        vpx.destroy().call(context);
        if (alphaContext != null) {
            vpx.destroy().call(alphaContext);
        }
        arena.close();
    }

    /// The picture `image` is, with `alpha`'s luma as its alpha when there is
    /// one that fits, as the SPI's frame.
    @SuppressWarnings("restricted")
    private VideoFrame frame(MemorySegment image, MemorySegment alpha) {
        var picture = image.reinterpret(IMAGE_SIZE);
        var width = picture.get(JAVA_INT, IMAGE_D_W);
        var height = picture.get(JAVA_INT, IMAGE_D_H);
        var planes = planes(picture, width, height);
        var matrix = matrix(picture.get(JAVA_INT, IMAGE_CS), height);
        var fullRange = picture.get(JAVA_INT, IMAGE_RANGE) == constants.crFullRange();
        if (!alpha.equals(MemorySegment.NULL) && planes.format() == PixelFormat.I420) {
            var alphaPicture = alpha.reinterpret(IMAGE_SIZE);
            if (alphaPicture.get(JAVA_INT, IMAGE_D_W) == width
                    && alphaPicture.get(JAVA_INT, IMAGE_D_H) == height
                    && alphaPicture.get(JAVA_INT, IMAGE_BIT_DEPTH) == 8) {
                var stride = stride(alphaPicture, 0);
                return new VideoFrame(
                        PixelFormat.I420A,
                        width,
                        height,
                        List.of(planes.y(), planes.u(), planes.v(), plane(alphaPicture, 0, stride, height)),
                        List.of(planes.yStride(), planes.uvStride(), planes.uvStride(), stride),
                        matrix,
                        fullRange,
                        pts);
            }
        }
        return new VideoFrame(
                planes.format(),
                width,
                height,
                List.of(planes.y(), planes.u(), planes.v()),
                List.of(planes.yStride(), planes.uvStride(), planes.uvStride()),
                matrix,
                fullRange,
                pts);
    }

    /// Three 4:2:0 planes of `picture`: libvpx's own when it decoded 4:2:0,
    /// else a subsampled copy in [#converted].
    private Planes planes(MemorySegment picture, int width, int height) {
        var fmt = picture.get(JAVA_INT, IMAGE_FMT);
        var bitDepth = picture.get(JAVA_INT, IMAGE_BIT_DEPTH);
        var chromaRows = (height + 1) / 2;
        if ((fmt == constants.imgFmtI420() && bitDepth == 8) || (fmt == constants.imgFmtI42016() && bitDepth == 10)) {
            var yStride = stride(picture, 0);
            var uvStride = stride(picture, 1);
            return new Planes(
                    bitDepth == 8 ? PixelFormat.I420 : PixelFormat.I010,
                    plane(picture, 0, yStride, height),
                    plane(picture, 1, uvStride, chromaRows),
                    plane(picture, 2, uvStride, chromaRows),
                    yStride,
                    uvStride);
        }
        if (bitDepth == 8 && (fmt == constants.imgFmtI444() || fmt == constants.imgFmtI422())) {
            return subsample(picture, width, height, fmt == constants.imgFmtI444(), PixelFormat.I420);
        }
        if (bitDepth == 10 && (fmt == constants.imgFmtI44416() || fmt == constants.imgFmtI42216())) {
            return subsample(picture, width, height, fmt == constants.imgFmtI44416(), PixelFormat.I010);
        }
        throw new MediaException(new MediaError.InvalidData(
                bitDepth + "-bit " + sampling(fmt) + " " + codec + " is not a picture this build hands over"));
    }

    /// `picture`'s 4:4:4 or 4:2:2 planes subsampled to 4:2:0 `format` by libyuv,
    /// in planes this decoder owns and reuses. Both samplings keep chroma at
    /// full height, so the source chroma planes are `height` rows either way.
    private Planes subsample(MemorySegment picture, int width, int height, boolean fourFourFour, PixelFormat format) {
        var bytes = format.bytesPerSample();
        var chromaWidth = (width + 1) / 2;
        var chromaRows = (height + 1) / 2;
        var sourceChromaRows = height;
        var yStride = width * bytes;
        var uvStride = chromaWidth * bytes;
        var ySize = (long) yStride * height;
        var uvSize = (long) uvStride * chromaRows;
        var buffer = converted.ensure(ySize + 2 * uvSize);
        var y = buffer.asSlice(0, ySize);
        var u = buffer.asSlice(ySize, uvSize);
        var v = buffer.asSlice(ySize + uvSize, uvSize);
        var srcYStride = stride(picture, 0);
        var srcUStride = stride(picture, 1);
        var srcVStride = stride(picture, 2);
        var srcY = plane(picture, 0, srcYStride, height);
        var srcU = plane(picture, 1, srcUStride, sourceChromaRows);
        var srcV = plane(picture, 2, srcVStride, sourceChromaRows);
        int result;
        String function;
        if (bytes == 1) {
            function = fourFourFour ? "I444ToI420" : "I422ToI420";
            result = fourFourFour
                    ? yuv.i444ToI420()
                            .call(
                                    srcY,
                                    srcYStride,
                                    srcU,
                                    srcUStride,
                                    srcV,
                                    srcVStride,
                                    y,
                                    yStride,
                                    u,
                                    uvStride,
                                    v,
                                    uvStride,
                                    width,
                                    height)
                    : yuv.i422ToI420()
                            .call(
                                    srcY,
                                    srcYStride,
                                    srcU,
                                    srcUStride,
                                    srcV,
                                    srcVStride,
                                    y,
                                    yStride,
                                    u,
                                    uvStride,
                                    v,
                                    uvStride,
                                    width,
                                    height);
        } else {
            // libyuv steps a 16-bit plane's pointer by its stride, so the strides
            // it is given count samples, not bytes.
            function = fourFourFour ? "I410ToI010" : "I210ToI010";
            result = fourFourFour
                    ? yuv.i410ToI010()
                            .call(
                                    srcY,
                                    srcYStride / 2,
                                    srcU,
                                    srcUStride / 2,
                                    srcV,
                                    srcVStride / 2,
                                    y,
                                    yStride / 2,
                                    u,
                                    uvStride / 2,
                                    v,
                                    uvStride / 2,
                                    width,
                                    height)
                    : yuv.i210ToI010()
                            .call(
                                    srcY,
                                    srcYStride / 2,
                                    srcU,
                                    srcUStride / 2,
                                    srcV,
                                    srcVStride / 2,
                                    y,
                                    yStride / 2,
                                    u,
                                    uvStride / 2,
                                    v,
                                    uvStride / 2,
                                    width,
                                    height);
        }
        if (result != 0) {
            throw new IllegalStateException(function + " refused a " + width + "×" + height + " picture");
        }
        return new Planes(format, y, u, v, yStride, uvStride);
    }

    /// Three 4:2:0 planes and their strides, Y's and the one U and V share.
    private record Planes(
            PixelFormat format, MemorySegment y, MemorySegment u, MemorySegment v, int yStride, int uvStride) {}

    private static int stride(MemorySegment picture, int index) {
        return picture.get(JAVA_INT, IMAGE_STRIDE + index * JAVA_INT.byteSize());
    }

    /// Plane `index` of `picture`, `rows` of `stride` bytes.
    @SuppressWarnings("restricted")
    private static MemorySegment plane(MemorySegment picture, int index, int stride, int rows) {
        return picture.get(ADDRESS, IMAGE_PLANES + index * ADDRESS.byteSize()).reinterpret((long) stride * rows);
    }

    /// What `fmt` samples, for a refusal.
    private String sampling(int fmt) {
        if (fmt == constants.imgFmtI420() || fmt == constants.imgFmtI42016()) {
            return "4:2:0";
        }
        if (fmt == constants.imgFmtI422() || fmt == constants.imgFmtI42216()) {
            return "4:2:2";
        }
        if (fmt == constants.imgFmtI444() || fmt == constants.imgFmtI44416()) {
            return "4:4:4";
        }
        if (fmt == constants.imgFmtI440() || fmt == constants.imgFmtI44016()) {
            return "4:4:0";
        }
        return fmt == constants.imgFmtNv12() ? "NV12" : "format " + fmt;
    }

    /// The matrix the stream signals, else the one a picture of this height is
    /// presumed to have: BT.709 from 720 rows, BT.601 below.
    private VideoFrame.ColorMatrix matrix(int cs, int height) {
        if (cs == constants.csBt709()) {
            return VideoFrame.ColorMatrix.BT709;
        }
        if (cs == constants.csBt2020()) {
            return VideoFrame.ColorMatrix.BT2020;
        }
        if (cs == constants.csBt601() || cs == constants.csSmpte170() || cs == constants.csSmpte240()) {
            return VideoFrame.ColorMatrix.BT601;
        }
        return height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }

    /// `function` answered `result` on `target`: libvpx's name for the code and
    /// the detail the context holds, when it holds one.
    @SuppressWarnings("restricted")
    private MediaException failure(String function, int result, MemorySegment target) {
        var text = vpx.errToString().call(result);
        var reason = text.equals(MemorySegment.NULL)
                ? "error " + result
                : text.reinterpret(256).getString(0);
        var detail = vpx.errorDetail().call(target);
        if (!detail.equals(MemorySegment.NULL)) {
            reason = reason + ": " + detail.reinterpret(1024).getString(0);
        }
        return new MediaException(new MediaError.InvalidData(function + ": " + reason));
    }

    /// Native memory that grows to what is asked of it: a packet's bytes, or
    /// the planes of a converted picture. The decoder's arena frees nothing
    /// before it closes, so a buffer that is outgrown is left in it; each is at
    /// least twice the last, which bounds what is left to the size of the
    /// largest.
    private static final class Scratch {

        private final Arena arena;
        private MemorySegment segment = MemorySegment.NULL;

        Scratch(Arena arena) {
            this.arena = arena;
        }

        /// At least `size` bytes, the same memory as last time when that was
        /// large enough.
        MemorySegment ensure(long size) {
            if (segment.byteSize() < size) {
                segment = arena.allocate(Math.max(size, 2 * segment.byteSize()), 64);
            }
            return segment;
        }
    }
}
