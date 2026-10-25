package dev.goldberry.media.bitstream.vp9;

import java.util.ArrayList;
import java.util.List;

/// What one VP9 frame leaves for the next one's header to be read against:
/// the eight reference slots, the colour last stated, the loop filter's deltas
/// and the segment features in force, and what the motion vectors of the
/// previous frame may be used for.
///
/// Start from [#initial], read a frame with
/// [UncompressedHeader#parse(byte[], StreamState)], and carry on with
/// [#after] of its header, frame after frame in decoding order. A frame that
/// shows an existing one leaves everything as it was.
///
/// @param slots            the eight reference slots, [Slot#EMPTY] until a
///                         frame refreshes them
/// @param color            the colour of the last keyframe or intra-only
///                         frame, which every frame between them shares
/// @param refDeltas        the loop filter's reference deltas in force
/// @param modeDeltas       its mode deltas in force
/// @param absOrDeltaUpdate `segmentation_abs_or_delta_update` in force
/// @param features         the segment features in force
/// @param lastWidth        the width of the last frame decoded, 0 before one
/// @param lastHeight       its height
/// @param lastShowFrame    whether it was shown
/// @param lastIntraOnly    whether it was an intra-only frame
/// @param lastKeyFrame     whether it was a keyframe
public record StreamState(
        List<Slot> slots,
        ColorConfig color,
        List<Integer> refDeltas,
        List<Integer> modeDeltas,
        boolean absOrDeltaUpdate,
        SegmentFeatures features,
        int lastWidth,
        int lastHeight,
        boolean lastShowFrame,
        boolean lastIntraOnly,
        boolean lastKeyFrame) {

    /// The reference slots: `NUM_REF_FRAMES`.
    public static final int NUM_REF_FRAMES = 8;

    /// A reference slot: the size and format of the frame last put in it,
    /// which a frame that names it as a reference may take its own size
    /// from and must match in format.
    ///
    /// @param width        `RefFrameWidth`, 0 for a slot never refreshed
    /// @param height       `RefFrameHeight`
    /// @param renderWidth  the width it was to be shown at
    /// @param renderHeight the height it was to be shown at
    /// @param subsamplingX `RefSubsamplingX`
    /// @param subsamplingY `RefSubsamplingY`
    /// @param bitDepth     `RefBitDepth`
    public record Slot(
            int width,
            int height,
            int renderWidth,
            int renderHeight,
            int subsamplingX,
            int subsamplingY,
            int bitDepth) {

        /// A slot no frame has been put in.
        public static final Slot EMPTY = new Slot(0, 0, 0, 0, 0, 0, 0);

        /// Whether a frame has been put in it.
        public boolean filled() {
            return width > 0;
        }
    }

    public StreamState {
        slots = List.copyOf(slots);
        refDeltas = List.copyOf(refDeltas);
        modeDeltas = List.copyOf(modeDeltas);
        if (slots.size() != NUM_REF_FRAMES) {
            throw new IllegalArgumentException(slots.size() + " reference slots");
        }
    }

    /// Before the first frame: every slot empty, everything at its default.
    public static StreamState initial() {
        return new StreamState(
                List.of(Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY, Slot.EMPTY),
                ColorConfig.PROFILE_0_INTRA_ONLY,
                LoopFilter.DEFAULT_REF_DELTAS,
                LoopFilter.DEFAULT_MODE_DELTAS,
                false,
                SegmentFeatures.NONE,
                0,
                0,
                false,
                false,
                false);
    }

    /// What is left once the frame of `header`, read against this state, is
    /// decoded: the slots it refreshes hold it, and the values it sets are in
    /// force.
    public StreamState after(UncompressedHeader header) {
        if (header.showExistingFrame()) {
            return this;
        }
        var color = header.color();
        var slot = new Slot(
                header.frameWidth(),
                header.frameHeight(),
                header.renderWidth(),
                header.renderHeight(),
                color.subsamplingX(),
                color.subsamplingY(),
                color.bitDepth());
        var next = new ArrayList<>(slots);
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            if ((header.refreshFrameFlags() & (1 << i)) != 0) {
                next.set(i, slot);
            }
        }
        return new StreamState(
                next,
                color,
                header.loopFilter().refDeltas(),
                header.loopFilter().modeDeltas(),
                header.segmentation().absOrDeltaUpdate(),
                header.segmentation().features(),
                header.frameWidth(),
                header.frameHeight(),
                header.showFrame(),
                header.intraOnly(),
                header.isKeyFrame());
    }
}
