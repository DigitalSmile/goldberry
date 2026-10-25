package dev.goldberry.media.bitstream.av1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// The decoder state a frame header is read against: the eight reference
/// slots, as the reference frame update process (§7.20) leaves them.
///
/// A frame header reads the slots in force and does not change them; once the
/// frame is decoded, [#updated] with its header gives the slots the next frame
/// reads. That covers what the header itself takes away from the slots before
/// it stores the frame: every slot when a key frame is shown, the slots whose
/// frame id is too far from the frame's own, and the slots an error resilient
/// frame says do not hold the frame they should.
///
/// @param slots the eight slots, by `ref_frame_idx`
public record References(List<ReferenceFrame> slots) {

    /// `NUM_REF_FRAMES`: the slots.
    public static final int NUM_REF_FRAMES = 8;

    /// `REFS_PER_FRAME`: the references a frame names, `LAST_FRAME` to
    /// `ALTREF_FRAME`.
    public static final int REFS_PER_FRAME = 7;

    /// `refresh_frame_flags` for every slot.
    public static final int ALL_FRAMES = (1 << NUM_REF_FRAMES) - 1;

    public static final int INTRA_FRAME = 0;
    public static final int LAST_FRAME = 1;
    public static final int LAST2_FRAME = 2;
    public static final int LAST3_FRAME = 3;
    public static final int GOLDEN_FRAME = 4;
    public static final int BWDREF_FRAME = 5;
    public static final int ALTREF2_FRAME = 6;
    public static final int ALTREF_FRAME = 7;

    /// No frame in any slot: the state before the first frame.
    public static final References NONE = new References(Collections.nCopies(NUM_REF_FRAMES, ReferenceFrame.NONE));

    public References {
        slots = List.copyOf(slots);
        if (slots.size() != NUM_REF_FRAMES) {
            throw new IllegalArgumentException(slots.size() + " reference slots");
        }
    }

    /// Slot `index`.
    public ReferenceFrame slot(int index) {
        return slots.get(index);
    }

    /// The slots once the frame `header` was read against is done: the
    /// reference frame update process (§7.20), after what the header took away
    /// from the slots. A shown key frame (`show_existing_frame` of a key frame)
    /// is loaded (§7.21) and stored in every slot, no longer showable.
    public References updated(UncompressedHeader header) {
        return switch (header) {
            case ShowExistingFrame shown -> {
                if (shown.frameType() != FrameHeader.KEY_FRAME) {
                    yield this;
                }
                var slot = slot(shown.frameToShowMapIdx());
                yield new References(Collections.nCopies(
                        NUM_REF_FRAMES, new ReferenceFrame(true, slot.orderHint(), false, slot.frame())));
            }
            case FrameHeader frame -> {
                var refs = this;
                var sequence = frame.sequenceHeader();
                if (frame.frameType() == FrameHeader.KEY_FRAME && frame.showFrame()) {
                    refs = NONE;
                }
                if (sequence.frameIdNumbersPresent()) {
                    refs = refs.markedByFrameId(frame.currentFrameId(), sequence);
                }
                refs = refs.expecting(frame.refOrderHint());
                var stored = ReferenceFrame.of(frame);
                var next = new ArrayList<>(refs.slots);
                for (var i = 0; i < NUM_REF_FRAMES; i++) {
                    if ((frame.refreshFrameFlags() & (1 << i)) != 0) {
                        next.set(i, stored);
                    }
                }
                yield new References(next);
            }
        };
    }

    /// The slots after `mark_ref_frames(idLen)` (§5.9.4): a slot whose frame id
    /// is not within `DeltaFrameId` before `currentFrameId` is invalid.
    References markedByFrameId(int currentFrameId, SequenceHeader sequence) {
        var idLength = sequence.frameIdLength();
        var diffLength = sequence.deltaFrameIdLengthMinus2() + 2;
        var next = new ArrayList<>(slots);
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            var id = slots.get(i).frameId();
            boolean stale;
            if (currentFrameId > (1 << diffLength)) {
                stale = id > currentFrameId || id < currentFrameId - (1 << diffLength);
            } else {
                stale = id > currentFrameId && id < (1 << idLength) + currentFrameId - (1 << diffLength);
            }
            if (stale) {
                next.set(i, slots.get(i).invalidated());
            }
        }
        return new References(next);
    }

    /// The slots after an error resilient frame's `ref_order_hint`: a slot
    /// that does not hold the order hint expected of it is invalid, and is
    /// taken to hold that order hint from then on, as libaom and dav1d take it.
    References expecting(List<Integer> refOrderHint) {
        Objects.requireNonNull(refOrderHint, "refOrderHint");
        if (refOrderHint.size() != NUM_REF_FRAMES) {
            throw new IllegalArgumentException(refOrderHint.size() + " reference order hints");
        }
        var next = new ArrayList<>(slots);
        var changed = false;
        for (var i = 0; i < NUM_REF_FRAMES; i++) {
            int hint = refOrderHint.get(i);
            if (hint != slots.get(i).orderHint()) {
                next.set(i, slots.get(i).invalidated(hint));
                changed = true;
            }
        }
        return changed ? new References(next) : this;
    }
}
