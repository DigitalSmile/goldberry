package dev.goldberry.media.ffm;

import java.util.Objects;

/// The upstream constants the bindings compare against, read from the layout
/// file rather than written down twice: an enum value a header renumbers is
/// then a loud mismatch at load, not a silent wrong branch.
///
/// Grouped by library, so a decoder takes its own group and nothing else.
///
/// @param dav1d dav1d's
/// @param vpx   libvpx's
/// @param opus  libopus's
/// @param flac  libFLAC's
/// @param mp3   minimp3's
/// @param yuv   libyuv's
public record NativeConstants(Dav1d dav1d, Vpx vpx, Opus opus, Flac flac, Mp3 mp3, Yuv yuv) {

    public NativeConstants {
        Objects.requireNonNull(dav1d, "dav1d");
        Objects.requireNonNull(vpx, "vpx");
        Objects.requireNonNull(opus, "opus");
        Objects.requireNonNull(flac, "flac");
        Objects.requireNonNull(mp3, "mp3");
        Objects.requireNonNull(yuv, "yuv");
    }

    /// Every group, from `layout`.
    ///
    /// @throws IllegalArgumentException when the probe did not report one of them
    public static NativeConstants from(NativeLayout layout) {
        return new NativeConstants(
                Dav1d.from(layout),
                Vpx.from(layout),
                Opus.from(layout),
                Flac.from(layout),
                Mp3.from(layout),
                Yuv.from(layout));
    }

    /// dav1d's pixel layouts, matrix coefficients and error codes.
    public record Dav1d(
            int layoutI400,
            int layoutI420,
            int layoutI422,
            int layoutI444,
            int mcIdentity,
            int mcBt709,
            int mcUnknown,
            int mcBt470bg,
            int mcBt601,
            int mcBt2020Ncl,
            int mcBt2020Cl,
            int errAgain,
            int errInval,
            int errNoMem,
            int errNoEnt,
            int pictureAlignment) {

        static Dav1d from(NativeLayout layout) {
            return new Dav1d(
                    i(layout, "DAV1D_PIXEL_LAYOUT_I400"),
                    i(layout, "DAV1D_PIXEL_LAYOUT_I420"),
                    i(layout, "DAV1D_PIXEL_LAYOUT_I422"),
                    i(layout, "DAV1D_PIXEL_LAYOUT_I444"),
                    i(layout, "DAV1D_MC_IDENTITY"),
                    i(layout, "DAV1D_MC_BT709"),
                    i(layout, "DAV1D_MC_UNKNOWN"),
                    i(layout, "DAV1D_MC_BT470BG"),
                    i(layout, "DAV1D_MC_BT601"),
                    i(layout, "DAV1D_MC_BT2020_NCL"),
                    i(layout, "DAV1D_MC_BT2020_CL"),
                    i(layout, "DAV1D_ERR(EAGAIN)"),
                    i(layout, "DAV1D_ERR(EINVAL)"),
                    i(layout, "DAV1D_ERR(ENOMEM)"),
                    i(layout, "DAV1D_ERR(ENOENT)"),
                    i(layout, "DAV1D_PICTURE_ALIGNMENT"));
        }
    }

    /// libvpx's ABI version, result codes, image formats and colour spaces.
    public record Vpx(
            int decoderAbiVersion,
            int codecOk,
            int codecUnsupportedBitstream,
            int codecCorruptFrame,
            int imgFmtI420,
            int imgFmtI422,
            int imgFmtI444,
            int imgFmtI440,
            int imgFmtNv12,
            int imgFmtI42016,
            int imgFmtI42216,
            int imgFmtI44416,
            int imgFmtI44016,
            int imgFmtHighBitDepth,
            int csUnknown,
            int csBt601,
            int csBt709,
            int csSmpte170,
            int csSmpte240,
            int csBt2020,
            int csSrgb,
            int crFullRange) {

        static Vpx from(NativeLayout layout) {
            return new Vpx(
                    i(layout, "VPX_DECODER_ABI_VERSION"),
                    i(layout, "VPX_CODEC_OK"),
                    i(layout, "VPX_CODEC_UNSUP_BITSTREAM"),
                    i(layout, "VPX_CODEC_CORRUPT_FRAME"),
                    i(layout, "VPX_IMG_FMT_I420"),
                    i(layout, "VPX_IMG_FMT_I422"),
                    i(layout, "VPX_IMG_FMT_I444"),
                    i(layout, "VPX_IMG_FMT_I440"),
                    i(layout, "VPX_IMG_FMT_NV12"),
                    i(layout, "VPX_IMG_FMT_I42016"),
                    i(layout, "VPX_IMG_FMT_I42216"),
                    i(layout, "VPX_IMG_FMT_I44416"),
                    i(layout, "VPX_IMG_FMT_I44016"),
                    i(layout, "VPX_IMG_FMT_HIGHBITDEPTH"),
                    i(layout, "VPX_CS_UNKNOWN"),
                    i(layout, "VPX_CS_BT_601"),
                    i(layout, "VPX_CS_BT_709"),
                    i(layout, "VPX_CS_SMPTE_170"),
                    i(layout, "VPX_CS_SMPTE_240"),
                    i(layout, "VPX_CS_BT_2020"),
                    i(layout, "VPX_CS_SRGB"),
                    i(layout, "VPX_CR_FULL_RANGE"));
        }
    }

    /// libopus's result codes and the two `ctl` requests used.
    public record Opus(int ok, int badArg, int invalidPacket, int resetState, int setGainRequest) {

        static Opus from(NativeLayout layout) {
            return new Opus(
                    i(layout, "OPUS_OK"),
                    i(layout, "OPUS_BAD_ARG"),
                    i(layout, "OPUS_INVALID_PACKET"),
                    i(layout, "OPUS_RESET_STATE"),
                    i(layout, "OPUS_SET_GAIN_REQUEST"));
        }
    }

    /// libFLAC's init, read and write statuses and decoder states.
    public record Flac(
            int initStatusOk,
            int readStatusContinue,
            int readStatusEndOfStream,
            int readStatusAbort,
            int writeStatusContinue,
            int writeStatusAbort,
            int stateSearchForFrameSync,
            int stateReadFrame,
            int stateEndOfStream,
            int stateAborted,
            int stateMemoryAllocationError,
            int numberTypeSampleNumber,
            int maxChannels,
            int maxBlockSize) {

        static Flac from(NativeLayout layout) {
            return new Flac(
                    i(layout, "FLAC__STREAM_DECODER_INIT_STATUS_OK"),
                    i(layout, "FLAC__STREAM_DECODER_READ_STATUS_CONTINUE"),
                    i(layout, "FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM"),
                    i(layout, "FLAC__STREAM_DECODER_READ_STATUS_ABORT"),
                    i(layout, "FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE"),
                    i(layout, "FLAC__STREAM_DECODER_WRITE_STATUS_ABORT"),
                    i(layout, "FLAC__STREAM_DECODER_SEARCH_FOR_FRAME_SYNC"),
                    i(layout, "FLAC__STREAM_DECODER_READ_FRAME"),
                    i(layout, "FLAC__STREAM_DECODER_END_OF_STREAM"),
                    i(layout, "FLAC__STREAM_DECODER_ABORTED"),
                    i(layout, "FLAC__STREAM_DECODER_MEMORY_ALLOCATION_ERROR"),
                    i(layout, "FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER"),
                    i(layout, "FLAC__MAX_CHANNELS"),
                    i(layout, "FLAC__MAX_BLOCK_SIZE"));
        }
    }

    /// minimp3's output bound and sample size.
    public record Mp3(int maxSamplesPerFrame, int sampleBytes) {

        static Mp3 from(NativeLayout layout) {
            return new Mp3(i(layout, "MINIMP3_MAX_SAMPLES_PER_FRAME"), i(layout, "MINIMP3_SAMPLE_BYTES"));
        }
    }

    /// libyuv's filter for chroma upsampling: bilinear, at each sample's centre.
    public record Yuv(int filterBilinear) {

        static Yuv from(NativeLayout layout) {
            return new Yuv(i(layout, "kFilterBilinear"));
        }
    }

    private static int i(NativeLayout layout, String name) {
        return Math.toIntExact(layout.require(name));
    }
}
