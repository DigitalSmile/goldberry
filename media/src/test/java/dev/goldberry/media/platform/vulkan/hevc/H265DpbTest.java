package dev.goldberry.media.platform.vulkan.hevc;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.bitstream.BitReader;
import dev.goldberry.media.bitstream.ParameterSets;
import dev.goldberry.media.bitstream.hevc.NalHeader;
import dev.goldberry.media.bitstream.hevc.SequenceParameterSet;
import dev.goldberry.media.bitstream.hevc.ShortTermRefPicSet;
import dev.goldberry.media.bitstream.hevc.SliceSegmentHeader;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.platform.fixtures.Fixtures;

/// The HEVC picture buffer: order counts, reference picture sets, skipped
/// pictures and output order, against hand-made slice headers over a real SPS.
@DisplayName("the HEVC decoded picture buffer")
class H265DpbTest {

    private static SequenceParameterSet sps;

    @BeforeAll
    static void readSps() {
        try (var demuxer = Fixtures.demux("clip-hevc-256.mp4")) {
            var track = demuxer.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            var record = demuxer.request(track.index()).extradata().toArray(JAVA_BYTE);
            for (var nal : ParameterSets.hevc(record).parameterSets()) {
                if (NalHeader.of(nal).type() == NalHeader.SPS) {
                    sps = SequenceParameterSet.parse(BitReader.unescape(nal, 2, nal.length - 2));
                }
            }
        }
    }

    /// A set of the pictures `deltas` away, negative then positive, each used
    /// by the current picture when `used`.
    private static ShortTermRefPicSet set(boolean used, int... deltas) {
        var negative = new ArrayList<Integer>();
        var positive = new ArrayList<Integer>();
        for (var delta : deltas) {
            (delta < 0 ? negative : positive).add(delta);
        }
        var mask = used ? 0xFFFF : 0;
        return new ShortTermRefPicSet(false, 0, false, 0, 0, 0, negative, mask, positive, mask, 0);
    }

    private static SliceSegmentHeader slice(int type, int pocLsb, Optional<ShortTermRefPicSet> set) {
        return slice(type, pocLsb, set, List.of(), true, false);
    }

    private static SliceSegmentHeader slice(
            int type,
            int pocLsb,
            Optional<ShortTermRefPicSet> set,
            List<SliceSegmentHeader.LongTerm> longTerm,
            boolean output,
            boolean noOutputOfPriorPics) {
        return new SliceSegmentHeader(
                new NalHeader(type, 0, 0),
                true,
                noOutputOfPriorPics,
                0,
                false,
                0,
                type == NalHeader.IDR_W_RADL || type == NalHeader.CRA_NUT ? 2 : 0,
                output,
                pocLsb,
                false,
                0,
                set,
                0,
                0,
                longTerm.size(),
                longTerm);
    }

    private static SliceSegmentHeader idr() {
        return slice(NalHeader.IDR_W_RADL, 0, Optional.empty());
    }

    /// Decodes `slice` as the stream would, returning what came out; the
    /// picture is its order count.
    private static List<Integer> decode(H265Dpb<Integer> dpb, SliceSegmentHeader slice) {
        var out = new ArrayList<Integer>();
        if (dpb.skips(slice.nal())) {
            return out;
        }
        var poc = dpb.poc(sps, slice);
        out.addAll(dpb.beforeDecoding(slice));
        dpb.referenceSets(sps, slice, poc);
        out.addAll(dpb.decoded(sps, slice, poc, dpb.freeSlot(), poc, slice.picOutput()));
        return out;
    }

    @Test
    @DisplayName("nothing decodes before the first IRAP")
    void waitsForAnIrap() {
        var dpb = new H265Dpb<Integer>(6);
        assertTrue(dpb.skips(new NalHeader(NalHeader.TRAIL_R, 0, 0)));
        assertFalse(dpb.skips(new NalHeader(NalHeader.CRA_NUT, 0, 0)));
    }

    @Test
    @DisplayName("the RASL pictures of a CRA that starts afresh are skipped, a later CRA's are decoded")
    void raslPictures() {
        var dpb = new H265Dpb<Integer>(6);
        var rasl = new NalHeader(NalHeader.RASL_N, 0, 0);
        decode(dpb, slice(NalHeader.CRA_NUT, 8, Optional.empty()));
        assertTrue(dpb.skips(rasl), "the first CRA's leading pictures refer to what was never sent");
        decode(dpb, slice(NalHeader.TRAIL_R, 12, Optional.of(set(true, -4))));
        decode(dpb, slice(NalHeader.CRA_NUT, 16, Optional.of(set(false, -4))));
        assertFalse(dpb.skips(rasl), "a CRA mid-stream keeps what came before it");
        dpb.endOfSequence();
        decode(dpb, slice(NalHeader.CRA_NUT, 0, Optional.empty()));
        assertTrue(dpb.skips(rasl), "after an end of sequence a CRA starts afresh");
    }

    @Test
    @DisplayName("the order count's most significant part follows pic_order_cnt_lsb round")
    void orderCountWraps() {
        var dpb = new H265Dpb<Integer>(6);
        var max = sps.maxPocLsb();
        decode(dpb, idr());
        var pocs = new ArrayList<Integer>();
        var poc = 0;
        for (var i = 0; i < 6; i++) {
            poc += max / 4;
            var slice = slice(NalHeader.TRAIL_R, poc % max, Optional.of(set(true, -max / 4)));
            pocs.add(dpb.poc(sps, slice));
            decode(dpb, slice);
        }
        assertEquals(List.of(max / 4, max / 2, 3 * max / 4, max, 5 * max / 4, 3 * max / 2), pocs);
    }

    @Test
    @DisplayName("the set keeps what it names, drops the rest, and names a missing picture as no slot")
    void referencePictureSet() {
        var dpb = new H265Dpb<Integer>(6);
        decode(dpb, idr());
        decode(dpb, slice(NalHeader.TRAIL_R, 4, Optional.of(set(true, -4))));
        decode(dpb, slice(NalHeader.TRAIL_R, 8, Optional.of(set(true, -4, -8))));
        // POC 12 keeps 8 for itself and 4 for later, drops 0, and names 2, never sent.
        var slice = slice(
                NalHeader.TRAIL_R,
                12,
                Optional.of(new ShortTermRefPicSet(
                        false, 0, false, 0, 0, 0, List.of(-4, -8, -10), 0b101, List.of(), 0, 0)));
        var poc = dpb.poc(sps, slice);
        var sets = dpb.referenceSets(sps, slice, poc);
        assertEquals(2, sets.stCurrBefore().size());
        assertEquals(H265Dpb.NO_SLOT, sets.stCurrBefore().get(1), "POC 2");
        assertEquals(
                List.of(8, 4),
                dpb.references().stream().map(H265Dpb.Reference::poc).toList());
        assertTrue(sets.stCurrAfter().isEmpty());
        assertTrue(sets.ltCurr().isEmpty());
    }

    @Test
    @DisplayName("a long-term picture is found by its pic_order_cnt_lsb and kept as long-term")
    void longTerm() {
        var dpb = new H265Dpb<Integer>(6);
        decode(dpb, idr());
        decode(dpb, slice(NalHeader.TRAIL_R, 4, Optional.of(set(true, -4))));
        var slice = slice(
                NalHeader.TRAIL_R,
                8,
                Optional.of(set(true, -4)),
                List.of(new SliceSegmentHeader.LongTerm(0, true, false, 0)),
                true,
                false);
        var sets = dpb.referenceSets(sps, slice, dpb.poc(sps, slice));
        assertEquals(1, sets.ltCurr().size());
        var kept = dpb.references();
        assertTrue(kept.stream().anyMatch(r -> r.poc() == 0 && r.longTerm()));
        assertTrue(kept.stream().anyMatch(r -> r.poc() == 4 && !r.longTerm()));
    }

    @Test
    @DisplayName("pictures come out in order, as many waiting as the SPS lets reorder")
    void outputOrder() {
        var dpb = new H265Dpb<Integer>(8);
        var out = new ArrayList<Integer>();
        out.addAll(decode(dpb, idr()));
        // A P picture and the B pictures between, decoded after it.
        out.addAll(decode(dpb, slice(NalHeader.TRAIL_R, 8, Optional.of(set(true, -8)))));
        out.addAll(decode(dpb, slice(NalHeader.TRAIL_R, 4, Optional.of(set(true, -4, 4)))));
        out.addAll(decode(dpb, slice(NalHeader.TRAIL_N, 2, Optional.of(set(true, -2, 2, 6)))));
        out.addAll(decode(dpb, slice(NalHeader.TRAIL_N, 6, Optional.of(set(true, -2, -6, 2)))));
        var waiting = out.size();
        out.addAll(dpb.drainWaiting());
        assertEquals(List.of(0, 2, 4, 6, 8), out);
        assertTrue(5 - waiting <= Math.max(sps.highest().maxNumReorderPics(), 1) + 1, "waited " + (5 - waiting));
    }

    @Test
    @DisplayName("a picture whose pic_output_flag is off is a reference but never comes out")
    void notOutput() {
        var dpb = new H265Dpb<Integer>(6);
        decode(dpb, idr());
        decode(dpb, slice(NalHeader.TRAIL_R, 4, Optional.of(set(true, -4)), List.of(), false, false));
        assertEquals(List.of(0), dpb.drainWaiting());
    }

    @Test
    @DisplayName("an IDR outputs what waited, unless it says no_output_of_prior_pics")
    void priorPictures() {
        var dpb = new H265Dpb<Integer>(6);
        decode(dpb, idr());
        decode(dpb, slice(NalHeader.TRAIL_R, 4, Optional.of(set(true, -4))));
        var waited = new ArrayList<Integer>(dpb.beforeDecoding(idr()));
        assertTrue(dpb.drainWaiting().isEmpty(), "every one went out");
        assertEquals(waited.stream().sorted().toList(), waited);
        var drop = new H265Dpb<Integer>(6);
        decode(drop, idr());
        decode(drop, slice(NalHeader.TRAIL_R, 4, Optional.of(set(true, -4))));
        var none = drop.beforeDecoding(slice(NalHeader.IDR_W_RADL, 0, Optional.empty(), List.of(), true, true));
        assertTrue(none.isEmpty());
        assertTrue(drop.drainWaiting().isEmpty(), "dropped, not kept");
    }

    @Test
    @DisplayName("an end of sequence outputs every picture waiting")
    void endOfSequence() {
        var dpb = new H265Dpb<Integer>(6);
        decode(dpb, idr());
        assertEquals(1, dpb.references().size());
        var out = dpb.endOfSequence();
        assertEquals(sps.highest().maxNumReorderPics() > 0 ? List.of(0) : List.of(), out, "the IDR, if it waited");
        assertTrue(dpb.drainWaiting().isEmpty());
    }

    @Test
    @DisplayName("a reset forgets everything and waits for an IRAP again")
    void reset() {
        var dpb = new H265Dpb<Integer>(6);
        decode(dpb, idr());
        dpb.reset();
        assertTrue(dpb.references().isEmpty());
        assertTrue(dpb.skips(new NalHeader(NalHeader.TRAIL_R, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new H265Dpb<Integer>(0));
    }
}
