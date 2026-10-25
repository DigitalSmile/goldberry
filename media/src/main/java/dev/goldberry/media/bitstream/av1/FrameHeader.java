package dev.goldberry.media.bitstream.av1;

import java.util.List;
import java.util.Objects;

/// An AV1 frame header (§5.9) of a frame that is decoded: the uncompressed
/// header with every value the decoding process works out from it and from
/// the references, as Vulkan Video's `StdVideoDecodeAV1PictureInfo` and the
/// structs it points to want them.
///
/// What the frame takes from its primary reference when it does not code it
/// (`load_previous()`: loop filter deltas, segmentation features, the global
/// motion the new one is coded against) and film grain it takes from a
/// reference are here as they apply to this frame, so a later frame that
/// refers to this one loads them from here.
///
/// @param sequenceHeader           the sequence header in force
/// @param frameType                `frame_type`: [#KEY_FRAME],
///                                 [#INTER_FRAME], [#INTRA_ONLY_FRAME] or
///                                 [#SWITCH_FRAME]
/// @param showFrame                `show_frame`
/// @param showableFrame            `showable_frame`
/// @param framePresentationTime    `frame_presentation_time`, 0 when not
///                                 coded
/// @param errorResilientMode       `error_resilient_mode`
/// @param disableCdfUpdate         `disable_cdf_update`
/// @param allowScreenContentTools  `allow_screen_content_tools`
/// @param forceIntegerMv           `force_integer_mv`, set for an intra frame
/// @param currentFrameId           `current_frame_id`, 0 without frame ids
/// @param frameSizeOverride        `frame_size_override_flag`
/// @param orderHint                `order_hint`
/// @param primaryRefFrame          `primary_ref_frame`: the reference, by its
///                                 index in [#refFrameIdx], whose state the
///                                 frame starts from; [#PRIMARY_REF_NONE]
///                                 for none
/// @param bufferRemovalTimePresent `buffer_removal_time_present_flag`
/// @param bufferRemovalTimes       `buffer_removal_time` per operating point,
///                                 0 where not coded; empty without a
///                                 decoder model
/// @param refreshFrameFlags        `refresh_frame_flags`: a bit per reference
///                                 slot the frame is stored in
/// @param refOrderHint             `ref_order_hint` per slot: as coded by an
///                                 error resilient frame, else the order hint
///                                 the slot holds
/// @param frameSize                its size
/// @param allowIntrabc             `allow_intrabc`
/// @param frameRefsShortSignaling  `frame_refs_short_signaling`
/// @param lastFrameIdx             `last_frame_idx`, 0 unless short signaling
/// @param goldFrameIdx             `gold_frame_idx`, 0 unless short signaling
/// @param refFrameIdx              `ref_frame_idx`: the slot of each of the
///                                 seven references, `LAST_FRAME` first, as
///                                 coded or as `set_frame_refs()` chose them;
///                                 empty for an intra frame
/// @param expectedFrameIds         `expectedFrameId` of each of the seven
///                                 references; empty without frame ids or for
///                                 an intra frame
/// @param allowHighPrecisionMv     `allow_high_precision_mv`
/// @param interpolationFilter      `interpolation_filter`: 0 to 3, or
///                                 [#SWITCHABLE] when `is_filter_switchable`
/// @param isMotionModeSwitchable   `is_motion_mode_switchable`
/// @param useRefFrameMvs           `use_ref_frame_mvs`
/// @param orderHints               `OrderHints` by reference name, from
///                                 `INTRA_FRAME`: each reference's order hint;
///                                 all 0 for an intra frame
/// @param refFrameSignBias         `RefFrameSignBias` as a bit per reference
///                                 name: set for a reference after this frame
///                                 in output order
/// @param disableFrameEndUpdateCdf `disable_frame_end_update_cdf`
/// @param tileInfo                 its tiles
/// @param quantization             its quantizers
/// @param segmentation             its segmentation, the features loaded from
///                                 the primary reference when not coded
/// @param deltaQPresent            `delta_q_present`
/// @param deltaQRes                `delta_q_res`
/// @param deltaLfPresent           `delta_lf_present`
/// @param deltaLfRes               `delta_lf_res`
/// @param deltaLfMulti             `delta_lf_multi`
/// @param codedLossless            `CodedLossless`: every segment lossless
/// @param allLossless              `AllLossless`: and not downscaled by
///                                 superres
/// @param loopFilter               its deblocking filter, the deltas loaded
///                                 from the primary reference when not coded
/// @param cdef                     its CDEF
/// @param loopRestoration          its loop restoration
/// @param txMode                   `TxMode`: [#ONLY_4X4], 1 (`TX_MODE_LARGEST`)
///                                 or 2 (`TX_MODE_SELECT`)
/// @param referenceSelect          `reference_select`
/// @param skipModePresent          `skip_mode_present`
/// @param skipModeFrames           `SkipModeFrame`: the two reference names
///                                 skip mode predicts from; 0, 0 when it is not
///                                 allowed
/// @param allowWarpedMotion        `allow_warped_motion`
/// @param reducedTxSet             `reduced_tx_set`
/// @param globalMotion             each reference's warp
/// @param filmGrain                its film grain
public record FrameHeader(
        SequenceHeader sequenceHeader,
        int frameType,
        boolean showFrame,
        boolean showableFrame,
        long framePresentationTime,
        boolean errorResilientMode,
        boolean disableCdfUpdate,
        boolean allowScreenContentTools,
        boolean forceIntegerMv,
        int currentFrameId,
        boolean frameSizeOverride,
        int orderHint,
        int primaryRefFrame,
        boolean bufferRemovalTimePresent,
        List<Long> bufferRemovalTimes,
        int refreshFrameFlags,
        List<Integer> refOrderHint,
        FrameSize frameSize,
        boolean allowIntrabc,
        boolean frameRefsShortSignaling,
        int lastFrameIdx,
        int goldFrameIdx,
        List<Integer> refFrameIdx,
        List<Integer> expectedFrameIds,
        boolean allowHighPrecisionMv,
        int interpolationFilter,
        boolean isMotionModeSwitchable,
        boolean useRefFrameMvs,
        List<Integer> orderHints,
        int refFrameSignBias,
        boolean disableFrameEndUpdateCdf,
        TileInfo tileInfo,
        Quantization quantization,
        Segmentation segmentation,
        boolean deltaQPresent,
        int deltaQRes,
        boolean deltaLfPresent,
        int deltaLfRes,
        boolean deltaLfMulti,
        boolean codedLossless,
        boolean allLossless,
        LoopFilter loopFilter,
        Cdef cdef,
        LoopRestoration loopRestoration,
        int txMode,
        boolean referenceSelect,
        boolean skipModePresent,
        List<Integer> skipModeFrames,
        boolean allowWarpedMotion,
        boolean reducedTxSet,
        GlobalMotion globalMotion,
        FilmGrain filmGrain)
        implements UncompressedHeader {

    public static final int KEY_FRAME = 0;
    public static final int INTER_FRAME = 1;
    public static final int INTRA_ONLY_FRAME = 2;
    public static final int SWITCH_FRAME = 3;

    /// `PRIMARY_REF_NONE`: the frame starts from nothing a reference left.
    public static final int PRIMARY_REF_NONE = 7;

    /// `interpolation_filter` when each block chooses its own.
    public static final int SWITCHABLE = 4;

    public static final int ONLY_4X4 = 0;

    public FrameHeader {
        Objects.requireNonNull(sequenceHeader, "sequenceHeader");
        bufferRemovalTimes = List.copyOf(bufferRemovalTimes);
        refOrderHint = List.copyOf(refOrderHint);
        Objects.requireNonNull(frameSize, "frameSize");
        refFrameIdx = List.copyOf(refFrameIdx);
        expectedFrameIds = List.copyOf(expectedFrameIds);
        orderHints = List.copyOf(orderHints);
        Objects.requireNonNull(tileInfo, "tileInfo");
        Objects.requireNonNull(quantization, "quantization");
        Objects.requireNonNull(segmentation, "segmentation");
        Objects.requireNonNull(loopFilter, "loopFilter");
        Objects.requireNonNull(cdef, "cdef");
        Objects.requireNonNull(loopRestoration, "loopRestoration");
        skipModeFrames = List.copyOf(skipModeFrames);
        Objects.requireNonNull(globalMotion, "globalMotion");
        Objects.requireNonNull(filmGrain, "filmGrain");
    }

    /// `FrameIsIntra`: a key frame or an intra-only frame, which refers to
    /// nothing.
    public boolean frameIsIntra() {
        return frameType == KEY_FRAME || frameType == INTRA_ONLY_FRAME;
    }

    /// `is_filter_switchable`.
    public boolean isFilterSwitchable() {
        return interpolationFilter == SWITCHABLE;
    }

    /// Whether `RefFrameSignBias[refFrame]` is set.
    public boolean refFrameSignBias(int refFrame) {
        return (refFrameSignBias & (1 << refFrame)) != 0;
    }
}
