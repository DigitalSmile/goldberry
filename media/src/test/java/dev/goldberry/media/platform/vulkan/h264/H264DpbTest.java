package dev.goldberry.media.platform.vulkan.h264;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.bitstream.h264.NalHeader;
import dev.goldberry.media.bitstream.h264.SequenceParameterSet;
import dev.goldberry.media.bitstream.h264.SliceHeader;
import dev.goldberry.media.bitstream.h264.SliceHeader.MemoryManagement;

/// The H.264 picture buffer: order counts, reference marking and output order,
/// against hand-made headers.
@DisplayName("the H.264 decoded picture buffer")
class H264DpbTest {

    /// An SPS of `pocType`, with `maxRefs` references, 16 values of `frame_num`
    /// and of `pic_order_cnt_lsb`.
    private static SequenceParameterSet sps(int pocType, int maxRefs, boolean gaps) {
        return new SequenceParameterSet(
                100,
                0,
                30,
                0,
                1,
                false,
                0,
                0,
                false,
                Optional.empty(),
                0,
                pocType,
                0,
                false,
                0,
                0,
                List.of(),
                maxRefs,
                gaps,
                9,
                5,
                true,
                false,
                true,
                false,
                0,
                0,
                0,
                0,
                Optional.empty());
    }

    private static SliceHeader slice(
            boolean idr, int refIdc, int frameNum, int pocLsb, List<MemoryManagement> operations) {
        return slice(idr, refIdc, frameNum, pocLsb, operations, false);
    }

    private static SliceHeader slice(
            boolean idr, int refIdc, int frameNum, int pocLsb, List<MemoryManagement> operations, boolean noOutput) {
        return new SliceHeader(
                new NalHeader(refIdc, idr ? NalHeader.IDR : NalHeader.SLICE),
                0,
                idr ? 2 : 0,
                0,
                frameNum,
                false,
                false,
                0,
                pocLsb,
                0,
                0,
                0,
                noOutput,
                false,
                !operations.isEmpty(),
                operations,
                0);
    }

    private static MemoryManagement mmco(int operation, int difference, int longTermFrameIdx) {
        return new MemoryManagement(operation, difference, 0, longTermFrameIdx, 0);
    }

    /// Feeds `slices`, each named by its POC, and returns what came out.
    private static List<Integer> run(
            H264Dpb<Integer> dpb, SequenceParameterSet sps, int reorder, SliceHeader... slices) {
        var out = new ArrayList<Integer>();
        for (var slice : slices) {
            var order = dpb.order(sps, slice);
            out.addAll(dpb.decoded(sps, slice, order, dpb.freeSlot(), order.frame(), reorder));
        }
        out.addAll(dpb.drainWaiting());
        return out;
    }

    @Test
    @DisplayName("order type 0: B-frames decoded after their P come out between")
    void reorders() {
        var sps = sps(0, 4, false);
        var out = run(
                new H264Dpb<>(5),
                sps,
                2,
                slice(true, 3, 0, 0, List.of()),
                slice(false, 2, 1, 6, List.of()),
                slice(false, 0, 2, 2, List.of()),
                slice(false, 0, 2, 4, List.of()));
        assertEquals(List.of(0, 2, 4, 6), out);
    }

    @Test
    @DisplayName("order type 0: pic_order_cnt_lsb wraps into the next most significant part")
    void lsbWraps() {
        // log2_max_pic_order_cnt_lsb 4: sixteen values.
        var sps = sps(0, 1, false);
        var dpb = new H264Dpb<Integer>(2);
        var pocs = new ArrayList<Integer>();
        var lsb = 0;
        for (var frame = 0; frame < 6; frame++) {
            var slice = slice(frame == 0, 1, frame % 16, lsb, List.of());
            var order = dpb.order(sps, slice);
            pocs.add(order.frame());
            dpb.decoded(sps, slice, order, dpb.freeSlot(), order.frame(), 0);
            lsb = (lsb + 4) % 16;
        }
        assertEquals(List.of(0, 4, 8, 12, 16, 20), pocs);
    }

    @Test
    @DisplayName("order type 2: twice the frame number, output as decoded")
    void typeTwo() {
        var sps = sps(2, 1, false);
        var out = run(
                new H264Dpb<>(2),
                sps,
                0,
                slice(true, 1, 0, 0, List.of()),
                slice(false, 1, 1, 0, List.of()),
                slice(false, 0, 2, 0, List.of()));
        assertEquals(List.of(0, 2, 3), out, "a non-reference picture is one less");
    }

    @Test
    @DisplayName("the sliding window drops the oldest short-term reference, and its slot is free again")
    void slidingWindow() {
        var sps = sps(2, 2, false);
        var dpb = new H264Dpb<Integer>(3);
        for (var frame = 0; frame < 3; frame++) {
            var slice = slice(frame == 0, 1, frame, 0, List.of());
            var order = dpb.order(sps, slice);
            assertEquals(frame, dpb.freeSlot(), "frame " + frame + " takes a fresh slot");
            dpb.decoded(sps, slice, order, dpb.freeSlot(), frame, 0);
        }
        var references = dpb.references();
        assertEquals(
                List.of(1, 2),
                references.stream().map(H264Dpb.Reference::frameNum).toList());
        assertEquals(0, dpb.freeSlot(), "the slot of the dropped frame 0");
    }

    @Test
    @DisplayName("MMCO 1 unmarks a short-term picture; MMCO 6 keeps the current one as long-term")
    void adaptiveMarking() {
        var sps = sps(2, 4, false);
        var dpb = new H264Dpb<Integer>(5);
        run(dpb, sps, 0, slice(true, 1, 0, 0, List.of()), slice(false, 1, 1, 0, List.of()));
        // Frame 2 unmarks frame 0 (difference 2) and keeps itself at index 0.
        var slice = slice(false, 1, 2, 0, List.of(mmco(1, 2, 0), mmco(6, 0, 0)));
        dpb.decoded(sps, slice, dpb.order(sps, slice), dpb.freeSlot(), 4, 0);
        var references = dpb.references();
        assertEquals(2, references.size());
        assertEquals(1, references.getFirst().frameNum());
        assertTrue(references.getLast().longTerm());
        assertEquals(0, references.getLast().longTermFrameIdx());
    }

    @Test
    @DisplayName("MMCO 5 forgets every reference and starts the order counts again")
    void mmcoFive() {
        var sps = sps(0, 4, false);
        var dpb = new H264Dpb<Integer>(5);
        var out = new ArrayList<Integer>();
        for (var lsb : new int[] {0, 4}) {
            var slice = slice(lsb == 0, 1, lsb / 4, lsb, List.of());
            var order = dpb.order(sps, slice);
            out.addAll(dpb.decoded(sps, slice, order, dpb.freeSlot(), order.frame(), 4));
        }
        var reset = slice(false, 1, 2, 8, List.of(mmco(5, 0, 0)));
        var order = dpb.order(sps, reset);
        out.addAll(dpb.decoded(sps, reset, order, dpb.freeSlot(), order.frame(), 4));
        assertEquals(List.of(0, 4), out, "what waited before the reset goes out first");
        assertEquals(1, dpb.references().size(), "only the picture that reset");
        assertEquals(0, dpb.references().getFirst().frameNum());
        assertEquals(0, dpb.references().getFirst().top(), "its order count is now zero");
        var next = slice(false, 1, 1, 4, List.of());
        assertEquals(4, dpb.order(sps, next).frame(), "and the next counts from there");
    }

    @Test
    @DisplayName("a gap in frame_num fills the window with frames of no slot, which are never referred to")
    void gap() {
        var sps = sps(2, 2, true);
        var dpb = new H264Dpb<Integer>(3);
        run(dpb, sps, 0, slice(true, 1, 0, 0, List.of()), slice(false, 1, 1, 0, List.of()));
        // Frames 2 and 3 are missing: they push 0 and 1 out.
        var slice = slice(false, 1, 4, 0, List.of());
        dpb.decoded(sps, slice, dpb.order(sps, slice), dpb.freeSlot(), 8, 0);
        var references = dpb.references();
        assertEquals(1, references.size(), "frame 3 stands in no slot");
        assertEquals(4, references.getFirst().frameNum());
    }

    @Test
    @DisplayName("an IDR with no_output_of_prior_pics drops what waited")
    void noOutputOfPriorPictures() {
        var sps = sps(0, 4, false);
        var out = run(
                new H264Dpb<>(5),
                sps,
                4,
                slice(true, 1, 0, 0, List.of()),
                slice(false, 1, 1, 4, List.of()),
                slice(true, 1, 0, 0, List.of(), true));
        assertEquals(List.of(0), out, "only the second IDR");
    }

    @Test
    @DisplayName("a reset forgets references and waiting pictures")
    void reset() {
        var sps = sps(2, 2, false);
        var dpb = new H264Dpb<Integer>(3);
        var slice = slice(true, 1, 0, 0, List.of());
        dpb.decoded(sps, slice, dpb.order(sps, slice), dpb.freeSlot(), 0, 4);
        dpb.reset();
        assertTrue(dpb.references().isEmpty());
        assertTrue(dpb.drainWaiting().isEmpty());
    }

    @Test
    @DisplayName("a buffer whose every slot holds a reference refuses another picture")
    void full() {
        var sps = sps(2, 4, false);
        var dpb = new H264Dpb<Integer>(1);
        var slice = slice(true, 1, 0, 0, List.of());
        dpb.decoded(sps, slice, dpb.order(sps, slice), dpb.freeSlot(), 0, 0);
        assertThrows(IllegalStateException.class, dpb::freeSlot);
        assertThrows(IllegalArgumentException.class, () -> new H264Dpb<Integer>(0));
    }
}
