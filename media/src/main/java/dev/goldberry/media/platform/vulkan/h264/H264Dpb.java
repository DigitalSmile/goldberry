package dev.goldberry.media.platform.vulkan.h264;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.goldberry.media.bitstream.h264.SequenceParameterSet;
import dev.goldberry.media.bitstream.h264.SliceHeader;

/// The decoded picture buffer of an H.264 stream as a decoder handed whole
/// pictures keeps it (H.264 §8.2.1, §8.2.4, §8.2.5): each picture's order
/// count, which pictures stay as references and in which slot, and which are
/// due for output.
///
/// Frame pictures only. A reference holds a slot of the Vulkan picture
/// buffer; a picture waiting for output does not, since it was copied out as
/// it was decoded, so the slots are the references and the picture being
/// decoded.
///
/// @param <P> what a decoded picture is kept as until it is output
final class H264Dpb<P> {

    /// A reference picture: its slot, `FrameNum`, order counts, and whether it
    /// is long-term with its `LongTermFrameIdx`.
    record Reference(int slot, int frameNum, int top, int bottom, boolean longTerm, int longTermFrameIdx) {

        /// `FrameNumWrap` against the current `frame_num` (8-27).
        int frameNumWrap(int currentFrameNum, int maxFrameNum) {
            return frameNum > currentFrameNum ? frameNum - maxFrameNum : frameNum;
        }
    }

    /// The order counts of a picture about to be decoded.
    ///
    /// @param top    `TopFieldOrderCnt`
    /// @param bottom `BottomFieldOrderCnt`
    record Order(int top, int bottom) {

        /// `PicOrderCnt` of a frame: the smaller.
        int frame() {
            return Math.min(top, bottom);
        }
    }

    /// The slot of a frame a gap in `frame_num` stands in for (§8.2.5.2):
    /// none, for nothing is decoded into it.
    static final int NO_SLOT = -1;

    /// A picture waiting for output.
    private record Waiting<P>(int poc, long sequence, P picture) {}

    private final List<Reference> references = new ArrayList<>();
    private final List<Waiting<P>> waiting = new ArrayList<>();
    private final int slots;
    private int maxLongTermFrameIdx = -1;
    private long sequence;

    // §8.2.1's state from the pictures before.
    private int prevPocMsb;
    private int prevPocLsb;
    private int prevFrameNumOffset;
    private int prevFrameNum;
    private boolean prevHadMmco5;
    private int prevRefFrameNum;

    /// A buffer of `slots` slots.
    H264Dpb(int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException(slots + " slots");
        }
        this.slots = slots;
    }

    /// The references in force, for the picture about to be decoded: those in
    /// a slot, without the frames a gap in `frame_num` stood in for.
    List<Reference> references() {
        return references.stream().filter(r -> r.slot() != NO_SLOT).toList();
    }

    /// The order counts of the picture `slice` starts (§8.2.1), with `refIdc`
    /// its `nal_ref_idc`.
    Order order(SequenceParameterSet sps, SliceHeader slice) {
        var idr = slice.nal().isIdr();
        var reference = slice.nal().refIdc() != 0;
        var maxFrameNum = sps.maxFrameNum();
        int frameNumOffset;
        if (idr) {
            frameNumOffset = 0;
        } else {
            var previous = prevHadMmco5 ? 0 : prevFrameNumOffset;
            frameNumOffset = prevFrameNum > slice.frameNum() ? previous + maxFrameNum : previous;
        }
        return switch (sps.picOrderCntType()) {
            case 0 -> {
                int previousMsb;
                int previousLsb;
                if (idr) {
                    previousMsb = 0;
                    previousLsb = 0;
                } else {
                    previousMsb = prevPocMsb;
                    previousLsb = prevPocLsb;
                }
                var max = sps.maxPocLsb();
                var lsb = slice.pocLsb();
                int msb;
                if (lsb < previousLsb && previousLsb - lsb >= max / 2) {
                    msb = previousMsb + max;
                } else if (lsb > previousLsb && lsb - previousLsb > max / 2) {
                    msb = previousMsb - max;
                } else {
                    msb = previousMsb;
                }
                var top = msb + lsb;
                yield new Order(top, top + slice.deltaPocBottom());
            }
            case 1 -> {
                var cycle = sps.offsetForRefFrame();
                var absFrameNum = cycle.isEmpty() ? 0 : frameNumOffset + slice.frameNum();
                if (!reference && absFrameNum > 0) {
                    absFrameNum--;
                }
                var expected = 0;
                if (absFrameNum > 0) {
                    var cycleCount = (absFrameNum - 1) / cycle.size();
                    var inCycle = (absFrameNum - 1) % cycle.size();
                    var delta = 0;
                    for (var offset : cycle) {
                        delta += offset;
                    }
                    expected = cycleCount * delta;
                    for (var i = 0; i <= inCycle; i++) {
                        expected += cycle.get(i);
                    }
                }
                if (!reference) {
                    expected += sps.offsetForNonRefPic();
                }
                var top = expected + slice.deltaPoc0();
                yield new Order(top, top + sps.offsetForTopToBottomField() + slice.deltaPoc1());
            }
            default -> {
                var temp = idr ? 0 : 2 * (frameNumOffset + slice.frameNum()) - (reference ? 0 : 1);
                yield new Order(temp, temp);
            }
        };
    }

    /// A free slot for the picture about to be decoded: one no reference holds.
    ///
    /// @throws IllegalStateException when every slot holds a reference, which a
    ///                               conformant stream never makes so
    int freeSlot() {
        for (var slot = 0; slot < slots; slot++) {
            var taken = false;
            for (var reference : references) {
                taken |= reference.slot() == slot;
            }
            if (!taken) {
                return slot;
            }
        }
        throw new IllegalStateException("every one of " + slots + " slots holds a reference");
    }

    /// Records the picture `slice` started, decoded into `slot` with `order`, as
    /// waiting for output and, if it is one, a reference; returns the pictures
    /// now due for output, in order, with at most `reorder` left waiting.
    List<P> decoded(SequenceParameterSet sps, SliceHeader slice, Order order, int slot, P picture, int reorder) {
        var due = new ArrayList<P>();
        var idr = slice.nal().isIdr();
        var mmco5 = slice.operations().stream().anyMatch(operation -> operation.operation() == 5);
        if (idr || mmco5) {
            // A new sequence of order counts: what waited goes out first.
            if (!(idr && slice.noOutputOfPriorPics())) {
                due.addAll(drainWaiting());
            }
            waiting.clear();
        }
        var top = order.top();
        var bottom = order.bottom();
        if (mmco5) {
            var temp = Math.min(top, bottom);
            top -= temp;
            bottom -= temp;
        }
        if (!idr && sps.gapsInFrameNumAllowed()) {
            fillGap(sps, slice);
        }
        if (slice.nal().refIdc() != 0) {
            mark(sps, slice, slot, top, bottom);
            if (sps.picOrderCntType() == 0) {
                prevPocMsb = mmco5 ? 0 : top - slice.pocLsb();
                prevPocLsb = mmco5 ? top : slice.pocLsb();
            }
            prevRefFrameNum = mmco5 ? 0 : slice.frameNum();
        }
        prevFrameNumOffset = mmco5 ? 0 : frameNumOffset(sps, slice);
        prevFrameNum = mmco5 ? 0 : slice.frameNum();
        prevHadMmco5 = mmco5;
        waiting.add(new Waiting<>(Math.min(top, bottom), sequence++, picture));
        waiting.sort(Comparator.comparingInt((Waiting<P> w) -> w.poc()).thenComparingLong(Waiting::sequence));
        while (waiting.size() > reorder) {
            due.add(waiting.removeFirst().picture());
        }
        return due;
    }

    /// Every picture still waiting, in order.
    List<P> drainWaiting() {
        var all = new ArrayList<P>(waiting.size());
        for (var w : waiting) {
            all.add(w.picture());
        }
        waiting.clear();
        return all;
    }

    /// Forgets everything: a seek, after which the next picture is an IDR or a
    /// recovery point.
    void reset() {
        references.clear();
        waiting.clear();
        maxLongTermFrameIdx = -1;
        prevPocMsb = 0;
        prevPocLsb = 0;
        prevFrameNumOffset = 0;
        prevFrameNum = 0;
        prevHadMmco5 = false;
        prevRefFrameNum = 0;
    }

    /// The frames a gap in `frame_num` before `slice` leaves out, each marked a
    /// short-term reference by the sliding window, in no slot (§8.2.5.2): they
    /// push older references out as the frames they stand for would have.
    private void fillGap(SequenceParameterSet sps, SliceHeader slice) {
        var maxFrameNum = sps.maxFrameNum();
        var missing = (prevRefFrameNum + 1) % maxFrameNum;
        while (slice.frameNum() != prevRefFrameNum && missing != slice.frameNum()) {
            slide(sps, missing);
            references.add(new Reference(NO_SLOT, missing, 0, 0, false, 0));
            missing = (missing + 1) % maxFrameNum;
        }
    }

    /// The sliding window (§8.2.5.3) before a frame of `frameNum` is marked: the
    /// oldest short-term reference goes when the buffer is full.
    private void slide(SequenceParameterSet sps, int frameNum) {
        var maxReferences = Math.max(sps.maxNumRefFrames(), 1);
        if (references.size() >= maxReferences) {
            var maxFrameNum = sps.maxFrameNum();
            references.stream()
                    .filter(r -> !r.longTerm())
                    .min(Comparator.comparingInt(r -> r.frameNumWrap(frameNum, maxFrameNum)))
                    .ifPresent(references::remove);
        }
    }

    private int frameNumOffset(SequenceParameterSet sps, SliceHeader slice) {
        if (slice.nal().isIdr()) {
            return 0;
        }
        var previous = prevHadMmco5 ? 0 : prevFrameNumOffset;
        return prevFrameNum > slice.frameNum() ? previous + sps.maxFrameNum() : previous;
    }

    /// The decoded reference picture marking process (§8.2.5) for the current
    /// picture in `slot`.
    private void mark(SequenceParameterSet sps, SliceHeader slice, int slot, int top, int bottom) {
        var frameNum = slice.frameNum();
        if (slice.nal().isIdr()) {
            references.clear();
            if (slice.longTermReference()) {
                maxLongTermFrameIdx = 0;
                references.add(new Reference(slot, frameNum, top, bottom, true, 0));
            } else {
                maxLongTermFrameIdx = -1;
                references.add(new Reference(slot, frameNum, top, bottom, false, 0));
            }
            return;
        }
        var currentLongTerm = false;
        var currentIdx = 0;
        if (slice.adaptiveRefPicMarking()) {
            var maxFrameNum = sps.maxFrameNum();
            for (var operation : slice.operations()) {
                switch (operation.operation()) {
                    case 1 -> {
                        var picNum = frameNum - operation.differenceOfPicNums();
                        references.removeIf(r -> !r.longTerm() && r.frameNumWrap(frameNum, maxFrameNum) == picNum);
                    }
                    case 2 ->
                        references.removeIf(r -> r.longTerm() && r.longTermFrameIdx() == operation.longTermPicNum());
                    case 3 -> {
                        var picNum = frameNum - operation.differenceOfPicNums();
                        var idx = operation.longTermFrameIdx();
                        references.removeIf(r -> r.longTerm() && r.longTermFrameIdx() == idx);
                        for (var i = 0; i < references.size(); i++) {
                            var r = references.get(i);
                            if (!r.longTerm() && r.frameNumWrap(frameNum, maxFrameNum) == picNum) {
                                references.set(
                                        i, new Reference(r.slot(), r.frameNum(), r.top(), r.bottom(), true, idx));
                            }
                        }
                    }
                    case 4 -> {
                        maxLongTermFrameIdx = operation.maxLongTermFrameIdxPlus1() - 1;
                        references.removeIf(r -> r.longTerm() && r.longTermFrameIdx() > maxLongTermFrameIdx);
                    }
                    case 5 -> {
                        references.clear();
                        maxLongTermFrameIdx = -1;
                    }
                    case 6 -> {
                        var idx = operation.longTermFrameIdx();
                        references.removeIf(r -> r.longTerm() && r.longTermFrameIdx() == idx);
                        currentLongTerm = true;
                        currentIdx = idx;
                    }
                    default ->
                        throw new IllegalArgumentException(
                                "memory_management_control_operation " + operation.operation());
                }
            }
        } else {
            slide(sps, frameNum);
        }
        var mmco5 = slice.operations().stream().anyMatch(operation -> operation.operation() == 5);
        references.add(new Reference(slot, mmco5 ? 0 : frameNum, top, bottom, currentLongTerm, currentIdx));
    }
}
