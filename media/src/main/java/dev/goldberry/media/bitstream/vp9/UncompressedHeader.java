package dev.goldberry.media.bitstream.vp9;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.MemorySegment;
import java.util.List;

import dev.goldberry.media.bitstream.BitReader;

/// A VP9 frame's `uncompressed_header()`, read to its last bit: everything
/// Vulkan's `StdVideoDecodeVP9PictureInfo` and the structs it points to are
/// filled from, and where the compressed header and the tiles start.
///
/// A frame is read against the [StreamState] the frames before it left: its
/// size may be a reference slot's, an inter frame keeps the colour of the last
/// keyframe, and the loop filter's deltas and the segment features carry on
/// from frame to frame. The values here are this frame's as they stand for
/// it, the carried ones included; the syntax elements that are only coded in
/// some frames read as the specification infers them in the others.
///
/// A frame that shows an existing one (`show_existing_frame`) is one or two
/// bytes: it has the slot's size and the stream's colour, and nothing else to
/// decode.
///
/// @param profile                     `Profile`: 0 to 3, from the low and
///                                    high bits
/// @param showExistingFrame           `show_existing_frame`
/// @param frameToShowMapIdx           `frame_to_show_map_idx`: the slot shown
/// @param frameType                   `frame_type`: [#KEY_FRAME] or
///                                    [#NON_KEY_FRAME]
/// @param showFrame                   `show_frame`, true for an existing
///                                    frame shown
/// @param errorResilientMode          `error_resilient_mode`
/// @param intraOnly                   `intra_only`
/// @param resetFrameContext           `reset_frame_context`, 0 to 3
/// @param color                       the colour in force: stated by a
///                                    keyframe or an intra-only frame, kept
///                                    by the rest
/// @param refreshFrameFlags           `refresh_frame_flags`: a bit for each
///                                    slot the frame is put in, `0xFF` for a
///                                    keyframe
/// @param refFrameIdx                 `ref_frame_idx`: the slots of the last,
///                                    golden and altref references; zeros for
///                                    an intra frame
/// @param refFrameSignBias            `ref_frame_sign_bias` as coded, bit 1
///                                    for last to bit 3 for altref
/// @param foundRef                    the reference whose size the frame
///                                    takes (`found_ref`), 0 to 2, or −1 when
///                                    it codes its own
/// @param frameWidth                  `FrameWidth`
/// @param frameHeight                 `FrameHeight`
/// @param renderAndFrameSizeDifferent `render_and_frame_size_different`
/// @param renderWidth                 `RenderWidth`: the frame's width
///                                    unless it states another
/// @param renderHeight                `RenderHeight`, likewise
/// @param allowHighPrecisionMv        `allow_high_precision_mv`
/// @param filterSwitchable            `is_filter_switchable`, false for an
///                                    intra frame
/// @param rawInterpolationFilter      `raw_interpolation_filter_type` as
///                                    coded; [#interpolationFilter] is the
///                                    filter it means
/// @param refreshFrameContext         `refresh_frame_context`
/// @param frameParallelDecodingMode   `frame_parallel_decoding_mode`
/// @param frameContextIdx             `frame_context_idx` as coded
/// @param loopFilter                  `loop_filter_params()`, with the deltas
///                                    in force
/// @param quantization                `quantization_params()`
/// @param segmentation                `segmentation_params()`, with the
///                                    features in force
/// @param tileColsLog2                `tile_cols_log2`
/// @param tileRowsLog2                `tile_rows_log2`
/// @param headerSizeInBytes           `header_size_in_bytes`: the size of the
///                                    compressed header, 0 for an existing
///                                    frame shown
/// @param uncompressedHeaderSize      the bytes of this header, with its
///                                    trailing bits: where the compressed
///                                    header starts
/// @param usePrevFrameMvs             `UsePrevFrameMvs`: the previous frame's
///                                    motion vectors are candidates for this
///                                    one's. As libvpx decides it: an inter
///                                    frame, not error resilient, the size of
///                                    the frame decoded before it, which was
///                                    shown and was neither a keyframe nor
///                                    intra-only
public record UncompressedHeader(
        int profile,
        boolean showExistingFrame,
        int frameToShowMapIdx,
        int frameType,
        boolean showFrame,
        boolean errorResilientMode,
        boolean intraOnly,
        int resetFrameContext,
        ColorConfig color,
        int refreshFrameFlags,
        List<Integer> refFrameIdx,
        int refFrameSignBias,
        int foundRef,
        int frameWidth,
        int frameHeight,
        boolean renderAndFrameSizeDifferent,
        int renderWidth,
        int renderHeight,
        boolean allowHighPrecisionMv,
        boolean filterSwitchable,
        int rawInterpolationFilter,
        boolean refreshFrameContext,
        boolean frameParallelDecodingMode,
        int frameContextIdx,
        LoopFilter loopFilter,
        Quantization quantization,
        Segmentation segmentation,
        int tileColsLog2,
        int tileRowsLog2,
        int headerSizeInBytes,
        int uncompressedHeaderSize,
        boolean usePrevFrameMvs) {

    /// `frame_type` of a keyframe.
    public static final int KEY_FRAME = 0;

    /// `frame_type` of every other frame.
    public static final int NON_KEY_FRAME = 1;

    /// The references of an inter frame: `REFS_PER_FRAME`.
    public static final int REFS_PER_FRAME = 3;

    /// The reference frames, as [#refFrameSignBias] and the loop filter's
    /// deltas number them.
    public static final int INTRA_FRAME = 0;

    public static final int LAST_FRAME = 1;
    public static final int GOLDEN_FRAME = 2;
    public static final int ALTREF_FRAME = 3;

    /// The interpolation filters, as Vulkan's
    /// `StdVideoVP9InterpolationFilter` numbers them.
    public static final int EIGHTTAP = 0;

    public static final int EIGHTTAP_SMOOTH = 1;
    public static final int EIGHTTAP_SHARP = 2;
    public static final int BILINEAR = 3;
    public static final int SWITCHABLE = 4;

    /// The first three bytes of a keyframe or intra-only frame after its first
    /// byte: `frame_sync_code()`.
    public static final int SYNC_CODE = 0x49_83_42;

    /// `literal_to_type`: the filter each `raw_interpolation_filter_type`
    /// means.
    private static final List<Integer> LITERAL_TO_TYPE = List.of(EIGHTTAP_SMOOTH, EIGHTTAP, EIGHTTAP_SHARP, BILINEAR);

    /// More than the longest uncompressed header, which is under 80 bytes.
    private static final int MAX_HEADER_BYTES = 256;

    /// The widest a tile column may be, and the narrowest, in 64×64 blocks.
    private static final int MAX_TILE_WIDTH_B64 = 64;

    private static final int MIN_TILE_WIDTH_B64 = 4;

    public UncompressedHeader {
        refFrameIdx = List.copyOf(refFrameIdx);
    }

    /// The header at the start of `frame`, one frame of a packet (see
    /// [Superframe#frames]), read against `state`: what the frames before it
    /// left.
    ///
    /// @throws IllegalArgumentException when it is malformed or not one a
    ///                                  decoder can decode: no frame marker or
    ///                                  sync code, a reserved bit set, a
    ///                                  reference to an empty slot or of
    ///                                  another format, a size no reference
    ///                                  can be scaled to, or headers that run
    ///                                  past the frame
    public static UncompressedHeader parse(MemorySegment frame, StreamState state) {
        var bytes = new byte[(int) Math.min(frame.byteSize(), MAX_HEADER_BYTES)];
        MemorySegment.copy(frame, JAVA_BYTE, 0, bytes, 0, bytes.length);
        var header = read(new BitReader(bytes), state);
        if ((long) header.uncompressedHeaderSize() + header.headerSizeInBytes() > frame.byteSize()) {
            throw new IllegalArgumentException("a VP9 frame of " + frame.byteSize() + " bytes, whose headers take "
                    + header.uncompressedHeaderSize() + " and " + header.headerSizeInBytes());
        }
        return header;
    }

    /// [#parse] of a frame in a byte array.
    public static UncompressedHeader parse(byte[] frame, StreamState state) {
        return parse(MemorySegment.ofArray(frame), state);
    }

    /// Whether it is a keyframe.
    public boolean isKeyFrame() {
        return frameType == KEY_FRAME;
    }

    /// `FrameIsIntra`: a keyframe or an intra-only frame.
    public boolean isIntra() {
        return isKeyFrame() || intraOnly;
    }

    /// Whether `ref_frame_sign_bias` is set for `refFrame`, [#LAST_FRAME] to
    /// [#ALTREF_FRAME], as coded.
    public boolean refFrameSignBias(int refFrame) {
        return (refFrameSignBias & (1 << refFrame)) != 0;
    }

    /// The sign biases a decoder uses, as Vulkan's `ref_frame_sign_bias_mask`
    /// holds them: as coded, except in an error-resilient frame, which resets
    /// them to zero as libvpx does.
    public int refFrameSignBiasMask() {
        return errorResilientMode ? 0 : refFrameSignBias;
    }

    /// The filter [#rawInterpolationFilter] means, or [#SWITCHABLE] when each
    /// block chooses: one of Vulkan's `StdVideoVP9InterpolationFilter`.
    public int interpolationFilter() {
        return filterSwitchable ? SWITCHABLE : LITERAL_TO_TYPE.get(rawInterpolationFilter);
    }

    /// The probability context the frame is decoded with: [#frameContextIdx],
    /// except in an intra or error-resilient frame, which always uses context
    /// 0 once it has reset them.
    public int frameContextIdxInForce() {
        return isIntra() || errorResilientMode ? 0 : frameContextIdx;
    }

    /// `MiCols`: the frame's width in 8×8 blocks.
    public int miCols() {
        return (frameWidth + 7) >> 3;
    }

    /// `MiRows`: its height in 8×8 blocks.
    public int miRows() {
        return (frameHeight + 7) >> 3;
    }

    /// `Sb64Cols`: its width in 64×64 blocks.
    public int sb64Cols() {
        return (miCols() + 7) >> 3;
    }

    /// `Sb64Rows`: its height in 64×64 blocks.
    public int sb64Rows() {
        return (miRows() + 7) >> 3;
    }

    /// Where the compressed header starts in the frame: Vulkan's
    /// `compressedHeaderOffset`, the uncompressed one being at 0.
    public int compressedHeaderOffset() {
        return uncompressedHeaderSize;
    }

    /// Where the tiles start in the frame: Vulkan's `tilesOffset`.
    public int tilesOffset() {
        return uncompressedHeaderSize + headerSizeInBytes;
    }

    private static UncompressedHeader read(BitReader r, StreamState state) {
        if (r.bits(2) != 2) {
            throw new IllegalArgumentException("a VP9 frame without its frame marker");
        }
        var low = r.bit();
        var profile = (r.bit() << 1) | low;
        if (profile == 3 && r.flag()) {
            throw new IllegalArgumentException("a VP9 frame of profile 3 with its reserved bit set");
        }
        if (r.flag()) {
            return existing(r, profile, state);
        }
        var frameType = r.bit();
        var showFrame = r.flag();
        var errorResilient = r.flag();
        var intraOnly = false;
        var reset = 0;
        var color = state.color();
        int refresh;
        var refIdx = List.of(0, 0, 0);
        var signBias = 0;
        var found = -1;
        Size size;
        var highPrecision = false;
        var switchable = false;
        var rawFilter = 0;
        if (frameType == KEY_FRAME) {
            sync(r);
            color = ColorConfig.read(r, profile);
            refresh = 0xFF;
            size = Size.read(r);
        } else {
            intraOnly = !showFrame && r.flag();
            reset = errorResilient ? 0 : r.bits(2);
            if (intraOnly) {
                sync(r);
                color = profile > 0 ? ColorConfig.read(r, profile) : ColorConfig.PROFILE_0_INTRA_ONLY;
                refresh = r.bits(8);
                size = Size.read(r);
            } else {
                refresh = r.bits(8);
                var slots = new int[REFS_PER_FRAME];
                for (var i = 0; i < REFS_PER_FRAME; i++) {
                    slots[i] = r.bits(3);
                    signBias |= r.bit() << (LAST_FRAME + i);
                    if (!state.slots().get(slots[i]).filled()) {
                        throw new IllegalArgumentException(
                                "a VP9 frame that refers to slot " + slots[i] + ", which no frame has been put in");
                    }
                }
                refIdx = List.of(slots[0], slots[1], slots[2]);
                for (var i = 0; i < REFS_PER_FRAME && found < 0; i++) {
                    if (r.flag()) {
                        found = i;
                    }
                }
                size = found >= 0 ? Size.ofSlot(r, state.slots().get(slots[found])) : Size.read(r);
                checkReferences(state, refIdx, size, color);
                highPrecision = r.flag();
                switchable = r.flag();
                if (!switchable) {
                    rawFilter = r.bits(2);
                }
            }
        }
        var refreshContext = false;
        var parallel = true;
        if (!errorResilient) {
            refreshContext = r.flag();
            parallel = r.flag();
        }
        var contextIdx = r.bits(2);
        // setup_past_independence(): what an intra or error-resilient frame
        // starts again from.
        var independent = frameType == KEY_FRAME || intraOnly || errorResilient;
        var loopFilter = LoopFilter.read(
                r,
                independent ? LoopFilter.DEFAULT_REF_DELTAS : state.refDeltas(),
                independent ? LoopFilter.DEFAULT_MODE_DELTAS : state.modeDeltas());
        var quantization = Quantization.read(r);
        var segmentation = Segmentation.read(
                r, !independent && state.absOrDeltaUpdate(), independent ? SegmentFeatures.NONE : state.features());
        var sb64Cols = (((size.width() + 7) >> 3) + 7) >> 3;
        var minLog2 = 0;
        while ((MAX_TILE_WIDTH_B64 << minLog2) < sb64Cols) {
            minLog2++;
        }
        var maxLog2 = 0;
        while ((sb64Cols >> (maxLog2 + 1)) >= MIN_TILE_WIDTH_B64) {
            maxLog2++;
        }
        var tileCols = minLog2;
        while (tileCols < maxLog2 && r.flag()) {
            tileCols++;
        }
        if (tileCols > 6) {
            throw new IllegalArgumentException("a VP9 frame of " + (1 << tileCols) + " tile columns");
        }
        var tileRows = r.bit();
        if (tileRows == 1) {
            tileRows += r.bit();
        }
        var compressedSize = r.bits(16);
        if (compressedSize == 0) {
            throw new IllegalArgumentException("a VP9 frame with no compressed header");
        }
        var usePrevFrameMvs = frameType != KEY_FRAME
                && !intraOnly
                && !errorResilient
                && size.width() == state.lastWidth()
                && size.height() == state.lastHeight()
                && state.lastShowFrame()
                && !state.lastIntraOnly()
                && !state.lastKeyFrame();
        return new UncompressedHeader(
                profile,
                false,
                0,
                frameType,
                showFrame,
                errorResilient,
                intraOnly,
                reset,
                color,
                refresh,
                refIdx,
                signBias,
                found,
                size.width(),
                size.height(),
                size.renderDifferent(),
                size.renderWidth(),
                size.renderHeight(),
                highPrecision,
                switchable,
                rawFilter,
                refreshContext,
                parallel,
                contextIdx,
                loopFilter,
                quantization,
                segmentation,
                tileCols,
                tileRows,
                compressedSize,
                (int) ((r.position() + 7) >>> 3),
                usePrevFrameMvs);
    }

    /// The rest of a frame that shows the one in a slot.
    private static UncompressedHeader existing(BitReader r, int profile, StreamState state) {
        var index = r.bits(3);
        var slot = state.slots().get(index);
        if (!slot.filled()) {
            throw new IllegalArgumentException(
                    "a VP9 frame that shows slot " + index + ", which no frame has been put in");
        }
        return new UncompressedHeader(
                profile,
                true,
                index,
                NON_KEY_FRAME,
                true,
                false,
                false,
                0,
                state.color(),
                0,
                List.of(0, 0, 0),
                0,
                -1,
                slot.width(),
                slot.height(),
                slot.renderWidth() != slot.width() || slot.renderHeight() != slot.height(),
                slot.renderWidth(),
                slot.renderHeight(),
                false,
                false,
                0,
                false,
                false,
                0,
                new LoopFilter(0, 0, false, false, 0, state.refDeltas(), 0, state.modeDeltas()),
                new Quantization(0, 0, 0, 0),
                new Segmentation(
                        false,
                        false,
                        List.of(255, 255, 255, 255, 255, 255, 255),
                        false,
                        List.of(255, 255, 255),
                        false,
                        state.absOrDeltaUpdate(),
                        state.features()),
                0,
                0,
                0,
                (int) ((r.position() + 7) >>> 3),
                false);
    }

    private static void sync(BitReader r) {
        var code = r.bits(24);
        if (code != SYNC_CODE) {
            throw new IllegalArgumentException("a VP9 frame with the sync code " + Integer.toHexString(code));
        }
    }

    /// libvpx's checks of an inter frame's references: each in the frame's
    /// format, and at least one at a size the frame can be predicted from, at
    /// most twice as large and at least a sixteenth as large.
    private static void checkReferences(StreamState state, List<Integer> refIdx, Size size, ColorConfig color) {
        var scalable = false;
        for (var index : refIdx) {
            var slot = state.slots().get(index);
            if (slot.bitDepth() != color.bitDepth()
                    || slot.subsamplingX() != color.subsamplingX()
                    || slot.subsamplingY() != color.subsamplingY()) {
                throw new IllegalArgumentException("a VP9 frame of " + color.bitDepth() + " bits that refers to slot "
                        + index + " of " + slot.bitDepth() + ", or of other subsampling");
            }
            scalable |= 2 * size.width() >= slot.width()
                    && 2 * size.height() >= slot.height()
                    && size.width() <= 16 * slot.width()
                    && size.height() <= 16 * slot.height();
        }
        if (!scalable) {
            throw new IllegalArgumentException(
                    "a VP9 frame of " + size.width() + "×" + size.height() + " that no reference can be scaled to");
        }
    }

    /// `frame_size()` or a reference's, and `render_size()`.
    private record Size(int width, int height, boolean renderDifferent, int renderWidth, int renderHeight) {

        static Size read(BitReader r) {
            var width = r.bits(16) + 1;
            var height = r.bits(16) + 1;
            return render(r, width, height);
        }

        static Size ofSlot(BitReader r, StreamState.Slot slot) {
            return render(r, slot.width(), slot.height());
        }

        private static Size render(BitReader r, int width, int height) {
            if (r.flag()) {
                return new Size(width, height, true, r.bits(16) + 1, r.bits(16) + 1);
            }
            return new Size(width, height, false, width, height);
        }
    }
}
