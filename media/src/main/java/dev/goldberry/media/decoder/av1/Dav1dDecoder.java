package dev.goldberry.media.decoder.av1;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Frame;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PictureLimit;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.NativeConstants;
import dev.goldberry.media.ffm.NativeStructs;
import dev.goldberry.media.ffm.calls.Dav1dCalls;

/// An AV1 decoder over dav1d: the send-data/get-picture loop, one picture
/// borrowed at a time.
///
/// A packet's bytes are copied into a buffer dav1d owns (`dav1d_data_create`),
/// stamped with the packet's time, and sent. dav1d takes the data when it has
/// room and asks for a picture to be fetched first when it has not; the copy
/// is kept across that, so [#send] never refuses a packet it has already
/// copied. The picture dav1d hands back is the frame's memory until the next
/// [#receive], [#flush] or [#close], which is the SPI's borrowing rule, and is
/// released to dav1d then.
///
/// Threads: dav1d's default, one per core, with its default frame delay. That
/// changes when pictures arrive and never what is in them: AV1 decoding is
/// bit-exact by specification, and `Dav1dDecoderTest` holds every picture to
/// the checksums FFmpeg's dav1d produced from the same clips.
///
/// What is handed over: 8-bit 4:2:0 as [PixelFormat#I420], 10-bit 4:2:0 as
/// [PixelFormat#I010]. 4:2:2, 4:4:4, monochrome and 12-bit pictures are
/// refused, by name, until the frame contract names them.
public final class Dav1dDecoder implements Decoder {

    private static final long SETTINGS_N_THREADS = NativeStructs.DAV1D_SETTINGS.byteOffset(groupElement("n_threads"));
    /// `DAV1D_ERR(ERANGE)`, which dav1d answers only for a frame past its frame
    /// size limit. `ERANGE` is 34 in glibc, in macOS and in the Windows C
    /// runtimes alike, so it is not one of the probed constants.
    private static final int ERR_RANGE = -34;

    private static final long SETTINGS_FRAME_SIZE_LIMIT =
            NativeStructs.DAV1D_SETTINGS.byteOffset(groupElement("frame_size_limit"));
    private static final long DATA_SZ = NativeStructs.DAV1D_DATA.byteOffset(groupElement("sz"));
    private static final long DATA_TIMESTAMP =
            NativeStructs.DAV1D_DATA.byteOffset(groupElement("m"), groupElement("timestamp"));
    private static final long PICTURE_SEQ_HDR = NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("seq_hdr"));
    private static final long PICTURE_DATA = NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("data"));
    private static final long PICTURE_STRIDE = NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("stride"));
    private static final long PICTURE_W = NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("p"), groupElement("w"));
    private static final long PICTURE_H = NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("p"), groupElement("h"));
    private static final long PICTURE_LAYOUT =
            NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("p"), groupElement("layout"));
    private static final long PICTURE_BPC =
            NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("p"), groupElement("bpc"));
    private static final long PICTURE_TIMESTAMP =
            NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("m"), groupElement("timestamp"));
    private static final long PICTURE_CONTENT_LIGHT =
            NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("content_light"));
    private static final long PICTURE_MASTERING_DISPLAY =
            NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("mastering_display"));
    private static final long CONTENT_LIGHT_MAX_CLL =
            NativeStructs.DAV1D_CONTENT_LIGHT_LEVEL.byteOffset(groupElement("max_content_light_level"));
    private static final long MASTERING_MAX_LUMINANCE =
            NativeStructs.DAV1D_MASTERING_DISPLAY.byteOffset(groupElement("max_luminance"));
    private static final long SEQ_PRI = NativeStructs.DAV1D_SEQUENCE_HEADER.byteOffset(groupElement("pri"));
    private static final long SEQ_TRC = NativeStructs.DAV1D_SEQUENCE_HEADER.byteOffset(groupElement("trc"));
    private static final long SEQ_MTRX = NativeStructs.DAV1D_SEQUENCE_HEADER.byteOffset(groupElement("mtrx"));
    private static final long SEQ_COLOR_RANGE =
            NativeStructs.DAV1D_SEQUENCE_HEADER.byteOffset(groupElement("color_range"));
    private static final long SEQ_SIZE = NativeStructs.DAV1D_SEQUENCE_HEADER.byteSize();

    /// How many send-then-fetch rounds one [#receive] runs before it answers
    /// that it needs input, should dav1d refuse data and have no picture at
    /// the same time, which its threading can do for a moment.
    private static final int ROUNDS = 64;

    private final Dav1dCalls dav1d;
    private final NativeConstants.Dav1d constants;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment contextHolder;
    private final MemorySegment data;
    private final MemorySegment picture;
    private boolean dataPending;
    private boolean pictureHeld;
    private boolean ended;
    private boolean closed;

    /// Opens a decoder for `request`, an AV1 track.
    ///
    /// @throws MediaException when dav1d refuses to open
    public Dav1dDecoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.dav1d = library.dav1d();
        this.constants = library.constants().dav1d();
        try {
            var settings = arena.allocate(NativeStructs.DAV1D_SETTINGS);
            dav1d.defaultSettings().call(settings);
            settings.set(JAVA_INT, SETTINGS_N_THREADS, 0);
            // dav1d refuses a frame of more pixels before it allocates one; the
            // sides are held in Java, as the frame comes out.
            settings.set(JAVA_INT, SETTINGS_FRAME_SIZE_LIMIT, (int) PictureLimit.MAX_PIXELS);
            this.contextHolder = arena.allocate(ADDRESS);
            var opened = dav1d.open().call(contextHolder, settings);
            if (opened < 0) {
                throw failure("dav1d_open", opened);
            }
            this.data = arena.allocate(NativeStructs.DAV1D_DATA);
            this.picture = arena.allocate(NativeStructs.DAV1D_PICTURE);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    private MemorySegment context() {
        return contextHolder.get(ADDRESS, 0);
    }

    @Override
    @SuppressWarnings("restricted")
    public boolean send(Packet packet) {
        requireOpen();
        if (ended) {
            throw new IllegalStateException("a packet after the end");
        }
        if (dataPending && !pushPending()) {
            return false;
        }
        var bytes = packet.data();
        if (bytes.byteSize() == 0) {
            return true;
        }
        var buffer = dav1d.dataCreate().call(data, bytes.byteSize());
        if (buffer.equals(MemorySegment.NULL)) {
            throw new OutOfMemoryError("dav1d_data_create failed for " + bytes.byteSize() + " bytes");
        }
        MemorySegment.copy(bytes, 0, buffer.reinterpret(bytes.byteSize()), 0, bytes.byteSize());
        var pts = packet.ptsNanos();
        data.set(JAVA_LONG, DATA_TIMESTAMP, pts == Frame.NO_PTS ? Long.MIN_VALUE : pts);
        dataPending = true;
        pushPending();
        return true;
    }

    /// Offers the pending data to dav1d.
    ///
    /// @return whether dav1d took all of it
    private boolean pushPending() {
        var result = dav1d.sendData().call(context(), data);
        if (result == 0 || data.get(JAVA_LONG, DATA_SZ) == 0) {
            if (data.get(JAVA_LONG, DATA_SZ) != 0) {
                dav1d.dataUnref().call(data);
            }
            dataPending = false;
            return true;
        }
        if (result == constants.errAgain()) {
            return false;
        }
        throw failure("dav1d_send_data", result);
    }

    @Override
    public void sendEnd() {
        requireOpen();
        ended = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        releasePicture();
        for (var round = 0; round < ROUNDS; round++) {
            var pushed = !dataPending || pushPending();
            var result = dav1d.getPicture().call(context(), picture);
            if (result == 0) {
                pictureHeld = true;
                return new Received.Decoded(frame());
            }
            if (result != constants.errAgain()) {
                throw failure("dav1d_get_picture", result);
            }
            if (pushed) {
                return ended && !dataPending ? Received.ENDED : Received.NEEDS_INPUT;
            }
        }
        return Received.NEEDS_INPUT;
    }

    @Override
    public void flush() {
        requireOpen();
        releasePicture();
        releaseData();
        dav1d.flush().call(context());
        ended = false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        releasePicture();
        releaseData();
        dav1d.close().call(contextHolder);
        arena.close();
    }

    private void releasePicture() {
        if (pictureHeld) {
            pictureHeld = false;
            dav1d.pictureUnref().call(picture);
        }
    }

    private void releaseData() {
        if (dataPending) {
            dataPending = false;
            dav1d.dataUnref().call(data);
        }
    }

    /// The held picture as the SPI's frame.
    @SuppressWarnings("restricted")
    private VideoFrame frame() {
        var width = picture.get(JAVA_INT, PICTURE_W);
        var height = picture.get(JAVA_INT, PICTURE_H);
        PictureLimit.require(width, height, "an AV1 picture");
        var layout = picture.get(JAVA_INT, PICTURE_LAYOUT);
        var bpc = picture.get(JAVA_INT, PICTURE_BPC);
        var format = format(layout, bpc);
        var lumaStride = Math.toIntExact(picture.get(JAVA_LONG, PICTURE_STRIDE));
        var chromaStride = Math.toIntExact(picture.get(JAVA_LONG, PICTURE_STRIDE + JAVA_LONG.byteSize()));
        var planes = List.of(
                plane(0, lumaStride, format.planeRows(0, height)),
                plane(1, chromaStride, format.planeRows(1, height)),
                plane(2, chromaStride, format.planeRows(2, height)));
        var sequence = picture.get(ADDRESS, PICTURE_SEQ_HDR).reinterpret(SEQ_SIZE);
        var matrix = matrix(sequence.get(JAVA_INT, SEQ_MTRX), height);
        var fullRange = sequence.get(JAVA_BYTE, SEQ_COLOR_RANGE) != 0;
        var timestamp = picture.get(JAVA_LONG, PICTURE_TIMESTAMP);
        return new VideoFrame(
                format,
                width,
                height,
                planes,
                List.of(lumaStride, chromaStride, chromaStride),
                matrix,
                fullRange,
                timestamp == Long.MIN_VALUE ? Frame.NO_PTS : timestamp,
                colour(sequence));
    }

    /// The sequence header's primaries and transfer function, which dav1d
    /// numbers as AV1 does and AV1 as H.273 does, and the light levels of the
    /// picture's HDR metadata OBUs: `MaxCLL`, and the mastering display's peak
    /// in 24.8 fixed point.
    @SuppressWarnings("restricted")
    private ColorDescription colour(MemorySegment sequence) {
        var described = ColorDescription.fromH273(sequence.get(JAVA_INT, SEQ_PRI), sequence.get(JAVA_INT, SEQ_TRC));
        var masteringPeak = 0;
        var mastering = picture.get(ADDRESS, PICTURE_MASTERING_DISPLAY);
        if (!MemorySegment.NULL.equals(mastering)) {
            var fixed = mastering
                            .reinterpret(NativeStructs.DAV1D_MASTERING_DISPLAY.byteSize())
                            .get(JAVA_INT, MASTERING_MAX_LUMINANCE)
                    & 0xFFFF_FFFFL;
            masteringPeak = (int) Math.min(100_000, fixed >> 8);
        }
        var contentPeak = 0;
        var light = picture.get(ADDRESS, PICTURE_CONTENT_LIGHT);
        if (!MemorySegment.NULL.equals(light)) {
            contentPeak = Short.toUnsignedInt(light.reinterpret(NativeStructs.DAV1D_CONTENT_LIGHT_LEVEL.byteSize())
                    .get(JAVA_SHORT, CONTENT_LIGHT_MAX_CLL));
        }
        return described.withLightLevels(masteringPeak, contentPeak);
    }

    @SuppressWarnings("restricted")
    private MemorySegment plane(int index, int stride, int rows) {
        var address = picture.get(ADDRESS, PICTURE_DATA + index * ADDRESS.byteSize());
        return address.reinterpret((long) stride * rows);
    }

    private PixelFormat format(int layout, int bpc) {
        if (layout == constants.layoutI420()) {
            if (bpc == 8) {
                return PixelFormat.I420;
            }
            if (bpc == 10) {
                return PixelFormat.I010;
            }
        }
        var name = layout == constants.layoutI400()
                ? "monochrome"
                : layout == constants.layoutI422() ? "4:2:2" : layout == constants.layoutI444() ? "4:4:4" : "4:2:0";
        throw new MediaException(
                new MediaError.InvalidData(bpc + "-bit " + name + " AV1 is not a picture this build hands over"));
    }

    /// The matrix the sequence header signals, else the one a picture of this
    /// height is presumed to have: BT.709 from 720 rows, BT.601 below.
    private VideoFrame.ColorMatrix matrix(int mtrx, int height) {
        if (mtrx == constants.mcBt709()) {
            return VideoFrame.ColorMatrix.BT709;
        }
        if (mtrx == constants.mcBt2020Ncl() || mtrx == constants.mcBt2020Cl()) {
            return VideoFrame.ColorMatrix.BT2020;
        }
        if (mtrx == constants.mcBt601() || mtrx == constants.mcBt470bg()) {
            return VideoFrame.ColorMatrix.BT601;
        }
        return height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }

    private MediaException failure(String function, int result) {
        var reason = result == constants.errInval()
                ? "invalid data"
                : result == constants.errNoMem()
                        ? "out of memory"
                        : result == ERR_RANGE
                                ? "a frame past the largest picture decoded, " + PictureLimit.MAX_PIXELS + " pixels"
                                : "error " + result;
        return new MediaException(new MediaError.InvalidData(function + ": " + reason));
    }

    /// Where a sequence's picture data starts for `index`, for a test that
    /// checks the layout arithmetic.
    static long dataOffset(int index) {
        return NativeStructs.DAV1D_PICTURE.byteOffset(groupElement("data"), sequenceElement(index));
    }
}
