package dev.goldberry.media.decoder.theora;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.bitstream.XiphLacing;
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
import dev.goldberry.media.ffm.calls.TheoraCalls;
import dev.goldberry.media.ffm.calls.YuvCalls;

/// A Theora decoder over libtheora: one frame a packet, one picture a frame.
///
/// The record a container hands over is the three header packets Xiph-laced,
/// as Matroska's `CodecPrivate` holds them and the Ogg reader laces them, the
/// same arrangement as Vorbis's; each goes through `th_decode_headerin` as an
/// `ogg_packet` built here. Theora has no reordering and no delay, so each
/// packet decoded is a picture to fetch, and [#send] refuses the next packet
/// until it has been: a frame handed out stays valid until the next call, as
/// the SPI promises.
///
/// What is handed over is the picture region the identification header
/// states, not the whole coded frame, whose sides are multiples of 16. Theora
/// writes that region's offset from the bottom of the frame and codes its rows
/// bottom up; libtheora turns both round, and answers top-down planes at a
/// positive stride with the offset counted from the top, so the crop here is
/// a slice of each plane. 4:2:0 is lent from libtheora's own planes; 4:2:2 and
/// 4:4:4 are subsampled by libyuv into planes this decoder owns, since the
/// frame contract names 4:2:0 alone. The colour is BT.601 in limited range
/// whatever the header says: Theora's two colour spaces, Rec. 470M and
/// Rec. 470BG, differ in their primaries and share that matrix, and an
/// unspecified one is an SD picture's too.
///
/// An empty packet, or a frame that codes no block, repeats the picture before
/// it at its own time: the decoder answers `TH_DUPFRAME` and the last picture
/// is handed over again. One before any picture was decoded hands over nothing.
/// A packet libtheora refuses is skipped, as FFmpeg's decoder skips it, and the
/// stream goes on from the next.
public final class TheoraDecoder implements Decoder {

    private static final Logger LOG = Logs.of(TheoraDecoder.class);

    private static final long PACKET_PACKET = NativeStructs.OGG_PACKET.byteOffset(groupElement("packet"));
    private static final long PACKET_BYTES = NativeStructs.OGG_PACKET.byteOffset(groupElement("bytes"));
    private static final long PACKET_BOS = NativeStructs.OGG_PACKET.byteOffset(groupElement("b_o_s"));
    private static final long PACKET_EOS = NativeStructs.OGG_PACKET.byteOffset(groupElement("e_o_s"));
    private static final long PACKET_GRANULEPOS = NativeStructs.OGG_PACKET.byteOffset(groupElement("granulepos"));
    private static final long PACKET_PACKETNO = NativeStructs.OGG_PACKET.byteOffset(groupElement("packetno"));
    /// `bytes`, `b_o_s` and `e_o_s` are C `long`s: this wide on this target.
    private static final long PACKET_LONG_SIZE =
            NativeStructs.OGG_PACKET.select(groupElement("bytes")).byteSize();

    private static final long INFO_FRAME_WIDTH = NativeStructs.TH_INFO.byteOffset(groupElement("frame_width"));
    private static final long INFO_FRAME_HEIGHT = NativeStructs.TH_INFO.byteOffset(groupElement("frame_height"));
    private static final long INFO_PIC_WIDTH = NativeStructs.TH_INFO.byteOffset(groupElement("pic_width"));
    private static final long INFO_PIC_HEIGHT = NativeStructs.TH_INFO.byteOffset(groupElement("pic_height"));
    private static final long INFO_PIC_X = NativeStructs.TH_INFO.byteOffset(groupElement("pic_x"));
    private static final long INFO_PIC_Y = NativeStructs.TH_INFO.byteOffset(groupElement("pic_y"));
    private static final long INFO_PIXEL_FMT = NativeStructs.TH_INFO.byteOffset(groupElement("pixel_fmt"));

    private static final long PLANE_SIZE = NativeStructs.TH_IMG_PLANE.byteSize();
    private static final long PLANE_HEIGHT = NativeStructs.TH_IMG_PLANE.byteOffset(groupElement("height"));
    private static final long PLANE_STRIDE = NativeStructs.TH_IMG_PLANE.byteOffset(groupElement("stride"));
    private static final long PLANE_DATA = NativeStructs.TH_IMG_PLANE.byteOffset(groupElement("data"));

    private final TheoraCalls theora;
    private final YuvCalls yuv;
    private final NativeConstants.Theora constants;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment info;
    private final MemorySegment comment;
    private final MemorySegment packet;
    private final MemorySegment buffer;
    private final MemorySegment context;
    private final Region region;
    private final Sampling sampling;
    private final Scratch packetBytes = new Scratch(arena);
    private final Scratch converted = new Scratch(arena);
    private long packetNumber;
    /// Whether a picture was decoded since the open or the last flush: what a
    /// repeated frame repeats.
    private boolean decoded;
    /// Whether the last packet's picture is still to be fetched.
    private boolean picturePending;
    private long pts = Frame.NO_PTS;
    private boolean ended;
    private boolean closed;

    /// Where the picture sits in the coded frame, counted from its top-left
    /// corner, and how large it is.
    private record Region(int x, int y, int width, int height) {}

    /// How the chroma planes are subsampled: by 2 across for 4:2:0 and 4:2:2,
    /// by 2 down for 4:2:0.
    private enum Sampling {
        YUV420(1, 1),
        YUV422(1, 0),
        YUV444(0, 0);

        final int shiftX;
        final int shiftY;

        Sampling(int shiftX, int shiftY) {
            this.shiftX = shiftX;
            this.shiftY = shiftY;
        }
    }

    /// Opens a decoder for `request`, a Theora track.
    ///
    /// @throws MediaException [MediaError.InvalidData] when the record is not
    ///                        three Theora headers libtheora accepts, or states
    ///                        a frame past the largest picture decoded
    public TheoraDecoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.theora = library.theora();
        this.yuv = library.yuv();
        this.constants = library.constants().theora();
        this.info = arena.allocate(NativeStructs.TH_INFO);
        this.comment = arena.allocate(NativeStructs.TH_COMMENT);
        this.packet = arena.allocate(NativeStructs.OGG_PACKET);
        this.buffer = arena.allocate(NativeStructs.TH_YCBCR_BUFFER);
        var setupOut = arena.allocate(ADDRESS);
        theora.infoInit().call(info);
        theora.commentInit().call(comment);
        try {
            var headers = headers(request);
            for (var i = 0; i < headers.size(); i++) {
                var header = headers.get(i);
                fill(arena.allocateFrom(JAVA_BYTE, header), header.length, i == 0, i);
                var code = theora.decodeHeaderin().call(info, comment, setupOut, packet);
                if (code <= 0) {
                    throw new MediaException(
                            new MediaError.InvalidData("th_decode_headerin refused header " + i + ": " + code));
                }
            }
            var frameWidth = info.get(JAVA_INT, INFO_FRAME_WIDTH);
            var frameHeight = info.get(JAVA_INT, INFO_FRAME_HEIGHT);
            // libtheora allocates its reference frames at the coded size, so the
            // cap is held there, before it does.
            PictureLimit.require(frameWidth, frameHeight, "a Theora frame");
            this.region = new Region(
                    info.get(JAVA_INT, INFO_PIC_X),
                    info.get(JAVA_INT, INFO_PIC_Y),
                    info.get(JAVA_INT, INFO_PIC_WIDTH),
                    info.get(JAVA_INT, INFO_PIC_HEIGHT));
            if (region.width() <= 0 || region.height() <= 0) {
                throw new MediaException(
                        new MediaError.InvalidData("a Theora picture of " + region.width() + "×" + region.height()));
            }
            this.sampling = sampling(info.get(JAVA_INT, INFO_PIXEL_FMT));
            var setup = setupOut.get(ADDRESS, 0);
            this.context = theora.decodeAlloc().call(info, setup);
            theora.setupFree().call(setup);
            setupOut.set(ADDRESS, 0, MemorySegment.NULL);
            if (context.equals(MemorySegment.NULL)) {
                throw new MediaException(new MediaError.InvalidData("th_decode_alloc refused the headers"));
            }
            this.packetNumber = headers.size();
        } catch (RuntimeException | Error e) {
            theora.setupFree().call(setupOut.get(ADDRESS, 0));
            theora.commentClear().call(comment);
            theora.infoClear().call(info);
            arena.close();
            throw e;
        }
    }

    /// The three headers `request`'s record laces.
    private static List<byte[]> headers(DecoderRequest request) {
        var extradata = request.extradata();
        if (extradata.byteSize() == 0) {
            throw new MediaException(new MediaError.InvalidData("a Theora track without its headers"));
        }
        List<byte[]> headers;
        try {
            headers = XiphLacing.split(extradata.toArray(JAVA_BYTE));
        } catch (IllegalArgumentException e) {
            throw new MediaException(new MediaError.InvalidData(String.valueOf(e.getMessage())), e);
        }
        if (headers.size() != 3) {
            throw new MediaException(
                    new MediaError.InvalidData("a Theora record of " + headers.size() + " packets, not three"));
        }
        return headers;
    }

    private Sampling sampling(int pixelFormat) {
        if (pixelFormat == constants.pf420()) {
            return Sampling.YUV420;
        }
        if (pixelFormat == constants.pf422()) {
            return Sampling.YUV422;
        }
        if (pixelFormat == constants.pf444()) {
            return Sampling.YUV444;
        }
        throw new MediaException(new MediaError.InvalidData("Theora pixel format " + pixelFormat));
    }

    @Override
    public boolean send(Packet packet) {
        requireOpen();
        if (ended) {
            throw new IllegalStateException("a packet after the end");
        }
        if (picturePending) {
            return false;
        }
        var data = packet.data();
        var length = Math.toIntExact(data.byteSize());
        var bytes = packetBytes.ensure(Math.max(length, 1));
        MemorySegment.copy(data, 0, bytes, 0, length);
        fill(bytes, length, false, packetNumber++);
        var code = theora.decodePacketin().call(context, this.packet, MemorySegment.NULL);
        if (code == 0 || (code == constants.duplicateFrame() && decoded)) {
            decoded = true;
            picturePending = true;
            pts = packet.ptsNanos();
        } else if (code < 0) {
            LOG.debug("th_decode_packetin refused a packet of {} bytes: {}", length, code);
        }
        return true;
    }

    @Override
    public void sendEnd() {
        requireOpen();
        ended = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        if (picturePending) {
            picturePending = false;
            return new Received.Decoded(frame());
        }
        return ended ? Received.ENDED : Received.NEEDS_INPUT;
    }

    /// Forgets the picture to be fetched, the picture a repeat would repeat,
    /// and the end. libtheora has no flush and needs none: the Engine seeks to a
    /// keyframe, which refers to nothing before it.
    @Override
    public void flush() {
        requireOpen();
        picturePending = false;
        decoded = false;
        ended = false;
        pts = Frame.NO_PTS;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        theora.decodeFree().call(context);
        theora.commentClear().call(comment);
        theora.infoClear().call(info);
        arena.close();
    }

    /// The last picture, cropped to the region and in 4:2:0.
    private VideoFrame frame() {
        var code = theora.decodeYcbcrOut().call(context, buffer);
        if (code != 0) {
            throw new MediaException(new MediaError.InvalidData("th_decode_ycbcr_out: " + code));
        }
        var lumaStride = stride(0);
        var chromaStride = stride(1);
        var y = plane(0, lumaStride, region.y(), region.x());
        var u = plane(1, chromaStride, region.y() >> sampling.shiftY, region.x() >> sampling.shiftX);
        var v = plane(2, chromaStride, region.y() >> sampling.shiftY, region.x() >> sampling.shiftX);
        if (sampling == Sampling.YUV420) {
            return picture(List.of(y, u, v), List.of(lumaStride, chromaStride, chromaStride));
        }
        var width = region.width();
        var height = region.height();
        var chromaWidth = (width + 1) / 2;
        var chromaRows = (height + 1) / 2;
        var ySize = (long) width * height;
        var uvSize = (long) chromaWidth * chromaRows;
        var out = converted.ensure(ySize + 2 * uvSize);
        var outY = out.asSlice(0, ySize);
        var outU = out.asSlice(ySize, uvSize);
        var outV = out.asSlice(ySize + uvSize, uvSize);
        // The converted planes are packed: a row's stride is its width.
        var outStride = width;
        var outChromaStride = chromaWidth;
        int result;
        String function;
        if (sampling == Sampling.YUV444) {
            function = "I444ToI420";
            result = yuv.i444ToI420()
                    .call(
                            y,
                            lumaStride,
                            u,
                            chromaStride,
                            v,
                            chromaStride,
                            outY,
                            outStride,
                            outU,
                            outChromaStride,
                            outV,
                            outChromaStride,
                            width,
                            height);
        } else {
            function = "I422ToI420";
            result = yuv.i422ToI420()
                    .call(
                            y,
                            lumaStride,
                            u,
                            chromaStride,
                            v,
                            chromaStride,
                            outY,
                            outStride,
                            outU,
                            outChromaStride,
                            outV,
                            outChromaStride,
                            width,
                            height);
        }
        if (result != 0) {
            throw new IllegalStateException(function + " refused a " + width + "×" + height + " picture");
        }
        return picture(List.of(outY, outU, outV), List.of(width, chromaWidth, chromaWidth));
    }

    private VideoFrame picture(List<MemorySegment> planes, List<Integer> strides) {
        return new VideoFrame(
                PixelFormat.I420,
                region.width(),
                region.height(),
                planes,
                strides,
                VideoFrame.ColorMatrix.BT601,
                false,
                pts);
    }

    /// The stride of plane `index` of the last picture, which libtheora keeps
    /// positive: its rows are top down.
    private int stride(int index) {
        var stride = buffer.get(JAVA_INT, index * PLANE_SIZE + PLANE_STRIDE);
        if (stride <= 0) {
            throw new MediaException(new MediaError.InvalidData("a Theora plane of stride " + stride));
        }
        return stride;
    }

    /// Plane `index` of the last picture from row `row` and column `column` on:
    /// where the picture region starts in it.
    @SuppressWarnings("restricted")
    private MemorySegment plane(int index, int stride, int row, int column) {
        var height = buffer.get(JAVA_INT, index * PLANE_SIZE + PLANE_HEIGHT);
        var whole = buffer.get(ADDRESS, index * PLANE_SIZE + PLANE_DATA).reinterpret((long) stride * height);
        return whole.asSlice((long) row * stride + column);
    }

    /// Writes the `ogg_packet` libtheora reads: `data`'s first `length` bytes,
    /// no granule position, numbered.
    private void fill(MemorySegment data, int length, boolean first, long number) {
        packet.set(ADDRESS, PACKET_PACKET, data);
        setCLong(packet, PACKET_BYTES, length);
        setCLong(packet, PACKET_BOS, first ? 1 : 0);
        setCLong(packet, PACKET_EOS, 0);
        packet.set(JAVA_LONG, PACKET_GRANULEPOS, -1);
        packet.set(JAVA_LONG, PACKET_PACKETNO, number);
    }

    private static void setCLong(MemorySegment struct, long offset, long value) {
        if (PACKET_LONG_SIZE == 8) {
            struct.set(JAVA_LONG, offset, value);
        } else {
            struct.set(JAVA_INT, offset, Math.toIntExact(value));
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
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
