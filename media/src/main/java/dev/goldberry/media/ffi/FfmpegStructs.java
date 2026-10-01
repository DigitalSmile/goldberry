package dev.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.paddingLayout;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.StructLayout;
import java.util.List;

/// The FFmpeg structs the Engine reads fields of, declared by hand.
///
/// `docs/goldberry-media.md` §2's table, written as layouts. Only the fields in
/// that table are named. Everything between them is unnamed padding, so a struct's
/// private and deprecated fields never appear in Java. Each layout is named for
/// its C type, which is the key [FfmpegLayoutCheck] matches against the probe.
///
/// The offsets are FFmpeg 8.1's (avformat 62, avcodec 62, avutil 60), as the probe
/// reports them. They hold on all four targets, because FFmpeg's public structs
/// are made of `int`, `int64_t`, pointers and enums, and every target is LP64 or
/// LLP64 with 64-bit pointers. The check still runs per target, at build time and
/// at start-up. A layout that is wrong somewhere refuses to load there rather
/// than read the wrong bytes.
///
/// **Moving the FFmpeg pin means re-reading this file**, and the layout test
/// fails until it has been.
public final class FfmpegStructs {

    private FfmpegStructs() {}

    /// `AVRational`: a fraction, passed and embedded by value.
    public static final StructLayout AV_RATIONAL =
            structLayout(JAVA_INT.withName("num"), JAVA_INT.withName("den")).withName("AVRational");

    /// `AVChannelLayout`: channel order, channel count, and a union whose `mask`
    /// member is the one read. The union is modelled as that member, which is also
    /// its size.
    public static final StructLayout AV_CHANNEL_LAYOUT = structLayout(
                    JAVA_INT.withName("order"),
                    JAVA_INT.withName("nb_channels"),
                    JAVA_LONG.withName("u.mask"),
                    paddingLayout(8)) // void *opaque
            .withName("AVChannelLayout");

    /// `AVFormatContext`: the demuxer's state.
    public static final StructLayout AV_FORMAT_CONTEXT = structLayout(
                    paddingLayout(32), // av_class, iformat, oformat, priv_data
                    ADDRESS.withName("pb"),
                    paddingLayout(4), // ctx_flags
                    JAVA_INT.withName("nb_streams"),
                    ADDRESS.withName("streams"),
                    paddingLayout(48), // nb_stream_groups .. start_time
                    JAVA_LONG.withName("duration"),
                    paddingLayout(16), // bit_rate, packet_size, max_delay
                    JAVA_INT.withName("flags"),
                    paddingLayout(348))
            .withName("AVFormatContext");

    /// `AVIOContext`: read only to free its buffer, which `avio_alloc_context` is
    /// allowed to have replaced.
    public static final StructLayout AV_IO_CONTEXT = structLayout(
                    paddingLayout(8), // av_class
                    ADDRESS.withName("buffer"),
                    paddingLayout(192))
            .withName("AVIOContext");

    /// `AVPacket`: one compressed frame. Embedded by value in `AVStream` as the
    /// attached picture.
    public static final StructLayout AV_PACKET = structLayout(
                    paddingLayout(8), // buf
                    JAVA_LONG.withName("pts"),
                    JAVA_LONG.withName("dts"),
                    ADDRESS.withName("data"),
                    JAVA_INT.withName("size"),
                    JAVA_INT.withName("stream_index"),
                    JAVA_INT.withName("flags"),
                    paddingLayout(20), // side_data, side_data_elems
                    JAVA_LONG.withName("duration"),
                    paddingLayout(32)) // pos, opaque, opaque_ref, time_base
            .withName("AVPacket");

    /// `AVCodec`: one decoder or encoder this build contains. Read only for the
    /// public fields at its head, to list what the build decodes.
    public static final StructLayout AV_CODEC = structLayout(
                    ADDRESS.withName("name"),
                    paddingLayout(8), // long_name
                    JAVA_INT.withName("type"),
                    JAVA_INT.withName("id"),
                    paddingLayout(72))
            .withName("AVCodec");

    /// `AVInputFormat`: one demuxer this build contains. Read only for its name.
    public static final StructLayout AV_INPUT_FORMAT =
            structLayout(ADDRESS.withName("name"), paddingLayout(48)).withName("AVInputFormat");

    /// `AVStream`: one track as the demuxer sees it.
    public static final StructLayout AV_STREAM = structLayout(
                    paddingLayout(8), // av_class
                    JAVA_INT.withName("index"),
                    paddingLayout(4), // id
                    ADDRESS.withName("codecpar"),
                    paddingLayout(8), // priv_data
                    AV_RATIONAL.withName("time_base"),
                    paddingLayout(8), // start_time
                    JAVA_LONG.withName("duration"),
                    paddingLayout(8), // nb_frames
                    JAVA_INT.withName("disposition"),
                    JAVA_INT.withName("discard"),
                    paddingLayout(8), // sample_aspect_ratio
                    ADDRESS.withName("metadata"),
                    paddingLayout(8), // avg_frame_rate
                    AV_PACKET.withName("attached_pic"),
                    paddingLayout(16)) // event_flags, r_frame_rate, pts_wrap_bits
            .withName("AVStream");

    /// `AVDictionaryEntry`: one entry of a metadata dictionary, as `av_dict_get`
    /// hands it back. Read for a track's `language` and `title`.
    public static final StructLayout AV_DICTIONARY_ENTRY =
            structLayout(ADDRESS.withName("key"), ADDRESS.withName("value")).withName("AVDictionaryEntry");

    /// `AVCodecParameters`: what the container says about a track's codec.
    public static final StructLayout AV_CODEC_PARAMETERS = structLayout(
                    JAVA_INT.withName("codec_type"),
                    JAVA_INT.withName("codec_id"),
                    paddingLayout(8), // codec_tag, padding
                    ADDRESS.withName("extradata"),
                    JAVA_INT.withName("extradata_size"),
                    paddingLayout(16), // coded_side_data, nb_coded_side_data
                    JAVA_INT.withName("format"),
                    JAVA_LONG.withName("bit_rate"),
                    paddingLayout(8), // bits_per_coded_sample, bits_per_raw_sample
                    JAVA_INT.withName("profile"),
                    JAVA_INT.withName("level"),
                    JAVA_INT.withName("width"),
                    JAVA_INT.withName("height"),
                    paddingLayout(20), // sample_aspect_ratio, framerate, field_order
                    JAVA_INT.withName("color_range"),
                    JAVA_INT.withName("color_primaries"),
                    JAVA_INT.withName("color_trc"),
                    JAVA_INT.withName("color_space"),
                    JAVA_INT.withName("chroma_location"),
                    paddingLayout(8), // video_delay, padding
                    AV_CHANNEL_LAYOUT.withName("ch_layout"),
                    JAVA_INT.withName("sample_rate"),
                    paddingLayout(28))
            .withName("AVCodecParameters");

    /// `AVCodecContext`: a decoder's state. The largest struct here and the one
    /// with the most fields between the ones read, which is why it has the most
    /// padding.
    public static final StructLayout AV_CODEC_CONTEXT = structLayout(
                    paddingLayout(24), // av_class, log_level_offset, codec_type, codec
                    JAVA_INT.withName("codec_id"),
                    paddingLayout(20), // codec_tag, priv_data, internal
                    ADDRESS.withName("opaque"),
                    paddingLayout(8), // bit_rate
                    JAVA_INT.withName("flags"),
                    paddingLayout(24), // flags2 .. time_base
                    AV_RATIONAL.withName("pkt_timebase"),
                    paddingLayout(36), // framerate onwards
                    JAVA_INT.withName("pix_fmt"),
                    paddingLayout(52),
                    ADDRESS.withName("get_format"),
                    paddingLayout(148),
                    JAVA_INT.withName("sample_fmt"),
                    paddingLayout(208),
                    ADDRESS.withName("hw_device_ctx"),
                    paddingLayout(88),
                    JAVA_INT.withName("thread_count"),
                    paddingLayout(204))
            .withName("AVCodecContext");

    /// `AVCodecHWConfig`: one way a decoder can use a hardware device, as
    /// `avcodec_get_hw_config` lists them (phase 5, ADR-0470).
    public static final StructLayout AV_CODEC_HW_CONFIG = structLayout(
                    JAVA_INT.withName("pix_fmt"), JAVA_INT.withName("methods"), JAVA_INT.withName("device_type"))
            .withName("AVCodecHWConfig");

    /// `AVFrame`: one decoded picture or run of samples.
    public static final StructLayout AV_FRAME = structLayout(
                    sequenceLayout(8, ADDRESS).withName("data"),
                    sequenceLayout(8, JAVA_INT).withName("linesize"),
                    paddingLayout(8), // extended_data
                    JAVA_INT.withName("width"),
                    JAVA_INT.withName("height"),
                    JAVA_INT.withName("nb_samples"),
                    JAVA_INT.withName("format"),
                    paddingLayout(16), // pict_type, sample_aspect_ratio, padding
                    JAVA_LONG.withName("pts"),
                    JAVA_LONG.withName("pkt_dts"),
                    paddingLayout(28), // time_base .. repeat_pict
                    JAVA_INT.withName("sample_rate"),
                    paddingLayout(92), // buf[], extended_buf, side_data
                    JAVA_INT.withName("flags"),
                    JAVA_INT.withName("color_range"),
                    JAVA_INT.withName("color_primaries"),
                    JAVA_INT.withName("color_trc"),
                    JAVA_INT.withName("colorspace"),
                    JAVA_INT.withName("chroma_location"),
                    paddingLayout(28), // best_effort_timestamp .. decode_error_flags
                    ADDRESS.withName("hw_frames_ctx"),
                    paddingLayout(48), // opaque_ref .. private_ref
                    AV_CHANNEL_LAYOUT.withName("ch_layout"),
                    JAVA_LONG.withName("duration"),
                    paddingLayout(8))
            .withName("AVFrame");

    /// Every layout above, which is the list [FfmpegLayoutCheck] verifies.
    public static final List<StructLayout> ALL = List.of(
            AV_RATIONAL,
            AV_CHANNEL_LAYOUT,
            AV_FORMAT_CONTEXT,
            AV_IO_CONTEXT,
            AV_CODEC,
            AV_INPUT_FORMAT,
            AV_STREAM,
            AV_DICTIONARY_ENTRY,
            AV_CODEC_PARAMETERS,
            AV_CODEC_CONTEXT,
            AV_CODEC_HW_CONFIG,
            AV_PACKET,
            AV_FRAME);
}
