package dev.goldberry.media.bitstream.vp9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The frames no fixture has, written field by field: frames shown again,
/// intra-only frames, error resilience, what carries on from frame to frame,
/// and what is refused.
@DisplayName("UncompressedHeader")
class UncompressedHeaderTest {

    private static HeaderWriter writer(Consumer<HeaderWriter> fields) {
        var w = new HeaderWriter();
        fields.accept(w);
        return w;
    }

    /// An inter frame of the default keyframe's size, referring to slots 0 to
    /// 2 and taking the first one's size.
    private static HeaderWriter inter(Consumer<HeaderWriter> fields) {
        return writer(w -> {
            w.frameType = UncompressedHeader.NON_KEY_FRAME;
            w.refresh = 0x01;
            w.foundRef = 0;
            fields.accept(w);
        });
    }

    private static UncompressedHeader parse(HeaderWriter w, StreamState state) {
        return UncompressedHeader.parse(w.frame(), state);
    }

    private static StreamState afterKeyframe() {
        var state = StreamState.initial();
        return state.after(parse(new HeaderWriter(), state));
    }

    @Test
    @DisplayName("a keyframe states its colour and size, fills every slot, and says where the tiles are")
    void keyframe() {
        var w = new HeaderWriter();
        var frame = w.frame();
        var header = UncompressedHeader.parse(frame, StreamState.initial());
        assertTrue(header.isKeyFrame());
        assertTrue(header.isIntra());
        assertEquals(new ColorConfig(8, ColorConfig.CS_BT_709, false, 1, 1), header.color());
        assertEquals(
                List.of(64, 64, 64, 64),
                List.of(header.frameWidth(), header.frameHeight(), header.renderWidth(), header.renderHeight()));
        assertEquals(0xFF, header.refreshFrameFlags());
        assertEquals(
                List.of(1, 1, 1, 1),
                List.of(header.miCols() / 8, header.miRows() / 8, header.sb64Cols(), header.sb64Rows()));
        assertEquals(frame.length - w.payload, header.uncompressedHeaderSize());
        assertEquals(header.uncompressedHeaderSize(), header.compressedHeaderOffset());
        assertEquals(header.uncompressedHeaderSize() + 4, header.tilesOffset());
        assertEquals(LoopFilter.DEFAULT_REF_DELTAS, header.loopFilter().refDeltas());
        assertFalse(header.usePrevFrameMvs());

        var state = StreamState.initial().after(header);
        for (var slot : state.slots()) {
            assertEquals(new StreamState.Slot(64, 64, 64, 64, 1, 1, 8), slot);
        }
    }

    @Test
    @DisplayName("a frame that shows a slot again is a byte, has the slot's size, and changes nothing")
    void showExistingFrame() {
        var state = afterKeyframe();
        var header = parse(
                writer(w -> {
                    w.showExisting = true;
                    w.frameToShow = 5;
                }),
                state);
        assertTrue(header.showExistingFrame());
        assertTrue(header.showFrame());
        assertEquals(5, header.frameToShowMapIdx());
        assertEquals(64, header.frameWidth());
        assertEquals(1, header.uncompressedHeaderSize());
        assertEquals(0, header.headerSizeInBytes());
        assertEquals(0, header.refreshFrameFlags());
        assertSame(state, state.after(header));

        var profile3 = parse(
                writer(w -> {
                    w.profile = 3;
                    w.showExisting = true;
                }),
                state);
        assertEquals(2, profile3.uncompressedHeaderSize(), "profile 3's reserved bit pushes it past a byte");

        assertThrows(
                IllegalArgumentException.class,
                () -> parse(writer(w -> w.showExisting = true), StreamState.initial()),
                "an empty slot");
    }

    @Test
    @DisplayName("an intra-only frame of profile 0 is 8-bit 4:2:0, resets the context, and fills only its slots")
    void intraOnly() {
        var state = afterKeyframe();
        var header = parse(
                writer(w -> {
                    w.frameType = UncompressedHeader.NON_KEY_FRAME;
                    w.showFrame = false;
                    w.intraOnly = true;
                    w.resetContext = 2;
                    w.refresh = 0b100;
                    w.width = 96;
                    w.height = 80;
                    w.contextIdx = 3;
                }),
                state);
        assertTrue(header.intraOnly());
        assertTrue(header.isIntra());
        assertFalse(header.isKeyFrame());
        assertEquals(ColorConfig.PROFILE_0_INTRA_ONLY, header.color());
        assertEquals(2, header.resetFrameContext());
        assertEquals(3, header.frameContextIdx());
        assertEquals(0, header.frameContextIdxInForce());
        assertEquals(List.of(0, 0, 0), header.refFrameIdx());
        assertFalse(header.usePrevFrameMvs());

        state = state.after(header);
        assertEquals(96, state.slots().get(2).width());
        assertEquals(64, state.slots().get(1).width());
        assertTrue(state.lastIntraOnly());
        assertFalse(state.lastShowFrame());

        // An inter frame that takes its size from its second reference, the
        // intra-only frame's slot.
        var inter = parse(
                inter(w -> {
                    w.refIdx = new int[] {0, 2, 1};
                    w.foundRef = 1;
                    w.width = 96;
                    w.height = 80;
                }),
                state);
        assertEquals(1, inter.foundRef());
        assertEquals(List.of(96, 80), List.of(inter.frameWidth(), inter.frameHeight()));
        assertEquals(List.of(0, 2, 1), inter.refFrameIdx());
        assertEquals(ColorConfig.PROFILE_0_INTRA_ONLY, inter.color(), "the colour carries on");
        assertFalse(inter.usePrevFrameMvs(), "the last frame was intra-only, and hidden");
    }

    @Test
    @DisplayName("the loop filter's deltas carry on until an error-resilient frame resets them")
    void loopFilterDeltas() {
        var state = StreamState.initial();
        var key = parse(
                writer(w -> {
                    w.deltaEnabled = true;
                    w.deltaUpdate = true;
                    w.refDeltas = new Integer[] {null, 5, null, -7};
                    w.modeDeltas = new Integer[] {-3, null};
                }),
                state);
        var lf = key.loopFilter();
        assertEquals(List.of(1, 5, -1, -7), lf.refDeltas());
        assertEquals(0b1010, lf.updateRefDelta());
        assertEquals(List.of(-3, 0), lf.modeDeltas());
        assertEquals(0b01, lf.updateModeDelta());
        state = state.after(key);

        var a = parse(inter(w -> w.deltaEnabled = true), state);
        assertEquals(List.of(1, 5, -1, -7), a.loopFilter().refDeltas());
        assertEquals(List.of(-3, 0), a.loopFilter().modeDeltas());
        assertEquals(0, a.loopFilter().updateRefDelta());
        assertFalse(a.usePrevFrameMvs(), "the last frame was a keyframe");
        state = state.after(a);

        var b = parse(inter(w -> w.signBias = new int[] {0, 1, 1}), state);
        assertTrue(b.usePrevFrameMvs(), "a shown inter frame of the same size before it");
        assertEquals(0b1100, b.refFrameSignBias());
        assertEquals(0b1100, b.refFrameSignBiasMask());
        assertTrue(b.refFrameSignBias(UncompressedHeader.ALTREF_FRAME));
        assertFalse(b.refFrameSignBias(UncompressedHeader.LAST_FRAME));
        state = state.after(b);

        var resilient = parse(
                inter(w -> {
                    w.errorResilient = true;
                    w.signBias = new int[] {1, 0, 1};
                    w.contextIdx = 2;
                }),
                state);
        assertTrue(resilient.errorResilientMode());
        assertEquals(0b1010, resilient.refFrameSignBias());
        assertEquals(0, resilient.refFrameSignBiasMask());
        assertEquals(0, resilient.resetFrameContext());
        assertFalse(resilient.refreshFrameContext());
        assertTrue(resilient.frameParallelDecodingMode());
        assertEquals(0, resilient.frameContextIdxInForce());
        assertEquals(LoopFilter.DEFAULT_REF_DELTAS, resilient.loopFilter().refDeltas());
        assertEquals(LoopFilter.DEFAULT_MODE_DELTAS, resilient.loopFilter().modeDeltas());
        assertFalse(resilient.usePrevFrameMvs());
    }

    @Test
    @DisplayName("segment features carry on through frames that send none, and an intra frame clears them")
    void segmentation() {
        var state = StreamState.initial();
        var key = parse(
                writer(w -> {
                    w.segmentation = true;
                    w.updateMap = true;
                    w.treeProbs = new int[] {128, 255, 7, 255, 255, 255, 1};
                    w.updateData = true;
                    w.absOrDelta = true;
                    w.features[0][SegmentFeatures.ALT_Q] = -20;
                    w.features[3][SegmentFeatures.ALT_L] = 9;
                    w.features[3][SegmentFeatures.REF_FRAME] = 2;
                    w.features[3][SegmentFeatures.SKIP] = 0;
                }),
                state);
        var s = key.segmentation();
        assertEquals(List.of(128, 255, 7, 255, 255, 255, 1), s.treeProbs());
        assertEquals(List.of(255, 255, 255), s.predProbs());
        assertTrue(s.absOrDeltaUpdate());
        var f = s.features();
        assertTrue(f.enabled(0, SegmentFeatures.ALT_Q));
        assertEquals(-20, f.value(0, SegmentFeatures.ALT_Q));
        assertEquals(0b1110, f.enabled().get(3));
        assertEquals(List.of(0, 9, 2, 0), f.data().get(3));
        assertEquals(0, f.enabled().get(7));
        state = state.after(key);

        var carried = parse(
                inter(w -> {
                    w.segmentation = true;
                    w.updateMap = true;
                    w.temporal = true;
                    w.predProbs = new int[] {255, 40, 255};
                }),
                state);
        assertFalse(carried.segmentation().updateData());
        assertEquals(List.of(255, 40, 255), carried.segmentation().predProbs());
        assertTrue(carried.segmentation().absOrDeltaUpdate());
        assertEquals(f, carried.segmentation().features());
        state = state.after(carried);

        var off = parse(inter(_ -> {}), state);
        assertFalse(off.segmentation().enabled());
        assertEquals(f, off.segmentation().features(), "kept while segmentation is off");
        state = state.after(off);

        var intra = parse(
                writer(w -> {
                    w.frameType = UncompressedHeader.NON_KEY_FRAME;
                    w.showFrame = false;
                    w.intraOnly = true;
                    w.refresh = 0x01;
                    w.segmentation = true;
                }),
                state);
        assertEquals(SegmentFeatures.NONE, intra.segmentation().features());
        assertFalse(intra.segmentation().absOrDeltaUpdate());
    }

    @Test
    @DisplayName("quantizer offsets, a render size, a fixed filter, and tiles")
    void fields() {
        var key = parse(
                writer(w -> {
                    w.deltaQ = new int[] {-3, 7, 0};
                    w.renderWidth = 32;
                    w.renderHeight = 16;
                }),
                StreamState.initial());
        assertEquals(new Quantization(60, -3, 7, 0), key.quantization());
        assertFalse(key.quantization().lossless());
        assertTrue(key.renderAndFrameSizeDifferent());
        assertEquals(List.of(32, 16), List.of(key.renderWidth(), key.renderHeight()));
        var state = StreamState.initial().after(key);
        assertEquals(32, state.slots().get(7).renderWidth());

        var lossless = parse(writer(w -> w.baseQ = 0), StreamState.initial());
        assertTrue(lossless.quantization().lossless());

        var smooth = parse(
                inter(w -> {
                    w.switchable = false;
                    w.highPrecision = true;
                }),
                state);
        assertTrue(smooth.allowHighPrecisionMv());
        assertFalse(smooth.filterSwitchable());
        assertEquals(UncompressedHeader.EIGHTTAP_SMOOTH, smooth.interpolationFilter());
        var regular = parse(
                inter(w -> {
                    w.switchable = false;
                    w.rawFilter = 1;
                }),
                state);
        assertEquals(UncompressedHeader.EIGHTTAP, regular.interpolationFilter());
        assertEquals(UncompressedHeader.SWITCHABLE, parse(inter(_ -> {}), state).interpolationFilter());

        // 1024 wide is sixteen 64-pixel columns: up to four tile columns.
        var tiled = parse(
                writer(w -> {
                    w.width = 1024;
                    w.tileColsIncrements = 2;
                    w.tileRows = 2;
                }),
                StreamState.initial());
        assertEquals(2, tiled.tileColsLog2());
        assertEquals(2, tiled.tileRowsLog2());
        var one = parse(
                writer(w -> {
                    w.width = 1024;
                    w.tileColsIncrements = 1;
                    w.tileRows = 1;
                }),
                StreamState.initial());
        assertEquals(1, one.tileColsLog2());
        assertEquals(1, one.tileRowsLog2());
    }

    @Test
    @DisplayName("profiles 1 to 3 state their depth and subsampling; RGB is 4:4:4 and full range")
    void colour() {
        var twelve = parse(
                writer(w -> {
                    w.profile = 2;
                    w.twelveBit = true;
                    w.colorSpace = ColorConfig.CS_BT_2020;
                }),
                StreamState.initial());
        assertEquals(new ColorConfig(12, ColorConfig.CS_BT_2020, false, 1, 1), twelve.color());
        var yuv422 = parse(
                writer(w -> {
                    w.profile = 1;
                    w.subsamplingY = 0;
                    w.fullRange = true;
                }),
                StreamState.initial());
        assertEquals(new ColorConfig(8, ColorConfig.CS_BT_709, true, 1, 0), yuv422.color());
        var rgb = parse(
                writer(w -> {
                    w.profile = 3;
                    w.colorSpace = ColorConfig.CS_RGB;
                }),
                StreamState.initial());
        assertEquals(new ColorConfig(10, ColorConfig.CS_RGB, true, 0, 0), rgb.color());
    }

    @Test
    @DisplayName("what a decoder cannot decode is IllegalArgumentException")
    void malformed() {
        List<Consumer<HeaderWriter>> broken = List.of(
                w -> w.marker = 3,
                w -> w.syncCode = 0x49_83_43,
                w -> {
                    w.profile = 3;
                    w.reservedBit = true;
                },
                w -> w.colorSpace = ColorConfig.CS_RGB,
                w -> w.profile = 1,
                w -> {
                    w.profile = 1;
                    w.subsamplingX = 0;
                    w.colorReserved = true;
                },
                w -> w.compressedSize = 0,
                w -> w.payload = 3);
        for (var fields : broken) {
            assertThrows(IllegalArgumentException.class, () -> parse(writer(fields), StreamState.initial()));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> UncompressedHeader.parse(new byte[] {(byte) 0x82, 0x49}, StreamState.initial()),
                "a frame that ends in its header");
    }

    @Test
    @DisplayName("an inter frame must refer to filled slots of its own format, at a size it can be scaled from")
    void references() {
        var state = StreamState.initial();
        var intra = parse(
                writer(w -> {
                    w.frameType = UncompressedHeader.NON_KEY_FRAME;
                    w.showFrame = false;
                    w.intraOnly = true;
                    w.refresh = 0x01;
                }),
                state);
        var onlySlot0 = state.after(intra);
        assertThrows(IllegalArgumentException.class, () -> parse(inter(_ -> {}), onlySlot0), "slots 1 and 2 empty");
        assertEquals(
                64, parse(inter(w -> w.refIdx = new int[] {0, 0, 0}), onlySlot0).frameWidth());

        var keyed = afterKeyframe();
        var tooSmall = inter(w -> {
            w.foundRef = -1;
            w.width = 16;
            w.height = 16;
        });
        assertThrows(IllegalArgumentException.class, () -> parse(tooSmall, keyed), "a quarter of every reference");
        var twice = parse(
                inter(w -> {
                    w.foundRef = -1;
                    w.width = 128;
                    w.height = 32;
                }),
                keyed);
        assertEquals(List.of(128, 32), List.of(twice.frameWidth(), twice.frameHeight()));
        assertEquals(-1, twice.foundRef());

        // Slot 0 holds a 10-bit frame, slots 1 and 2 8-bit ones.
        var deeper = keyed.after(parse(
                writer(w -> {
                    w.profile = 2;
                    w.frameType = UncompressedHeader.NON_KEY_FRAME;
                    w.showFrame = false;
                    w.intraOnly = true;
                    w.refresh = 0x01;
                }),
                keyed));
        assertEquals(10, deeper.color().bitDepth());
        assertThrows(IllegalArgumentException.class, () -> parse(inter(w -> w.profile = 2), deeper));
        assertEquals(
                10,
                parse(
                                inter(w -> {
                                    w.profile = 2;
                                    w.refIdx = new int[] {0, 0, 0};
                                }),
                                deeper)
                        .color()
                        .bitDepth());
    }
}
