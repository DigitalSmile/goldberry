package dev.goldberry.media.bitstream.av1;

import static dev.goldberry.media.bitstream.av1.FrameHeader.INTER_FRAME;
import static dev.goldberry.media.bitstream.av1.FrameHeader.INTRA_ONLY_FRAME;
import static dev.goldberry.media.bitstream.av1.FrameHeader.KEY_FRAME;
import static dev.goldberry.media.bitstream.av1.FrameHeader.PRIMARY_REF_NONE;
import static dev.goldberry.media.bitstream.av1.FrameHeader.SWITCH_FRAME;
import static dev.goldberry.media.bitstream.av1.References.ALL_FRAMES;
import static dev.goldberry.media.bitstream.av1.References.LAST_FRAME;
import static dev.goldberry.media.bitstream.av1.References.NUM_REF_FRAMES;
import static dev.goldberry.media.bitstream.av1.References.REFS_PER_FRAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// `uncompressed_header()` (§5.9.2) and the processes it runs, read as the
/// specification writes them, against one sequence header and one set of
/// reference slots.
///
/// The slots are read, never changed: what the header takes away from them
/// while it is read (a shown key frame, frame ids, an error resilient frame's
/// order hints) is applied to a copy, and replayed by [References#updated].
final class FrameHeaderReader {

    /// `Segmentation_Feature_Bits`.
    private static final int[] FEATURE_BITS = {8, 6, 6, 6, 6, 3, 0, 0};

    /// `Segmentation_Feature_Signed`.
    private static final boolean[] FEATURE_SIGNED = {true, true, true, true, true, false, false, false};

    /// `Segmentation_Feature_Max`: `MAXQ`, `MAX_LOOP_FILTER` four times, the
    /// last reference frame.
    private static final int[] FEATURE_MAX = {255, 63, 63, 63, 63, 7, 0, 0};

    /// `Remap_Lr_Type`: `lr_type` to `FrameRestorationType`.
    private static final int[] REMAP_LR_TYPE = {
        LoopRestoration.RESTORE_NONE,
        LoopRestoration.RESTORE_SWITCHABLE,
        LoopRestoration.RESTORE_WIENER,
        LoopRestoration.RESTORE_SGRPROJ
    };

    /// `Ref_Frame_List`: the references `set_frame_refs()` fills forward.
    private static final int[] REF_FRAME_LIST = {
        References.LAST2_FRAME,
        References.LAST3_FRAME,
        References.BWDREF_FRAME,
        References.ALTREF2_FRAME,
        References.ALTREF_FRAME
    };

    private static final int MAX_TILE_WIDTH = 4096;
    private static final int MAX_TILE_AREA = 4096 * 2304;
    private static final int MAX_TILE_COLS = 64;
    private static final int MAX_TILE_ROWS = 64;

    private static final int GM_ABS_ALPHA_BITS = 12;
    private static final int GM_ALPHA_PREC_BITS = 15;
    private static final int GM_ABS_TRANS_ONLY_BITS = 9;
    private static final int GM_TRANS_ONLY_PREC_BITS = 3;
    private static final int GM_ABS_TRANS_BITS = 12;
    private static final int GM_TRANS_PREC_BITS = 6;

    private final Syntax s;
    private final ObuHeader obu;
    private final SequenceHeader seq;
    private References refs;

    private FrameHeaderReader(Syntax s, ObuHeader obu, SequenceHeader seq, References refs) {
        this.s = s;
        this.obu = obu;
        this.seq = seq;
        this.refs = refs;
    }

    /// The uncompressed header `s` is at, of an OBU with header `obu`.
    static UncompressedHeader read(Syntax s, ObuHeader obu, SequenceHeader seq, References refs) {
        return new FrameHeaderReader(s, obu, seq, refs).read();
    }

    private UncompressedHeader read() {
        s.heading("Frame Header");
        var idLength = seq.frameIdLength();
        var timing = seq.timingInfo();
        var presentationTime = seq.decoderModelInfo().isPresent()
                && timing.isPresent()
                && !timing.get().equalPictureInterval();
        long framePresentationTime = 0;
        int frameType;
        boolean showFrame;
        boolean showable;
        boolean errorResilient;
        if (seq.reducedStillPictureHeader()) {
            frameType = KEY_FRAME;
            showFrame = true;
            showable = false;
            errorResilient = true;
        } else {
            if (s.flag("show_existing_frame")) {
                var index = s.f(3, "frame_to_show_map_idx");
                var slot = refs.slot(index);
                if (!slot.valid()) {
                    throw new IllegalArgumentException("show_existing_frame of slot " + index + ", which is not valid");
                }
                if (presentationTime) {
                    framePresentationTime = presentationTimeOf();
                }
                var displayFrameId = seq.frameIdNumbersPresent() ? s.f(idLength, "display_frame_id") : 0;
                return new ShowExistingFrame(index, framePresentationTime, displayFrameId, slot.header());
            }
            frameType = s.f(2, "frame_type");
            showFrame = s.flag("show_frame");
            if (showFrame && presentationTime) {
                framePresentationTime = presentationTimeOf();
            }
            showable = showFrame ? frameType != KEY_FRAME : s.flag("showable_frame");
            errorResilient = frameType == SWITCH_FRAME
                    || (frameType == KEY_FRAME && showFrame)
                    || s.flag("error_resilient_mode");
        }
        if (frameType == KEY_FRAME && showFrame) {
            refs = References.NONE;
        }
        var frameIsIntra = frameType == KEY_FRAME || frameType == INTRA_ONLY_FRAME;
        var disableCdfUpdate = s.flag("disable_cdf_update");
        boolean allowScreenContentTools;
        if (seq.seqForceScreenContentTools() == SequenceHeader.SELECT_SCREEN_CONTENT_TOOLS) {
            allowScreenContentTools = s.flag("allow_screen_content_tools");
        } else {
            allowScreenContentTools = seq.seqForceScreenContentTools() == 1;
        }
        var forceIntegerMv = false;
        if (allowScreenContentTools) {
            if (seq.seqForceIntegerMv() == SequenceHeader.SELECT_INTEGER_MV) {
                forceIntegerMv = s.flag("force_integer_mv");
            } else {
                forceIntegerMv = seq.seqForceIntegerMv() == 1;
            }
        }
        if (frameIsIntra) {
            forceIntegerMv = true;
        }
        var currentFrameId = 0;
        if (seq.frameIdNumbersPresent()) {
            currentFrameId = s.f(idLength, "current_frame_id");
            refs = refs.markedByFrameId(currentFrameId, seq);
        }
        boolean frameSizeOverride;
        if (frameType == SWITCH_FRAME) {
            frameSizeOverride = true;
        } else {
            frameSizeOverride = !seq.reducedStillPictureHeader() && s.flag("frame_size_override_flag");
        }
        var orderHintBits = seq.orderHintBits();
        var orderHint = orderHintBits > 0 ? s.f(orderHintBits, "order_hint") : 0;
        var primaryRefFrame = frameIsIntra || errorResilient ? PRIMARY_REF_NONE : s.f(3, "primary_ref_frame");
        var bufferRemovalTimePresent = false;
        List<Long> bufferRemovalTimes = List.of();
        if (seq.decoderModelInfo().isPresent()) {
            bufferRemovalTimePresent = s.flag("buffer_removal_time_present_flag");
            if (bufferRemovalTimePresent) {
                bufferRemovalTimes = bufferRemovalTimes();
            }
        }
        var refresh = frameType == SWITCH_FRAME || (frameType == KEY_FRAME && showFrame)
                ? ALL_FRAMES
                : s.f(8, "refresh_frame_flags");
        if (frameType == INTRA_ONLY_FRAME && refresh == ALL_FRAMES) {
            throw new IllegalArgumentException("an intra-only frame that refreshes every slot");
        }
        var refOrderHint = new int[NUM_REF_FRAMES];
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            refOrderHint[i] = refs.slot(i).orderHint();
        }
        if ((!frameIsIntra || refresh != ALL_FRAMES) && errorResilient && seq.enableOrderHint()) {
            for (var i = 0; i < NUM_REF_FRAMES; i++) {
                refOrderHint[i] = s.f(orderHintBits, "ref_order_hint", i);
            }
            refs = refs.expecting(Syntax.list(refOrderHint));
        }

        FrameSize size;
        var allowIntrabc = false;
        var shortSignaling = false;
        var lastFrameIdx = 0;
        var goldFrameIdx = 0;
        List<Integer> refFrameIdx = List.of();
        List<Integer> expectedFrameIds = List.of();
        var allowHighPrecisionMv = false;
        var interpolationFilter = 0;
        var motionModeSwitchable = false;
        var useRefFrameMvs = false;
        var orderHints = new int[NUM_REF_FRAMES];
        var signBias = 0;
        if (frameIsIntra) {
            size = renderSize(frameSize(frameSizeOverride));
            if (allowScreenContentTools && size.upscaledWidth() == size.frameWidth()) {
                allowIntrabc = s.flag("allow_intrabc");
            }
        } else {
            int[] indices;
            if (seq.enableOrderHint()) {
                shortSignaling = s.flag("frame_refs_short_signaling");
            }
            if (shortSignaling) {
                lastFrameIdx = s.f(3, "last_frame_idx");
                goldFrameIdx = s.f(3, "golden_frame_idx");
                indices = setFrameRefs(lastFrameIdx, goldFrameIdx, refOrderHint(), orderHint, seq);
            } else {
                indices = new int[REFS_PER_FRAME];
            }
            var expected = new int[REFS_PER_FRAME];
            for (var i = 0; i < REFS_PER_FRAME; i++) {
                if (!shortSignaling) {
                    indices[i] = s.f(3, "ref_frame_idx", i);
                }
                if (seq.frameIdNumbersPresent()) {
                    var delta = s.f(seq.deltaFrameIdLengthMinus2() + 2, "delta_frame_id_minus1", i) + 1;
                    expected[i] = (currentFrameId + (1 << idLength) - delta) % (1 << idLength);
                }
            }
            refFrameIdx = Syntax.list(indices);
            if (seq.frameIdNumbersPresent()) {
                expectedFrameIds = Syntax.list(expected);
            }
            if (frameSizeOverride && !errorResilient) {
                size = frameSizeWithRefs(indices);
            } else {
                size = renderSize(frameSize(frameSizeOverride));
            }
            if (!forceIntegerMv) {
                allowHighPrecisionMv = s.flag("allow_high_precision_mv");
            }
            interpolationFilter =
                    s.flag("is_filter_switchable") ? FrameHeader.SWITCHABLE : s.f(2, "interpolation_filter");
            motionModeSwitchable = s.flag("is_motion_mode_switchable");
            if (!errorResilient && seq.enableRefFrameMvs()) {
                useRefFrameMvs = s.flag("use_ref_frame_mvs");
            }
            for (var i = 0; i < REFS_PER_FRAME; i++) {
                var hint = refs.slot(indices[i]).orderHint();
                orderHints[LAST_FRAME + i] = hint;
                if (seq.relativeDistance(hint, orderHint) > 0) {
                    signBias |= 1 << (LAST_FRAME + i);
                }
            }
        }
        var disableFrameEndUpdateCdf =
                seq.reducedStillPictureHeader() || disableCdfUpdate || s.flag("disable_frame_end_update_cdf");

        // load_previous(), or setup_past_independence() without a primary
        // reference.
        var previousGm = GlobalMotion.DEFAULT;
        var previousLf = new LoopFilter(
                List.of(0, 0, 0, 0),
                0,
                true,
                false,
                0,
                LoopFilter.DEFAULT_REF_DELTAS,
                0,
                LoopFilter.DEFAULT_MODE_DELTAS);
        var previousSeg = Segmentation.NONE;
        if (primaryRefFrame != PRIMARY_REF_NONE) {
            var previous = refs.slot(refFrameIdx.get(primaryRefFrame)).header();
            previousGm = previous.globalMotion();
            previousLf = previous.loopFilter();
            previousSeg = previous.segmentation();
        }

        var tileInfo = tileInfo(size);
        var quantization = quantization();
        var segmentation = segmentation(primaryRefFrame, previousSeg);
        var deltaQPresent = quantization.baseQIdx() > 0 && s.flag("delta_q_present");
        var deltaQRes = deltaQPresent ? s.f(2, "delta_q_res") : 0;
        var deltaLfPresent = false;
        var deltaLfRes = 0;
        var deltaLfMulti = false;
        if (deltaQPresent) {
            deltaLfPresent = !allowIntrabc && s.flag("delta_lf_present");
            if (deltaLfPresent) {
                deltaLfRes = s.f(2, "delta_lf_res");
                deltaLfMulti = s.flag("delta_lf_multi");
            }
        }
        var codedLossless = true;
        for (var segment = 0; segment < Segmentation.MAX_SEGMENTS; segment++) {
            var qindex = quantization.baseQIdx();
            if (segmentation.featureEnabled(segment, Segmentation.SEG_LVL_ALT_Q)) {
                qindex = Math.clamp(qindex + segmentation.featureData(segment, Segmentation.SEG_LVL_ALT_Q), 0, 255);
            }
            if (qindex != 0
                    || quantization.deltaQYDc() != 0
                    || quantization.deltaQUAc() != 0
                    || quantization.deltaQUDc() != 0
                    || quantization.deltaQVAc() != 0
                    || quantization.deltaQVDc() != 0) {
                codedLossless = false;
            }
        }
        var allLossless = codedLossless && size.frameWidth() == size.upscaledWidth();
        var loopFilter = loopFilter(codedLossless || allowIntrabc, previousLf);
        var cdef = codedLossless || allowIntrabc || !seq.enableCdef() ? Cdef.NONE : cdef();
        var loopRestoration =
                allLossless || allowIntrabc || !seq.enableRestoration() ? LoopRestoration.NONE : loopRestoration();
        var txMode = codedLossless ? FrameHeader.ONLY_4X4 : s.increment(1, 2, "tx_mode");
        var referenceSelect = !frameIsIntra && s.flag("reference_select");
        var skipModeFrames = frameIsIntra || !referenceSelect || !seq.enableOrderHint()
                ? new int[0]
                : skipModeFrames(refFrameIdx, orderHint);
        var skipModePresent = skipModeFrames.length > 0 && s.flag("skip_mode_present");
        var allowWarpedMotion =
                !frameIsIntra && !errorResilient && seq.enableWarpedMotion() && s.flag("allow_warped_motion");
        var reducedTxSet = s.flag("reduced_tx_set");
        var globalMotion = frameIsIntra ? GlobalMotion.DEFAULT : globalMotion(previousGm, allowHighPrecisionMv);
        var filmGrain = filmGrain(frameType, showFrame, showable);
        return new FrameHeader(
                seq,
                frameType,
                showFrame,
                showable,
                framePresentationTime,
                errorResilient,
                disableCdfUpdate,
                allowScreenContentTools,
                forceIntegerMv,
                currentFrameId,
                frameSizeOverride,
                orderHint,
                primaryRefFrame,
                bufferRemovalTimePresent,
                bufferRemovalTimes,
                refresh,
                Syntax.list(refOrderHint),
                size,
                allowIntrabc,
                shortSignaling,
                lastFrameIdx,
                goldFrameIdx,
                refFrameIdx,
                expectedFrameIds,
                allowHighPrecisionMv,
                interpolationFilter,
                motionModeSwitchable,
                useRefFrameMvs,
                Syntax.list(orderHints),
                signBias,
                disableFrameEndUpdateCdf,
                tileInfo,
                quantization,
                segmentation,
                deltaQPresent,
                deltaQRes,
                deltaLfPresent,
                deltaLfRes,
                deltaLfMulti,
                codedLossless,
                allLossless,
                loopFilter,
                cdef,
                loopRestoration,
                txMode,
                referenceSelect,
                skipModePresent,
                skipModeFrames.length > 0 ? Syntax.list(skipModeFrames) : List.of(0, 0),
                allowWarpedMotion,
                reducedTxSet,
                globalMotion,
                filmGrain);
    }

    /// `temporal_point_info()`.
    private long presentationTimeOf() {
        var model = seq.decoderModelInfo().orElseThrow();
        return s.fLong(model.framePresentationTimeLengthMinus1() + 1, "frame_presentation_time");
    }

    private List<Long> bufferRemovalTimes() {
        var model = seq.decoderModelInfo().orElseThrow();
        var points = seq.operatingPoints();
        var times = new ArrayList<Long>(points.size());
        for (var op = 0; op < points.size(); op++) {
            var point = points.get(op);
            long time = 0;
            if (point.decoderModelPresent()) {
                var idc = point.idc();
                var inTemporalLayer = ((idc >> obu.temporalId()) & 1) != 0;
                var inSpatialLayer = ((idc >> (obu.spatialId() + 8)) & 1) != 0;
                if (idc == 0 || (inTemporalLayer && inSpatialLayer)) {
                    time = s.fLong(model.bufferRemovalTimeLengthMinus1() + 1, "buffer_removal_time", op);
                }
            }
            times.add(time);
        }
        return times;
    }

    private int[] refOrderHint() {
        var hints = new int[NUM_REF_FRAMES];
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            hints[i] = refs.slot(i).orderHint();
        }
        return hints;
    }

    /// `frame_size()` (§5.9.5) and `superres_params()`, not yet the render
    /// size.
    private FrameSize frameSize(boolean override) {
        int width;
        int height;
        if (override) {
            width = s.f(seq.frameWidthBitsMinus1() + 1, "frame_width_minus_1") + 1;
            height = s.f(seq.frameHeightBitsMinus1() + 1, "frame_height_minus_1") + 1;
        } else {
            width = seq.maxFrameWidthMinus1() + 1;
            height = seq.maxFrameHeightMinus1() + 1;
        }
        return superres(width, height, width, height, false);
    }

    /// `render_size()` (§5.9.6) after `frame_size()`.
    private FrameSize renderSize(FrameSize size) {
        if (!s.flag("render_and_frame_size_different")) {
            return size;
        }
        var width = s.f(16, "render_width_minus_1") + 1;
        var height = s.f(16, "render_height_minus_1") + 1;
        return new FrameSize(
                size.frameWidth(),
                size.frameHeight(),
                size.upscaledWidth(),
                width,
                height,
                true,
                size.useSuperres(),
                size.codedDenom());
    }

    /// `superres_params()` (§5.9.8) for a frame `upscaledWidth` wide, and
    /// `RenderWidth` and `RenderHeight` as they stand.
    private FrameSize superres(
            int upscaledWidth, int height, int renderWidth, int renderHeight, boolean renderDifferent) {
        var use = seq.enableSuperres() && s.flag("use_superres");
        var codedDenom = use ? s.f(3, "coded_denom") : 0;
        var denom = use ? codedDenom + 9 : FrameSize.SUPERRES_NUM;
        var width = (upscaledWidth * FrameSize.SUPERRES_NUM + denom / 2) / denom;
        return new FrameSize(width, height, upscaledWidth, renderWidth, renderHeight, renderDifferent, use, codedDenom);
    }

    /// `frame_size_with_refs()` (§5.9.7).
    private FrameSize frameSizeWithRefs(int[] indices) {
        for (var i = 0; i < REFS_PER_FRAME; i++) {
            if (s.flag("found_ref", i)) {
                var slot = refs.slot(indices[i]);
                if (!slot.valid()) {
                    throw new IllegalArgumentException("found_ref names slot " + indices[i] + ", which is not valid");
                }
                var ref = slot.header().frameSize();
                return superres(ref.upscaledWidth(), ref.frameHeight(), ref.renderWidth(), ref.renderHeight(), false);
            }
        }
        return renderSize(frameSize(true));
    }

    /// `set_frame_refs()` (§7.8): the slot of each of the seven references,
    /// from the slots of `LAST_FRAME` and `GOLDEN_FRAME` and the order hints
    /// of every slot.
    static int[] setFrameRefs(
            int lastFrameIdx, int goldFrameIdx, int[] refOrderHint, int orderHint, SequenceHeader seq) {
        var refFrameIdx = new int[REFS_PER_FRAME];
        Arrays.fill(refFrameIdx, -1);
        refFrameIdx[0] = lastFrameIdx;
        refFrameIdx[References.GOLDEN_FRAME - LAST_FRAME] = goldFrameIdx;
        var used = new boolean[NUM_REF_FRAMES];
        used[lastFrameIdx] = true;
        used[goldFrameIdx] = true;
        var curFrameHint = 1 << (seq.orderHintBits() - 1);
        var shifted = new int[NUM_REF_FRAMES];
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            shifted[i] = curFrameHint + seq.relativeDistance(refOrderHint[i], orderHint);
        }
        // The latest of the frames after this one is ALTREF_FRAME, the
        // earliest BWDREF_FRAME, the next earliest ALTREF2_FRAME.
        var ref = findLatest(shifted, used, curFrameHint, true);
        if (ref >= 0) {
            refFrameIdx[References.ALTREF_FRAME - LAST_FRAME] = ref;
            used[ref] = true;
        }
        ref = findEarliestBackward(shifted, used, curFrameHint);
        if (ref >= 0) {
            refFrameIdx[References.BWDREF_FRAME - LAST_FRAME] = ref;
            used[ref] = true;
        }
        ref = findEarliestBackward(shifted, used, curFrameHint);
        if (ref >= 0) {
            refFrameIdx[References.ALTREF2_FRAME - LAST_FRAME] = ref;
            used[ref] = true;
        }
        // The rest, latest first, from the frames before this one.
        for (var refFrame : REF_FRAME_LIST) {
            if (refFrameIdx[refFrame - LAST_FRAME] < 0) {
                ref = findLatest(shifted, used, curFrameHint, false);
                if (ref >= 0) {
                    refFrameIdx[refFrame - LAST_FRAME] = ref;
                    used[ref] = true;
                }
            }
        }
        // Any left over: the earliest frame of all.
        ref = -1;
        var earliest = 0;
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            if (ref < 0 || shifted[i] < earliest) {
                ref = i;
                earliest = shifted[i];
            }
        }
        for (var i = 0; i < REFS_PER_FRAME; i++) {
            if (refFrameIdx[i] < 0) {
                refFrameIdx[i] = ref;
            }
        }
        return refFrameIdx;
    }

    /// `find_latest_backward()`, or `find_latest_forward()` when not
    /// `backward`.
    private static int findLatest(int[] shifted, boolean[] used, int curFrameHint, boolean backward) {
        var ref = -1;
        var latest = 0;
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            var hint = shifted[i];
            if (!used[i] && backward == (hint >= curFrameHint) && (ref < 0 || hint >= latest)) {
                ref = i;
                latest = hint;
            }
        }
        return ref;
    }

    /// `find_earliest_backward()`.
    private static int findEarliestBackward(int[] shifted, boolean[] used, int curFrameHint) {
        var ref = -1;
        var earliest = 0;
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            var hint = shifted[i];
            if (!used[i] && hint >= curFrameHint && (ref < 0 || hint < earliest)) {
                ref = i;
                earliest = hint;
            }
        }
        return ref;
    }

    /// `tile_info()` (§5.9.15).
    private TileInfo tileInfo(FrameSize size) {
        var miCols = size.miCols();
        var miRows = size.miRows();
        var sb128 = seq.use128x128Superblock();
        var sbCols = sb128 ? (miCols + 31) >> 5 : (miCols + 15) >> 4;
        var sbRows = sb128 ? (miRows + 31) >> 5 : (miRows + 15) >> 4;
        var sbShift = sb128 ? 5 : 4;
        var sbSize = sbShift + 2;
        var maxTileWidthSb = MAX_TILE_WIDTH >> sbSize;
        var maxTileAreaSb = MAX_TILE_AREA >> (2 * sbSize);
        var minLog2TileCols = tileLog2(maxTileWidthSb, sbCols);
        var maxLog2TileCols = tileLog2(1, Math.min(sbCols, MAX_TILE_COLS));
        var maxLog2TileRows = tileLog2(1, Math.min(sbRows, MAX_TILE_ROWS));
        var minLog2Tiles = Math.max(minLog2TileCols, tileLog2(maxTileAreaSb, sbRows * sbCols));
        var colStarts = new ArrayList<Integer>();
        var rowStarts = new ArrayList<Integer>();
        var widths = new ArrayList<Integer>();
        var heights = new ArrayList<Integer>();
        var uniform = s.flag("uniform_tile_spacing_flag");
        int colsLog2;
        int rowsLog2;
        if (uniform) {
            colsLog2 = s.increment(minLog2TileCols, maxLog2TileCols, "tile_cols_log2");
            var tileWidthSb = (sbCols + (1 << colsLog2) - 1) >> colsLog2;
            for (var start = 0; start < sbCols; start += tileWidthSb) {
                colStarts.add(start << sbShift);
                widths.add(Math.min(tileWidthSb, sbCols - start) - 1);
            }
            var minLog2TileRows = Math.max(minLog2Tiles - colsLog2, 0);
            rowsLog2 = s.increment(minLog2TileRows, maxLog2TileRows, "tile_rows_log2");
            var tileHeightSb = (sbRows + (1 << rowsLog2) - 1) >> rowsLog2;
            for (var start = 0; start < sbRows; start += tileHeightSb) {
                rowStarts.add(start << sbShift);
                heights.add(Math.min(tileHeightSb, sbRows - start) - 1);
            }
        } else {
            var widestTileSb = 0;
            for (var start = 0; start < sbCols; ) {
                if (widths.size() == MAX_TILE_COLS) {
                    throw new IllegalArgumentException("more than " + MAX_TILE_COLS + " tile columns");
                }
                colStarts.add(start << sbShift);
                var maxWidth = Math.min(sbCols - start, maxTileWidthSb);
                var width = s.ns(maxWidth, "width_in_sbs_minus_1", widths.size());
                widths.add(width);
                widestTileSb = Math.max(width + 1, widestTileSb);
                start += width + 1;
            }
            colsLog2 = tileLog2(1, widths.size());
            var maxTileAreaRows = minLog2Tiles > 0 ? (sbRows * sbCols) >> (minLog2Tiles + 1) : sbRows * sbCols;
            var maxTileHeightSb = Math.max(maxTileAreaRows / widestTileSb, 1);
            for (var start = 0; start < sbRows; ) {
                if (heights.size() == MAX_TILE_ROWS) {
                    throw new IllegalArgumentException("more than " + MAX_TILE_ROWS + " tile rows");
                }
                rowStarts.add(start << sbShift);
                var maxHeight = Math.min(sbRows - start, maxTileHeightSb);
                var height = s.ns(maxHeight, "height_in_sbs_minus_1", heights.size());
                heights.add(height);
                start += height + 1;
            }
            rowsLog2 = tileLog2(1, heights.size());
        }
        colStarts.add(miCols);
        rowStarts.add(miRows);
        var contextUpdateTileId = 0;
        var tileSizeBytesMinus1 = 0;
        if (colsLog2 > 0 || rowsLog2 > 0) {
            contextUpdateTileId = s.f(colsLog2 + rowsLog2, "context_update_tile_id");
            tileSizeBytesMinus1 = s.f(2, "tile_size_bytes_minus1");
            if (contextUpdateTileId >= widths.size() * heights.size()) {
                throw new IllegalArgumentException("context_update_tile_id " + contextUpdateTileId + " of "
                        + widths.size() * heights.size() + " tiles");
            }
        }
        return new TileInfo(
                uniform,
                colsLog2,
                rowsLog2,
                colStarts,
                rowStarts,
                widths,
                heights,
                contextUpdateTileId,
                tileSizeBytesMinus1);
    }

    /// `tile_log2(blkSize, target)`: the least `k` with `blkSize << k` at
    /// least `target`.
    private static int tileLog2(int blockSize, int target) {
        var k = 0;
        while ((blockSize << k) < target) {
            k++;
        }
        return k;
    }

    /// `quantization_params()` (§5.9.12).
    private Quantization quantization() {
        var color = seq.colorConfig();
        var base = s.f(8, "base_q_idx");
        var yDc = deltaQ("delta_q_y_dc.delta_coded", "delta_q_y_dc.delta_q");
        var uDc = 0;
        var uAc = 0;
        var vDc = 0;
        var vAc = 0;
        var diffUvDelta = false;
        if (color.numPlanes() > 1) {
            diffUvDelta = color.separateUvDeltaQ() && s.flag("diff_uv_delta");
            uDc = deltaQ("delta_q_u_dc.delta_coded", "delta_q_u_dc.delta_q");
            uAc = deltaQ("delta_q_u_ac.delta_coded", "delta_q_u_ac.delta_q");
            if (diffUvDelta) {
                vDc = deltaQ("delta_q_v_dc.delta_coded", "delta_q_v_dc.delta_q");
                vAc = deltaQ("delta_q_v_ac.delta_coded", "delta_q_v_ac.delta_q");
            } else {
                vDc = uDc;
                vAc = uAc;
            }
        }
        var usingQmatrix = s.flag("using_qmatrix");
        var qmY = 0;
        var qmU = 0;
        var qmV = 0;
        if (usingQmatrix) {
            qmY = s.f(4, "qm_y");
            qmU = s.f(4, "qm_u");
            qmV = color.separateUvDeltaQ() ? s.f(4, "qm_v") : qmU;
        }
        return new Quantization(base, yDc, uDc, uAc, vDc, vAc, diffUvDelta, usingQmatrix, qmY, qmU, qmV);
    }

    /// `read_delta_q()` (§5.9.13).
    private int deltaQ(String coded, String value) {
        return s.flag(coded) ? s.su(7, value) : 0;
    }

    /// `segmentation_params()` (§5.9.14).
    private Segmentation segmentation(int primaryRefFrame, Segmentation previous) {
        if (!s.flag("segmentation_enabled")) {
            return Segmentation.NONE;
        }
        boolean updateMap;
        var temporalUpdate = false;
        boolean updateData;
        if (primaryRefFrame == PRIMARY_REF_NONE) {
            updateMap = true;
            updateData = true;
        } else {
            updateMap = s.flag("segmentation_update_map");
            if (updateMap) {
                temporalUpdate = s.flag("segmentation_temporal_update");
            }
            updateData = s.flag("segmentation_update_data");
        }
        if (!updateData) {
            return new Segmentation(
                    true, updateMap, temporalUpdate, false, previous.featureEnabled(), previous.featureData());
        }
        var enabled = new int[Segmentation.MAX_SEGMENTS];
        var data = new int[Segmentation.MAX_SEGMENTS * Segmentation.SEG_LVL_MAX];
        for (var i = 0; i < Segmentation.MAX_SEGMENTS; i++) {
            for (var j = 0; j < Segmentation.SEG_LVL_MAX; j++) {
                if (!s.flag("feature_enabled", i, j)) {
                    continue;
                }
                enabled[i] |= 1 << j;
                var bits = FEATURE_BITS[j];
                if (bits == 0) {
                    continue;
                }
                var limit = FEATURE_MAX[j];
                data[i * Segmentation.SEG_LVL_MAX + j] = FEATURE_SIGNED[j]
                        ? Math.clamp(s.su(1 + bits, "feature_value", i, j), -limit, limit)
                        : Math.clamp(s.f(bits, "feature_value", i, j), 0, limit);
            }
        }
        return new Segmentation(true, updateMap, temporalUpdate, true, Syntax.list(enabled), Syntax.list(data));
    }

    /// `loop_filter_params()` (§5.9.11), the deltas starting from `previous`'s.
    private LoopFilter loopFilter(boolean losslessOrIntrabc, LoopFilter previous) {
        if (losslessOrIntrabc) {
            return new LoopFilter(
                    List.of(0, 0, 0, 0),
                    0,
                    false,
                    false,
                    0,
                    LoopFilter.DEFAULT_REF_DELTAS,
                    0,
                    LoopFilter.DEFAULT_MODE_DELTAS);
        }
        var level = new int[4];
        level[0] = s.f(6, "loop_filter_level", 0);
        level[1] = s.f(6, "loop_filter_level", 1);
        if (seq.colorConfig().numPlanes() > 1 && (level[0] != 0 || level[1] != 0)) {
            level[2] = s.f(6, "loop_filter_level", 2);
            level[3] = s.f(6, "loop_filter_level", 3);
        }
        var sharpness = s.f(3, "loop_filter_sharpness");
        var deltaEnabled = s.flag("loop_filter_delta_enabled");
        var refDeltas =
                previous.refDeltas().stream().mapToInt(Integer::intValue).toArray();
        var modeDeltas =
                previous.modeDeltas().stream().mapToInt(Integer::intValue).toArray();
        var deltaUpdate = false;
        var updateRef = 0;
        var updateMode = 0;
        if (deltaEnabled) {
            deltaUpdate = s.flag("loop_filter_delta_update");
            if (deltaUpdate) {
                for (var i = 0; i < NUM_REF_FRAMES; i++) {
                    if (s.flag("update_ref_delta", i)) {
                        updateRef |= 1 << i;
                        refDeltas[i] = s.su(7, "loop_filter_ref_deltas", i);
                    }
                }
                for (var i = 0; i < 2; i++) {
                    if (s.flag("update_mode_delta", i)) {
                        updateMode |= 1 << i;
                        modeDeltas[i] = s.su(7, "loop_filter_mode_deltas", i);
                    }
                }
            }
        }
        return new LoopFilter(
                Syntax.list(level),
                sharpness,
                deltaEnabled,
                deltaUpdate,
                updateRef,
                Syntax.list(refDeltas),
                updateMode,
                Syntax.list(modeDeltas));
    }

    /// `cdef_params()` (§5.9.19), when CDEF is on.
    private Cdef cdef() {
        var damping = s.f(2, "cdef_damping_minus_3");
        var bits = s.f(2, "cdef_bits");
        var count = 1 << bits;
        var yPri = new int[count];
        var ySec = new int[count];
        var uvPri = new int[count];
        var uvSec = new int[count];
        var chroma = seq.colorConfig().numPlanes() > 1;
        for (var i = 0; i < count; i++) {
            yPri[i] = s.f(4, "cdef_y_pri_strength", i);
            ySec[i] = s.f(2, "cdef_y_sec_strength", i);
            if (chroma) {
                uvPri[i] = s.f(4, "cdef_uv_pri_strength", i);
                uvSec[i] = s.f(2, "cdef_uv_sec_strength", i);
            }
        }
        return new Cdef(damping, bits, Syntax.list(yPri), Syntax.list(ySec), Syntax.list(uvPri), Syntax.list(uvSec));
    }

    /// `lr_params()` (§5.9.20), when restoration may be on.
    private LoopRestoration loopRestoration() {
        var color = seq.colorConfig();
        var type = new int[3];
        var uses = false;
        var usesChroma = false;
        for (var i = 0; i < color.numPlanes(); i++) {
            type[i] = REMAP_LR_TYPE[s.f(2, "lr_type", i)];
            if (type[i] != LoopRestoration.RESTORE_NONE) {
                uses = true;
                usesChroma |= i > 0;
            }
        }
        var unitShift = 0;
        var uvShift = 0;
        if (uses) {
            unitShift = s.increment(seq.use128x128Superblock() ? 1 : 0, 2, "lr_unit_shift");
            if (color.subsamplingX() == 1 && color.subsamplingY() == 1 && usesChroma) {
                uvShift = s.f(1, "lr_uv_shift");
            }
        }
        return new LoopRestoration(Syntax.list(type), unitShift, uvShift);
    }

    /// `skip_mode_params()` (§5.9.22), when skip mode may be allowed: the two
    /// references skip mode uses, or none when it is not allowed.
    private int[] skipModeFrames(List<Integer> refFrameIdx, int orderHint) {
        var forwardIdx = -1;
        var backwardIdx = -1;
        var forwardHint = 0;
        var backwardHint = 0;
        for (var i = 0; i < REFS_PER_FRAME; i++) {
            var refHint = refs.slot(refFrameIdx.get(i)).orderHint();
            var distance = seq.relativeDistance(refHint, orderHint);
            if (distance < 0) {
                if (forwardIdx < 0 || seq.relativeDistance(refHint, forwardHint) > 0) {
                    forwardIdx = i;
                    forwardHint = refHint;
                }
            } else if (distance > 0) {
                if (backwardIdx < 0 || seq.relativeDistance(refHint, backwardHint) < 0) {
                    backwardIdx = i;
                    backwardHint = refHint;
                }
            }
        }
        if (forwardIdx < 0) {
            return new int[0];
        }
        if (backwardIdx >= 0) {
            return new int[] {
                LAST_FRAME + Math.min(forwardIdx, backwardIdx), LAST_FRAME + Math.max(forwardIdx, backwardIdx)
            };
        }
        var secondIdx = -1;
        var secondHint = 0;
        for (var i = 0; i < REFS_PER_FRAME; i++) {
            var refHint = refs.slot(refFrameIdx.get(i)).orderHint();
            if (seq.relativeDistance(refHint, forwardHint) < 0) {
                if (secondIdx < 0 || seq.relativeDistance(refHint, secondHint) > 0) {
                    secondIdx = i;
                    secondHint = refHint;
                }
            }
        }
        if (secondIdx < 0) {
            return new int[0];
        }
        return new int[] {LAST_FRAME + Math.min(forwardIdx, secondIdx), LAST_FRAME + Math.max(forwardIdx, secondIdx)};
    }

    /// `global_motion_params()` (§5.9.24) of an inter frame, coded against
    /// `previous`.
    private GlobalMotion globalMotion(GlobalMotion previous, boolean allowHighPrecisionMv) {
        var types = new ArrayList<Integer>(NUM_REF_FRAMES);
        var params = new ArrayList<List<Integer>>(NUM_REF_FRAMES);
        types.add(GlobalMotion.IDENTITY);
        params.add(GlobalMotion.IDENTITY_PARAMS);
        for (var ref = LAST_FRAME; ref <= References.ALTREF_FRAME; ref++) {
            var type = GlobalMotion.IDENTITY;
            if (s.flag("is_global", ref)) {
                if (s.flag("is_rot_zoom", ref)) {
                    type = GlobalMotion.ROTZOOM;
                } else {
                    type = s.flag("is_translation", ref) ? GlobalMotion.TRANSLATION : GlobalMotion.AFFINE;
                }
            }
            var p = GlobalMotion.IDENTITY_PARAMS.stream()
                    .mapToInt(Integer::intValue)
                    .toArray();
            if (type >= GlobalMotion.ROTZOOM) {
                p[2] = globalParam(type, ref, 2, previous, allowHighPrecisionMv);
                p[3] = globalParam(type, ref, 3, previous, allowHighPrecisionMv);
                if (type == GlobalMotion.AFFINE) {
                    p[4] = globalParam(type, ref, 4, previous, allowHighPrecisionMv);
                    p[5] = globalParam(type, ref, 5, previous, allowHighPrecisionMv);
                } else {
                    p[4] = -p[3];
                    p[5] = p[2];
                }
            }
            if (type >= GlobalMotion.TRANSLATION) {
                p[0] = globalParam(type, ref, 0, previous, allowHighPrecisionMv);
                p[1] = globalParam(type, ref, 1, previous, allowHighPrecisionMv);
            }
            types.add(type);
            params.add(Syntax.list(p));
        }
        return new GlobalMotion(types, params);
    }

    /// `read_global_param()` (§5.9.25).
    private int globalParam(int type, int ref, int idx, GlobalMotion previous, boolean allowHighPrecisionMv) {
        int absBits;
        int precBits;
        if (idx < 2 && type == GlobalMotion.TRANSLATION) {
            var low = allowHighPrecisionMv ? 0 : 1;
            absBits = GM_ABS_TRANS_ONLY_BITS - low;
            precBits = GM_TRANS_ONLY_PREC_BITS - low;
        } else if (idx < 2) {
            absBits = GM_ABS_TRANS_BITS;
            precBits = GM_TRANS_PREC_BITS;
        } else {
            absBits = GM_ABS_ALPHA_BITS;
            precBits = GM_ALPHA_PREC_BITS;
        }
        var precDiff = GlobalMotion.WARPEDMODEL_PREC_BITS - precBits;
        var round = idx % 3 == 2 ? 1 << GlobalMotion.WARPEDMODEL_PREC_BITS : 0;
        var sub = idx % 3 == 2 ? 1 << precBits : 0;
        var mx = 1 << absBits;
        var r = (previous.params(ref, idx) >> precDiff) - sub;
        var coded = s.subexp(2 * mx + 1, "gm_params", ref, idx);
        return (decodeSignedSubexpWithRef(coded, -mx, mx + 1, r) << precDiff) + round;
    }

    /// `decode_signed_subexp_with_ref()` (§5.9.26) of the value `decode_subexp`
    /// read, `coded`.
    static int decodeSignedSubexpWithRef(int coded, int low, int high, int r) {
        var mx = high - low;
        var reference = r - low;
        int x;
        if ((reference << 1) <= mx) {
            x = inverseRecenter(reference, coded);
        } else {
            x = mx - 1 - inverseRecenter(mx - 1 - reference, coded);
        }
        return x + low;
    }

    /// `inverse_recenter(r, v)` (§5.9.29).
    static int inverseRecenter(int r, int v) {
        if (v > 2 * r) {
            return v;
        } else if ((v & 1) != 0) {
            return r - ((v + 1) >> 1);
        } else {
            return r + (v >> 1);
        }
    }

    /// `film_grain_params()` (§5.9.30).
    private FilmGrain filmGrain(int frameType, boolean showFrame, boolean showable) {
        if (!seq.filmGrainParamsPresent() || (!showFrame && !showable)) {
            return FilmGrain.NONE;
        }
        if (!s.flag("apply_grain")) {
            return FilmGrain.NONE;
        }
        var seed = s.f(16, "grain_seed");
        var update = frameType != INTER_FRAME || s.flag("update_grain");
        if (!update) {
            var index = s.f(3, "film_grain_params_ref_idx");
            return refs.slot(index).header().filmGrain().loadedFrom(index, seed);
        }
        var color = seq.colorConfig();
        var numY = s.f(4, "num_y_points");
        if (numY > 14) {
            throw new IllegalArgumentException("num_y_points " + numY);
        }
        var yValue = new int[numY];
        var yScaling = new int[numY];
        points(numY, yValue, yScaling, "point_y_value", "point_y_scaling");
        var fromLuma = !color.monoChrome() && s.flag("chroma_scaling_from_luma");
        var numCb = 0;
        var numCr = 0;
        int[] cbValue = {};
        int[] cbScaling = {};
        int[] crValue = {};
        int[] crScaling = {};
        if (!color.monoChrome()
                && !fromLuma
                && !(color.subsamplingX() == 1 && color.subsamplingY() == 1 && numY == 0)) {
            numCb = s.f(4, "num_cb_points");
            if (numCb > 10) {
                throw new IllegalArgumentException("num_cb_points " + numCb);
            }
            cbValue = new int[numCb];
            cbScaling = new int[numCb];
            points(numCb, cbValue, cbScaling, "point_cb_value", "point_cb_scaling");
            numCr = s.f(4, "num_cr_points");
            if (numCr > 10) {
                throw new IllegalArgumentException("num_cr_points " + numCr);
            }
            crValue = new int[numCr];
            crScaling = new int[numCr];
            points(numCr, crValue, crScaling, "point_cr_value", "point_cr_scaling");
        }
        var scalingMinus8 = s.f(2, "grain_scaling_minus_8");
        var lag = s.f(2, "ar_coeff_lag");
        var numPosLuma = 2 * lag * (lag + 1);
        var numPosChroma = numY > 0 ? numPosLuma + 1 : numPosLuma;
        var yCoeffs = numY > 0 ? coefficients(numPosLuma, "ar_coeffs_y_plus_128") : new int[0];
        var cbCoeffs = fromLuma || numCb > 0 ? coefficients(numPosChroma, "ar_coeffs_cb_plus_128") : new int[0];
        var crCoeffs = fromLuma || numCr > 0 ? coefficients(numPosChroma, "ar_coeffs_cr_plus_128") : new int[0];
        var shiftMinus6 = s.f(2, "ar_coeff_shift_minus_6");
        var scaleShift = s.f(2, "grain_scale_shift");
        var cbMult = 0;
        var cbLumaMult = 0;
        var cbOffset = 0;
        if (numCb > 0) {
            cbMult = s.f(8, "cb_mult");
            cbLumaMult = s.f(8, "cb_luma_mult");
            cbOffset = s.f(9, "cb_offset");
        }
        var crMult = 0;
        var crLumaMult = 0;
        var crOffset = 0;
        if (numCr > 0) {
            crMult = s.f(8, "cr_mult");
            crLumaMult = s.f(8, "cr_luma_mult");
            crOffset = s.f(9, "cr_offset");
        }
        var overlap = s.flag("overlap_flag");
        var clip = s.flag("clip_to_restricted_range");
        return new FilmGrain(
                true,
                seed,
                true,
                0,
                Syntax.list(yValue),
                Syntax.list(yScaling),
                fromLuma,
                Syntax.list(cbValue),
                Syntax.list(cbScaling),
                Syntax.list(crValue),
                Syntax.list(crScaling),
                scalingMinus8,
                lag,
                Syntax.list(yCoeffs),
                Syntax.list(cbCoeffs),
                Syntax.list(crCoeffs),
                shiftMinus6,
                scaleShift,
                cbMult,
                cbLumaMult,
                cbOffset,
                crMult,
                crLumaMult,
                crOffset,
                overlap,
                clip);
    }

    /// `count` points of a scaling function, each value above the last.
    private void points(int count, int[] values, int[] scalings, String valueName, String scalingName) {
        for (var i = 0; i < count; i++) {
            values[i] = s.f(8, valueName, i);
            if (i > 0 && values[i] <= values[i - 1]) {
                throw new IllegalArgumentException(valueName + " not increasing");
            }
            scalings[i] = s.f(8, scalingName, i);
        }
    }

    private int[] coefficients(int count, String name) {
        var values = new int[count];
        for (var i = 0; i < count; i++) {
            values[i] = s.f(8, name, i);
        }
        return values;
    }
}
