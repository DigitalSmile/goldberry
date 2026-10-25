package dev.goldberry.media.platform.vulkan.hevc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.goldberry.media.bitstream.hevc.NalHeader;
import dev.goldberry.media.bitstream.hevc.SequenceParameterSet;
import dev.goldberry.media.bitstream.hevc.SliceSegmentHeader;

/// The decoded picture buffer of an HEVC stream as a decoder handed whole
/// pictures keeps it (H.265 §8.3.1, §8.3.2, C.5.2): each picture's order
/// count, the reference picture set that says which pictures stay and in
/// which slot, the random-access skipped pictures that cannot be decoded, and
/// which pictures are due for output.
///
/// A reference holds a slot of the Vulkan picture buffer; a picture waiting
/// for output does not, since it was copied out as it was decoded.
///
/// @param <P> what a decoded picture is kept as until it is output
final class H265Dpb<P> {

    /// A slot no picture is in: what a missing reference is told as.
    static final int NO_SLOT = 0xFF;

    /// A picture kept for reference.
    record Reference(int slot, int poc, boolean longTerm) {}

    /// The current picture's reference picture set as slots: the lists a
    /// picture's prediction may name, each slot [#NO_SLOT] for a picture the
    /// stream named but never sent.
    record Sets(List<Integer> stCurrBefore, List<Integer> stCurrAfter, List<Integer> ltCurr) {

        Sets {
            stCurrBefore = List.copyOf(stCurrBefore);
            stCurrAfter = List.copyOf(stCurrAfter);
            ltCurr = List.copyOf(ltCurr);
        }
    }

    /// A picture waiting for output.
    private record Waiting<P>(int poc, long sequence, P picture, int latency) {}

    private final List<Reference> references = new ArrayList<>();
    private final List<Waiting<P>> waiting = new ArrayList<>();
    private final int slots;
    private long sequence;

    // Whether the next IRAP starts afresh: the first picture, or after an end
    // of sequence or a reset (§8.1.3's NoRaslOutputFlag).
    private boolean firstOfSequence = true;
    // Whether the last IRAP did, so its RASL pictures are skipped.
    private boolean skippingRasl;
    // Whether an IRAP has been seen since the reset: before one, nothing decodes.
    private boolean started;
    private int prevTid0Poc;

    /// A buffer of `slots` slots.
    H265Dpb(int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException(slots + " slots");
        }
        this.slots = slots;
    }

    /// Whether the picture `nal` starts is skipped: anything before the first
    /// IRAP, and the RASL pictures of an IRAP that started afresh, whose
    /// references precede it (§8.1.3).
    boolean skips(NalHeader nal) {
        if (nal.isIrap()) {
            return false;
        }
        return !started || (nal.isRasl() && skippingRasl);
    }

    /// Whether the IRAP `nal` starts a coded video sequence afresh: an IDR or a
    /// BLA always, a CRA when it is the first picture or follows an end of
    /// sequence.
    boolean startsAfresh(NalHeader nal) {
        return nal.isIrap() && (nal.isIdr() || nal.isBla() || firstOfSequence);
    }

    /// An end of sequence NAL unit: the next picture starts afresh, and every
    /// picture still waiting is output now, as FFmpeg's decoder outputs them.
    List<P> endOfSequence() {
        firstOfSequence = true;
        return drainWaiting();
    }

    /// `PicOrderCntVal` of the picture `slice` starts (§8.3.1).
    int poc(SequenceParameterSet sps, SliceSegmentHeader slice) {
        var nal = slice.nal();
        var max = sps.maxPocLsb();
        var lsb = slice.pocLsb();
        int msb;
        if (startsAfresh(nal)) {
            msb = 0;
        } else {
            var prevLsb = prevTid0Poc & (max - 1);
            var prevMsb = prevTid0Poc - prevLsb;
            if (lsb < prevLsb && prevLsb - lsb >= max / 2) {
                msb = prevMsb + max;
            } else if (lsb > prevLsb && lsb - prevLsb > max / 2) {
                msb = prevMsb - max;
            } else {
                msb = prevMsb;
            }
        }
        return msb + lsb;
    }

    /// The pictures due for output before the picture `slice` starts is
    /// decoded (C.5.2.2): at an IRAP that starts afresh, every picture still
    /// waiting, unless the stream says they are not to be output.
    List<P> beforeDecoding(SliceSegmentHeader slice) {
        var nal = slice.nal();
        if (!startsAfresh(nal) || waiting.isEmpty()) {
            return List.of();
        }
        // A CRA that starts afresh mid-stream follows an end of sequence,
        // whose pictures went out with it; an IDR or BLA says.
        if (slice.noOutputOfPriorPics()) {
            waiting.clear();
            return List.of();
        }
        return drainWaiting();
    }

    /// Works out the reference picture set of the picture `slice` starts, of
    /// order count `poc` (§8.3.2): the pictures it names stay, short-term or
    /// long-term as it says, and every other leaves the buffer.
    Sets referenceSets(SequenceParameterSet sps, SliceSegmentHeader slice, int poc) {
        var nal = slice.nal();
        if (startsAfresh(nal)) {
            references.clear();
        }
        if (nal.isIdr()) {
            return new Sets(List.of(), List.of(), List.of());
        }
        var stCurrBefore = new ArrayList<Integer>();
        var stCurrAfter = new ArrayList<Integer>();
        var stFoll = new ArrayList<Integer>();
        slice.shortTermRefPicSet().ifPresent(set -> {
            for (var i = 0; i < set.negative(); i++) {
                ((set.usedS0() >> i & 1) != 0 ? stCurrBefore : stFoll)
                        .add(poc + set.deltaPocS0().get(i));
            }
            for (var i = 0; i < set.positive(); i++) {
                ((set.usedS1() >> i & 1) != 0 ? stCurrAfter : stFoll)
                        .add(poc + set.deltaPocS1().get(i));
            }
        });
        var max = sps.maxPocLsb();
        var kept = new ArrayList<Reference>();
        var ltCurr = new ArrayList<Integer>();
        for (var longTerm : slice.longTerm()) {
            var value = longTerm.pocLsb();
            if (longTerm.msbPresent()) {
                value += poc - longTerm.deltaPocMsbCycle() * max - (poc & (max - 1));
            }
            var target = value;
            var found = references.stream()
                    .filter(r -> longTerm.msbPresent() ? r.poc() == target : (r.poc() & (max - 1)) == target)
                    .findFirst();
            found.ifPresent(r -> kept.add(new Reference(r.slot(), r.poc(), true)));
            if (longTerm.usedByCurrPic()) {
                ltCurr.add(found.map(Reference::slot).orElse(NO_SLOT));
            }
        }
        var before = shortTerm(stCurrBefore, kept);
        var after = shortTerm(stCurrAfter, kept);
        shortTerm(stFoll, kept);
        references.clear();
        references.addAll(kept);
        return new Sets(before, after, ltCurr);
    }

    /// The slots of the short-term pictures of `pocs`, each kept.
    private List<Integer> shortTerm(List<Integer> pocs, List<Reference> kept) {
        var found = new ArrayList<Integer>();
        for (var poc : pocs) {
            var reference = references.stream()
                    .filter(r -> !r.longTerm() && r.poc() == poc)
                    .findFirst();
            reference.ifPresent(r -> {
                if (!kept.contains(r)) {
                    kept.add(r);
                }
            });
            found.add(reference.map(Reference::slot).orElse(NO_SLOT));
        }
        return found;
    }

    /// The references in force, for the picture about to be decoded.
    List<Reference> references() {
        return List.copyOf(references);
    }

    /// A free slot for the picture about to be decoded: one no reference holds.
    ///
    /// @throws IllegalStateException when every slot holds a reference
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

    /// Records the picture `slice` started, of order count `poc`, decoded into
    /// `slot`: a short-term reference from now on, and waiting for output when
    /// `output`; returns the pictures now due, in order (C.5.2.3).
    List<P> decoded(SequenceParameterSet sps, SliceSegmentHeader slice, int poc, int slot, P picture, boolean output) {
        var nal = slice.nal();
        if (nal.isIrap()) {
            skippingRasl = startsAfresh(nal);
            started = true;
            firstOfSequence = false;
        }
        if (nal.temporalId() == 0 && !nal.isRasl() && !isRadl(nal) && !nal.isSubLayerNonReference()) {
            prevTid0Poc = poc;
        }
        references.add(new Reference(slot, poc, false));
        var due = new ArrayList<P>();
        var ordering = sps.highest();
        if (output) {
            for (var i = 0; i < waiting.size(); i++) {
                var w = waiting.get(i);
                waiting.set(i, new Waiting<>(w.poc(), w.sequence(), w.picture(), w.latency() + 1));
            }
            waiting.add(new Waiting<>(poc, sequence++, picture, 0));
            waiting.sort(Comparator.comparingInt((Waiting<P> w) -> w.poc()).thenComparingLong(Waiting::sequence));
        }
        while (waiting.size() > ordering.maxNumReorderPics() || overLatency(ordering.maxLatencyPictures())) {
            due.add(waiting.removeFirst().picture());
        }
        return due;
    }

    private boolean overLatency(int limit) {
        return limit >= 0 && waiting.stream().anyMatch(w -> w.latency() >= limit);
    }

    private static boolean isRadl(NalHeader nal) {
        return nal.type() == NalHeader.RADL_N || nal.type() == NalHeader.RADL_R;
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

    /// Forgets everything: a seek, after which nothing decodes until an IRAP.
    void reset() {
        references.clear();
        waiting.clear();
        firstOfSequence = true;
        skippingRasl = false;
        started = false;
        prevTid0Poc = 0;
    }
}
