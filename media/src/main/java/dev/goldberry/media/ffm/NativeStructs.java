package dev.goldberry.media.ffm;

import static java.lang.foreign.MemoryLayout.paddingLayout;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.List;

/// The upstream structs the bindings allocate or read, declared by hand.
///
/// Each is the C definition of the pinned version, member for member, with
/// unnamed padding where C pads. A struct the Java side only allocates and never
/// reads into — `vorbis_dsp_state`, `mp3dec_t` — is declared as its size and
/// alignment alone, which is all an allocation needs and all the probe can
/// check of it.
///
/// Five of them hold a C `long`, which is 8 bytes on Linux and macOS and 4 on
/// Windows, so those take the size as a parameter and [#ALL] holds this
/// target's shape; `NativeStructsTest` checks the other shape too.
///
/// Every one is verified against `media-layout.properties` by [LayoutCheck]
/// before the library is used, and a test does the same against the probe
/// output committed for linux-x64, so a mistake here fails the build rather than
/// corrupting memory. The probe and this table list the same fields, in both
/// directions.
public final class NativeStructs {

    private NativeStructs() {}

    // ---- dav1d ---------------------------------------------------------------

    /// `Dav1dPicAllocator`: a cookie and two callbacks. Left as dav1d's default.
    public static final StructLayout DAV1D_PIC_ALLOCATOR = structLayout(
                    ADDRESS.withName("cookie"),
                    ADDRESS.withName("alloc_picture_callback"),
                    ADDRESS.withName("release_picture_callback"))
            .withName("Dav1dPicAllocator");

    /// `Dav1dLogger`: a cookie and a callback. Left null, so dav1d is silent.
    public static final StructLayout DAV1D_LOGGER = structLayout(
                    ADDRESS.withName("cookie"), ADDRESS.withName("callback"))
            .withName("Dav1dLogger");

    /// `Dav1dSettings`: what `dav1d_open` is given, after `dav1d_default_settings`
    /// filled it.
    public static final StructLayout DAV1D_SETTINGS = structLayout(
                    JAVA_INT.withName("n_threads"),
                    JAVA_INT.withName("max_frame_delay"),
                    JAVA_INT.withName("apply_grain"),
                    JAVA_INT.withName("operating_point"),
                    JAVA_INT.withName("all_layers"),
                    JAVA_INT.withName("frame_size_limit"),
                    DAV1D_PIC_ALLOCATOR.withName("allocator"),
                    DAV1D_LOGGER.withName("logger"),
                    JAVA_INT.withName("strict_std_compliance"),
                    JAVA_INT.withName("output_invisible_frames"),
                    JAVA_INT.withName("inloop_filters"),
                    JAVA_INT.withName("decode_frame_type"),
                    sequenceLayout(16, JAVA_BYTE).withName("reserved"))
            .withName("Dav1dSettings");

    /// `Dav1dUserData`: a pointer and its allocation origin, inside the props.
    public static final StructLayout DAV1D_USER_DATA =
            structLayout(ADDRESS.withName("data"), ADDRESS.withName("ref")).withName("Dav1dUserData");

    /// `Dav1dDataProps`: the packet's timestamp and duration, which dav1d copies
    /// from the data to the picture decoded from it.
    public static final StructLayout DAV1D_DATA_PROPS = structLayout(
                    JAVA_LONG.withName("timestamp"),
                    JAVA_LONG.withName("duration"),
                    JAVA_LONG.withName("offset"),
                    NativeTypes.SIZE_T.withName("size"),
                    DAV1D_USER_DATA.withName("user_data"))
            .withName("Dav1dDataProps");

    /// `Dav1dData`: one packet's bytes, wrapped without a copy.
    public static final StructLayout DAV1D_DATA = structLayout(
                    ADDRESS.withName("data"),
                    NativeTypes.SIZE_T.withName("sz"),
                    ADDRESS.withName("ref"),
                    DAV1D_DATA_PROPS.withName("m"))
            .withName("Dav1dData");

    /// `Dav1dPictureParameters`: size, layout and bit depth of a picture.
    public static final StructLayout DAV1D_PICTURE_PARAMETERS = structLayout(
                    JAVA_INT.withName("w"),
                    JAVA_INT.withName("h"),
                    JAVA_INT.withName("layout"),
                    JAVA_INT.withName("bpc"))
            .withName("Dav1dPictureParameters");

    /// `Dav1dPicture`: a decoded picture, borrowed until `dav1d_picture_unref`.
    public static final StructLayout DAV1D_PICTURE = structLayout(
                    ADDRESS.withName("seq_hdr"),
                    ADDRESS.withName("frame_hdr"),
                    sequenceLayout(3, ADDRESS).withName("data"),
                    sequenceLayout(2, JAVA_LONG).withName("stride"),
                    DAV1D_PICTURE_PARAMETERS.withName("p"),
                    DAV1D_DATA_PROPS.withName("m"),
                    ADDRESS.withName("content_light"),
                    ADDRESS.withName("mastering_display"),
                    ADDRESS.withName("itut_t35"),
                    NativeTypes.SIZE_T.withName("n_itut_t35"),
                    sequenceLayout(4, JAVA_LONG).withName("reserved"),
                    ADDRESS.withName("frame_hdr_ref"),
                    ADDRESS.withName("seq_hdr_ref"),
                    ADDRESS.withName("content_light_ref"),
                    ADDRESS.withName("mastering_display_ref"),
                    ADDRESS.withName("itut_t35_ref"),
                    sequenceLayout(4, JAVA_LONG).withName("reserved_ref"),
                    ADDRESS.withName("ref"),
                    ADDRESS.withName("allocator_data"))
            .withName("Dav1dPicture");

    /// `Dav1dSequenceHeader`: read for the colour description at its head; the
    /// rest, 774 bytes of coding parameters, is padding here.
    public static final StructLayout DAV1D_SEQUENCE_HEADER = structLayout(
                    JAVA_BYTE.withName("profile"),
                    paddingLayout(3),
                    JAVA_INT.withName("max_width"),
                    JAVA_INT.withName("max_height"),
                    JAVA_INT.withName("layout"),
                    JAVA_INT.withName("pri"),
                    JAVA_INT.withName("trc"),
                    JAVA_INT.withName("mtrx"),
                    JAVA_INT.withName("chr"),
                    JAVA_BYTE.withName("hbd"),
                    JAVA_BYTE.withName("color_range"),
                    paddingLayout(774))
            .withName("Dav1dSequenceHeader");

    // ---- libvpx --------------------------------------------------------------

    /// `vpx_codec_dec_cfg_t`: the thread count and the size hint.
    public static final StructLayout VPX_CODEC_DEC_CFG = structLayout(
                    JAVA_INT.withName("threads"), JAVA_INT.withName("w"), JAVA_INT.withName("h"))
            .withName("vpx_codec_dec_cfg_t");

    /// `vpx_image_t`: a decoded picture, owned by the codec until its next decode.
    public static final StructLayout VPX_IMAGE = structLayout(
                    JAVA_INT.withName("fmt"),
                    JAVA_INT.withName("cs"),
                    JAVA_INT.withName("range"),
                    JAVA_INT.withName("w"),
                    JAVA_INT.withName("h"),
                    JAVA_INT.withName("bit_depth"),
                    JAVA_INT.withName("d_w"),
                    JAVA_INT.withName("d_h"),
                    JAVA_INT.withName("r_w"),
                    JAVA_INT.withName("r_h"),
                    JAVA_INT.withName("x_chroma_shift"),
                    JAVA_INT.withName("y_chroma_shift"),
                    sequenceLayout(4, ADDRESS).withName("planes"),
                    sequenceLayout(4, JAVA_INT).withName("stride"),
                    JAVA_INT.withName("bps"),
                    paddingLayout(4),
                    ADDRESS.withName("user_priv"),
                    ADDRESS.withName("img_data"),
                    JAVA_INT.withName("img_data_owner"),
                    JAVA_INT.withName("self_allocd"),
                    ADDRESS.withName("fb_priv"))
            .withName("vpx_image_t");

    /// `vpx_codec_stream_info_t`: what `vpx_codec_peek_stream_info` fills.
    public static final StructLayout VPX_CODEC_STREAM_INFO = structLayout(
                    JAVA_INT.withName("sz"), JAVA_INT.withName("w"), JAVA_INT.withName("h"), JAVA_INT.withName("is_kf"))
            .withName("vpx_codec_stream_info_t");

    /// `vpx_codec_ctx_t` where C's `long` is `longSize` bytes: `init_flags` is a
    /// `vpx_codec_flags_t`, which is a `long`, and the union after it is a
    /// pointer, so the struct is 56 bytes either way.
    public static StructLayout vpxCodecCtx(long longSize) {
        var head = List.<MemoryLayout>of(
                ADDRESS.withName("name"),
                ADDRESS.withName("iface"),
                JAVA_INT.withName("err"),
                paddingLayout(4),
                ADDRESS.withName("err_detail"),
                cLong(longSize).withName("init_flags"));
        var tail = List.<MemoryLayout>of(ADDRESS.withName("config"), ADDRESS.withName("priv"));
        return padded("vpx_codec_ctx_t", head, tail, 8);
    }

    /// `vpx_codec_ctx_t` on this target.
    public static final StructLayout VPX_CODEC_CTX = vpxCodecCtx(NativeTypes.C_LONG_SIZE);

    // ---- libogg and libvorbis ------------------------------------------------

    /// `ogg_packet` where C's `long` is `longSize` bytes: `bytes`, `b_o_s` and
    /// `e_o_s` are `long`s, and the two 64-bit counters after them realign.
    public static StructLayout oggPacket(long longSize) {
        var head = List.<MemoryLayout>of(
                ADDRESS.withName("packet"),
                cLong(longSize).withName("bytes"),
                cLong(longSize).withName("b_o_s"),
                cLong(longSize).withName("e_o_s"));
        var tail = List.<MemoryLayout>of(JAVA_LONG.withName("granulepos"), JAVA_LONG.withName("packetno"));
        return padded("ogg_packet", head, tail, 8);
    }

    /// `ogg_packet` on this target.
    public static final StructLayout OGG_PACKET = oggPacket(NativeTypes.C_LONG_SIZE);

    /// `vorbis_info` where C's `long` is `longSize` bytes: the rate and the four
    /// bit rates are `long`s.
    public static StructLayout vorbisInfo(long longSize) {
        var head = List.<MemoryLayout>of(
                JAVA_INT.withName("version"),
                JAVA_INT.withName("channels"),
                cLong(longSize).withName("rate"),
                cLong(longSize).withName("bitrate_upper"),
                cLong(longSize).withName("bitrate_nominal"),
                cLong(longSize).withName("bitrate_lower"),
                cLong(longSize).withName("bitrate_window"));
        var tail = List.<MemoryLayout>of(ADDRESS.withName("codec_setup"));
        return padded("vorbis_info", head, tail, 8);
    }

    /// `vorbis_info` on this target.
    public static final StructLayout VORBIS_INFO = vorbisInfo(NativeTypes.C_LONG_SIZE);

    /// `vorbis_comment`: filled by the header packets, never read.
    public static final StructLayout VORBIS_COMMENT = structLayout(
                    ADDRESS.withName("user_comments"),
                    ADDRESS.withName("comment_lengths"),
                    JAVA_INT.withName("comments"),
                    paddingLayout(4),
                    ADDRESS.withName("vendor"))
            .withName("vorbis_comment");

    /// `vorbis_dsp_state` where C's `long` is `longSize` bytes: 144 bytes with
    /// four 8-byte `long`s, 128 with four 4-byte ones. Allocated, never read.
    public static StructLayout vorbisDspState(long longSize) {
        return opaque("vorbis_dsp_state", longSize == 8 ? 144 : 128, 8);
    }

    /// `vorbis_dsp_state` on this target.
    public static final StructLayout VORBIS_DSP_STATE = vorbisDspState(NativeTypes.C_LONG_SIZE);

    /// `vorbis_block` where C's `long` is `longSize` bytes: 192 bytes with
    /// 8-byte `long`s, 144 with 4-byte ones. Allocated, never read.
    public static StructLayout vorbisBlock(long longSize) {
        return opaque("vorbis_block", longSize == 8 ? 192 : 144, 8);
    }

    /// `vorbis_block` on this target.
    public static final StructLayout VORBIS_BLOCK = vorbisBlock(NativeTypes.C_LONG_SIZE);

    // ---- FLAC ----------------------------------------------------------------

    /// `FLAC__FrameHeader`: what the write callback's frame says about its block.
    /// `number` is a union of a 32-bit frame number and a 64-bit sample number,
    /// modelled as the wider member.
    public static final StructLayout FLAC_FRAME_HEADER = structLayout(
                    JAVA_INT.withName("blocksize"),
                    JAVA_INT.withName("sample_rate"),
                    JAVA_INT.withName("channels"),
                    JAVA_INT.withName("channel_assignment"),
                    JAVA_INT.withName("bits_per_sample"),
                    JAVA_INT.withName("number_type"),
                    JAVA_LONG.withName("number"),
                    JAVA_BYTE.withName("crc"),
                    paddingLayout(7))
            .withName("FLAC__FrameHeader");

    /// `FLAC__Frame`: the header, then eight subframes of 448 bytes nobody here
    /// reads, then the footer's CRC.
    public static final StructLayout FLAC_FRAME = structLayout(
                    FLAC_FRAME_HEADER.withName("header"),
                    sequenceLayout(8, paddingLayout(448).withByteAlignment(8)).withName("subframes"),
                    JAVA_SHORT.withName("footer"),
                    paddingLayout(6))
            .withName("FLAC__Frame");

    // ---- minimp3 -------------------------------------------------------------

    /// `mp3dec_t`: the decoder's whole state, allocated and never read.
    public static final StructLayout MP3DEC = opaque("mp3dec_t", 6668, 4);

    /// `mp3dec_frame_info_t`: what one decoded frame was.
    public static final StructLayout MP3DEC_FRAME_INFO = structLayout(
                    JAVA_INT.withName("frame_bytes"),
                    JAVA_INT.withName("frame_offset"),
                    JAVA_INT.withName("channels"),
                    JAVA_INT.withName("hz"),
                    JAVA_INT.withName("layer"),
                    JAVA_INT.withName("bitrate_kbps"))
            .withName("mp3dec_frame_info_t");

    /// Every struct, for the layout check: this target's shape of each.
    public static final List<StructLayout> ALL = List.of(
            DAV1D_SETTINGS,
            DAV1D_PIC_ALLOCATOR,
            DAV1D_LOGGER,
            DAV1D_USER_DATA,
            DAV1D_DATA_PROPS,
            DAV1D_DATA,
            DAV1D_PICTURE_PARAMETERS,
            DAV1D_PICTURE,
            DAV1D_SEQUENCE_HEADER,
            VPX_CODEC_CTX,
            VPX_CODEC_DEC_CFG,
            VPX_IMAGE,
            VPX_CODEC_STREAM_INFO,
            OGG_PACKET,
            VORBIS_INFO,
            VORBIS_COMMENT,
            VORBIS_DSP_STATE,
            VORBIS_BLOCK,
            FLAC_FRAME_HEADER,
            FLAC_FRAME,
            MP3DEC,
            MP3DEC_FRAME_INFO);

    /// Every struct where C's `long` is `longSize` bytes, for the test that
    /// checks the other target's shapes.
    public static List<StructLayout> all(long longSize) {
        return List.of(
                DAV1D_SETTINGS,
                DAV1D_PIC_ALLOCATOR,
                DAV1D_LOGGER,
                DAV1D_USER_DATA,
                DAV1D_DATA_PROPS,
                DAV1D_DATA,
                DAV1D_PICTURE_PARAMETERS,
                DAV1D_PICTURE,
                DAV1D_SEQUENCE_HEADER,
                vpxCodecCtx(longSize),
                VPX_CODEC_DEC_CFG,
                VPX_IMAGE,
                VPX_CODEC_STREAM_INFO,
                oggPacket(longSize),
                vorbisInfo(longSize),
                VORBIS_COMMENT,
                vorbisDspState(longSize),
                vorbisBlock(longSize),
                FLAC_FRAME_HEADER,
                FLAC_FRAME,
                MP3DEC,
                MP3DEC_FRAME_INFO);
    }

    /// A C `long` of `longSize` bytes.
    private static MemoryLayout cLong(long longSize) {
        return longSize == 8 ? JAVA_LONG : JAVA_INT;
    }

    /// A struct that is only ever allocated: `size` bytes at `alignment`.
    private static StructLayout opaque(String name, long size, long alignment) {
        return structLayout(paddingLayout(size).withByteAlignment(alignment)).withName(name);
    }

    /// `head` then `tail`, with the padding C inserts so that `tail`'s first
    /// member starts at a multiple of `tailAlignment`, and trailing padding to
    /// the struct's own alignment.
    private static StructLayout padded(
            String name, List<MemoryLayout> head, List<MemoryLayout> tail, long tailAlignment) {
        var members = new java.util.ArrayList<MemoryLayout>(head);
        var offset = head.stream().mapToLong(MemoryLayout::byteSize).sum();
        var gap = (tailAlignment - offset % tailAlignment) % tailAlignment;
        if (gap > 0) {
            members.add(paddingLayout(gap));
        }
        members.addAll(tail);
        var size = members.stream().mapToLong(MemoryLayout::byteSize).sum();
        var trailing = (tailAlignment - size % tailAlignment) % tailAlignment;
        if (trailing > 0) {
            members.add(paddingLayout(trailing));
        }
        return structLayout(members.toArray(MemoryLayout[]::new)).withName(name);
    }
}
