package dev.goldberry.media.bitstream.vp9;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.bitstream.HeaderTrace;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Containers;

/// The VP9 readers against FFmpeg's `trace_headers` of the same streams:
/// every syntax element FFmpeg read from every frame, by name, value and
/// order, its summary of the frame, and every superframe index. The clips are
/// libvpx's; `vp9-hand-made.ivf` holds the frames it does not make.
@DisplayName("VP9 headers")
class Vp9HeadersTest {

    /// FFmpeg's summary after each header: the size, subsampling and bit depth
    /// in force, and the tiles.
    private static final Pattern SUMMARY = Pattern.compile(
            "^Frame:  size (\\d+)x(\\d+)  subsample (\\d)x(\\d)  bit_depth (\\d+)  tiles (\\d+)x(\\d+)\\.$");

    /// The frames of `vp9-hand-made.ivf`, each as [HeaderWriter] writes it.
    private static final List<Consumer<HeaderWriter>> HAND_MADE = List.of(
            // A keyframe 1024 wide, so four tile columns; four tile rows; a
            // render size; quantizer offsets; loop filter deltas; segments.
            w -> {
                w.width = 1024;
                w.tileColsIncrements = 2;
                w.tileRows = 2;
                w.deltaEnabled = true;
                w.deltaUpdate = true;
                w.refDeltas = new Integer[] {null, 5, null, -7};
                w.modeDeltas = new Integer[] {-3, null};
                w.deltaQ = new int[] {-3, 7, 0};
                w.renderWidth = 32;
                w.renderHeight = 16;
                w.segmentation = true;
                w.updateMap = true;
                w.treeProbs = new int[] {128, 255, 7, 255, 255, 255, 1};
                w.temporal = true;
                w.predProbs = new int[] {255, 40, 3};
                w.updateData = true;
                w.absOrDelta = true;
                w.features[0][SegmentFeatures.ALT_Q] = -20;
                w.features[3][SegmentFeatures.ALT_L] = 9;
                w.features[3][SegmentFeatures.REF_FRAME] = 2;
                w.features[3][SegmentFeatures.SKIP] = 0;
                w.features[7][SegmentFeatures.ALT_Q] = 255;
            },
            // Slot 5 shown again.
            w -> {
                w.showExisting = true;
                w.frameToShow = 5;
            },
            // A hidden intra-only frame into slot 2, resetting context 3.
            w -> {
                w.width = 1024;
                w.frameType = UncompressedHeader.NON_KEY_FRAME;
                w.showFrame = false;
                w.intraOnly = true;
                w.resetContext = 2;
                w.refresh = 0b100;
                w.contextIdx = 3;
            },
            // An inter frame sized by its second reference, with a fixed
            // sharp filter and two tile columns.
            w -> {
                w.width = 1024;
                w.frameType = UncompressedHeader.NON_KEY_FRAME;
                w.refresh = 0x02;
                w.refIdx = new int[] {0, 2, 1};
                w.foundRef = 1;
                w.signBias = new int[] {0, 1, 1};
                w.switchable = false;
                w.rawFilter = 2;
                w.highPrecision = true;
                w.tileColsIncrements = 1;
            },
            // An error-resilient frame of its own size, half the references',
            // with a render size and segmentation that sends nothing.
            w -> {
                w.width = 512;
                w.height = 48;
                w.frameType = UncompressedHeader.NON_KEY_FRAME;
                w.errorResilient = true;
                w.refresh = 0x08;
                w.signBias = new int[] {1, 0, 1};
                w.contextIdx = 2;
                w.renderWidth = 500;
                w.renderHeight = 40;
                w.segmentation = true;
            },
            // An inter frame sized by the error-resilient one, the deltas at
            // their extremes.
            w -> {
                w.width = 512;
                w.height = 48;
                w.frameType = UncompressedHeader.NON_KEY_FRAME;
                w.refresh = 0;
                w.refIdx = new int[] {3, 3, 0};
                w.foundRef = 0;
                w.deltaEnabled = true;
                w.deltaUpdate = true;
                w.refDeltas = new Integer[] {-63, null, 63, null};
                w.modeDeltas = new Integer[] {null, 1};
            });

    /// The packets of `name`'s video track, from `…/media/fixtures/`.
    static List<byte[]> packets(String name) {
        try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
            var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
            container.select(Set.of(track.index()));
            var packets = new ArrayList<byte[]>();
            for (var next = container.read(); next != null; next = container.read()) {
                try (var packet = next) {
                    packets.add(packet.data().toArray(JAVA_BYTE));
                }
            }
            return packets;
        }
    }

    private static String traceName(String name) {
        return name.replace('.', '-') + ".trace";
    }

    private static List<List<Integer>> summaries(String name) {
        String text;
        try (var in = Objects.requireNonNull(
                Vp9HeadersTest.class.getResourceAsStream("/dev/goldberry/media/bitstream/" + traceName(name)))) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var summaries = new ArrayList<List<Integer>>();
        for (var line : text.lines().toList()) {
            var m = SUMMARY.matcher(line.strip());
            if (m.matches()) {
                var values = new ArrayList<Integer>();
                for (var g = 1; g <= m.groupCount(); g++) {
                    values.add(Integer.parseInt(m.group(g)));
                }
                summaries.add(values);
            }
        }
        return summaries;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "clip-vp9-full.webm",
                "clip-vp9-444.webm",
                "clip-vp9-2020-10bit.webm",
                "clip-vp9-444-10bit.webm",
                "clip-vp9-superframes.webm"
            })
    @DisplayName("every frame's uncompressed header reads as FFmpeg reads it, element by element")
    void matchesFfmpeg(String name) {
        assertMatchesTrace(name, packets(name));
    }

    @Test
    @DisplayName("frames libvpx does not make, written field by field, read as FFmpeg reads them")
    void handMade() {
        var frames = ivfFrames("vp9-hand-made.ivf");
        assertEquals(HAND_MADE.size(), frames.size());
        for (var i = 0; i < frames.size(); i++) {
            var w = new HeaderWriter();
            HAND_MADE.get(i).accept(w);
            assertArrayEquals(w.frame(), frames.get(i), "frame " + i + " is HeaderWriter's");
        }
        assertMatchesTrace("vp9-hand-made.ivf", frames);
    }

    /// The frames of the IVF file `name`, from `…/media/bitstream/`: a 32-byte
    /// file header, then each frame's size, its time, and its bytes.
    private static List<byte[]> ivfFrames(String name) {
        byte[] file;
        try (var in = Objects.requireNonNull(
                Vp9HeadersTest.class.getResourceAsStream("/dev/goldberry/media/bitstream/" + name))) {
            file = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(buffer.getShort(6));
        var frames = new ArrayList<byte[]>();
        while (buffer.remaining() >= 12) {
            var frame = new byte[buffer.getInt()];
            buffer.position(buffer.position() + Long.BYTES);
            buffer.get(frame);
            frames.add(frame);
        }
        return frames;
    }

    /// Asserts that every frame of `packets`, read in order, has the syntax
    /// elements, header size and summary FFmpeg traced from `name`.
    private static void assertMatchesTrace(String name, List<byte[]> packets) {
        var trace = HeaderTrace.load(traceName(name));
        var traced = trace.all("Frame");
        var indexes = trace.all("Superframe Index");
        var summaries = summaries(name);
        var state = StreamState.initial();
        var frame = 0;
        var decoded = 0;
        var superframes = 0;
        for (var packet : packets) {
            var index = Superframe.parse(packet);
            if (index.isPresent()) {
                var t = indexes.get(superframes++);
                var sf = index.orElseThrow();
                assertEquals(
                        t.value("frames_in_superframe_minus_1") + 1, sf.frames().size(), name);
                assertEquals(t.value("bytes_per_framesize_minus_1") + 1, sf.bytesPerFrameSize(), name);
                for (var i = 0; i < sf.frames().size(); i++) {
                    assertEquals(
                            t.value("frame_sizes[" + i + "]"),
                            sf.frames().get(i).size(),
                            name);
                }
                var last = sf.frames().getLast();
                assertEquals(packet.length, last.offset() + last.size() + sf.indexSize(), name);
            }
            for (var range : Superframe.frames(packet)) {
                var where = name + " frame " + frame;
                var bytes = Arrays.copyOfRange(packet, range.offset(), range.offset() + range.size());
                var header = UncompressedHeader.parse(bytes, state);
                var t = traced.get(frame);
                assertEquals(
                        List.copyOf(t.values().entrySet()),
                        List.copyOf(elements(header).entrySet()),
                        where);
                if (!header.showExistingFrame()) {
                    // The header ends 16 bits after header_size_in_bytes
                    // starts, and its trailing bits fill the byte.
                    assertEquals((t.bit("header_size_in_bytes") + 16 + 7) / 8, header.uncompressedHeaderSize(), where);
                    var color = header.color();
                    assertEquals(
                            List.of(
                                    header.frameWidth(),
                                    header.frameHeight(),
                                    color.subsamplingX(),
                                    color.subsamplingY(),
                                    color.bitDepth(),
                                    1 << header.tileColsLog2(),
                                    1 << header.tileRowsLog2()),
                            summaries.get(decoded++),
                            where);
                    assertTrue(header.tilesOffset() <= bytes.length, where);
                }
                state = state.after(header);
                frame++;
            }
        }
        assertEquals(traced.size(), frame, name + ": frames");
        assertEquals(summaries.size(), decoded, name + ": frames decoded");
        assertEquals(indexes.size(), superframes, name + ": superframes");
    }

    @Test
    @DisplayName("the superframe clip hides an altref in two packets, and its frames carry on from each other")
    void superframes() {
        var packets = packets("clip-vp9-superframes.webm");
        assertEquals(15, packets.size());
        var state = StreamState.initial();
        var hidden = 0;
        var shown = 0;
        var segmented = 0;
        for (var packet : packets) {
            var frames = Superframe.frames(packet);
            assertEquals(Superframe.parse(packet).isPresent(), frames.size() > 1);
            for (var i = 0; i < frames.size(); i++) {
                var range = frames.get(i);
                var header = UncompressedHeader.parse(
                        Arrays.copyOfRange(packet, range.offset(), range.offset() + range.size()), state);
                // In a superframe, only the last frame is shown.
                assertEquals(i == frames.size() - 1, header.showFrame());
                if (header.showFrame()) {
                    shown++;
                } else {
                    hidden++;
                }
                if (header.segmentation().enabled()
                        && header.segmentation().features().any()) {
                    segmented++;
                }
                assertEquals(160, header.frameWidth());
                assertEquals(128, header.frameHeight());
                if (!header.isIntra() && state.lastShowFrame()) {
                    assertTrue(header.usePrevFrameMvs() || state.lastKeyFrame(), "frame after a shown one");
                }
                state = state.after(header);
            }
        }
        assertEquals(2, hidden);
        assertEquals(15, shown);
        assertTrue(segmented > 0, "segment features in force");
        assertFalse(state.slots().stream().anyMatch(slot -> !slot.filled()), "every slot is filled by the keyframe");
    }

    /// The syntax elements FFmpeg's `cbs_vp9` reads for `h`, in its order,
    /// under its names, with the values it prints.
    static Map<String, Long> elements(UncompressedHeader h) {
        var e = new Elements();
        e.put("frame_marker", 2);
        e.put("profile_low_bit", h.profile() & 1);
        e.put("profile_high_bit", h.profile() >> 1);
        if (h.profile() == 3) {
            e.put("reserved_zero", 0);
        }
        e.put("show_existing_frame", h.showExistingFrame());
        if (h.showExistingFrame()) {
            e.put("frame_to_show_map_idx", h.frameToShowMapIdx());
            return e.map;
        }
        e.put("frame_type", h.frameType());
        e.put("show_frame", h.showFrame());
        e.put("error_resilient_mode", h.errorResilientMode());
        if (h.isKeyFrame()) {
            sync(e);
            color(e, h);
            size(e, h);
            render(e, h);
        } else {
            if (!h.showFrame()) {
                e.put("intra_only", h.intraOnly());
            }
            if (!h.errorResilientMode()) {
                e.put("reset_frame_context", h.resetFrameContext());
            }
            if (h.intraOnly()) {
                sync(e);
                if (h.profile() > 0) {
                    color(e, h);
                }
                e.put("refresh_frame_flags", h.refreshFrameFlags());
                size(e, h);
                render(e, h);
            } else {
                e.put("refresh_frame_flags", h.refreshFrameFlags());
                for (var i = 0; i < UncompressedHeader.REFS_PER_FRAME; i++) {
                    e.put("ref_frame_idx[" + i + "]", h.refFrameIdx().get(i));
                    var ref = UncompressedHeader.LAST_FRAME + i;
                    e.put("ref_frame_sign_bias[" + ref + "]", h.refFrameSignBias(ref));
                }
                for (var i = 0; i < UncompressedHeader.REFS_PER_FRAME; i++) {
                    e.put("found_ref[" + i + "]", h.foundRef() == i);
                    if (h.foundRef() == i) {
                        break;
                    }
                }
                if (h.foundRef() < 0) {
                    size(e, h);
                }
                render(e, h);
                e.put("allow_high_precision_mv", h.allowHighPrecisionMv());
                e.put("is_filter_switchable", h.filterSwitchable());
                if (!h.filterSwitchable()) {
                    e.put("raw_interpolation_filter_type", h.rawInterpolationFilter());
                }
            }
        }
        if (!h.errorResilientMode()) {
            e.put("refresh_frame_context", h.refreshFrameContext());
            e.put("frame_parallel_decoding_mode", h.frameParallelDecodingMode());
        }
        e.put("frame_context_idx", h.frameContextIdx());

        var lf = h.loopFilter();
        e.put("loop_filter_level", lf.level());
        e.put("loop_filter_sharpness", lf.sharpness());
        e.put("loop_filter_delta_enabled", lf.deltaEnabled());
        if (lf.deltaEnabled()) {
            e.put("loop_filter_delta_update", lf.deltaUpdate());
            if (lf.deltaUpdate()) {
                for (var i = 0; i < 4; i++) {
                    var update = (lf.updateRefDelta() & (1 << i)) != 0;
                    e.put("update_ref_delta[" + i + "]", update);
                    if (update) {
                        e.put(
                                "loop_filter_ref_deltas[" + i + "]",
                                lf.refDeltas().get(i));
                    }
                }
                for (var i = 0; i < 2; i++) {
                    var update = (lf.updateModeDelta() & (1 << i)) != 0;
                    e.put("update_mode_delta[" + i + "]", update);
                    if (update) {
                        e.put(
                                "loop_filter_mode_deltas[" + i + "]",
                                lf.modeDeltas().get(i));
                    }
                }
            }
        }

        var q = h.quantization();
        e.put("base_q_idx", q.baseQIdx());
        delta(e, "delta_q_y_dc", q.deltaQYDc());
        delta(e, "delta_q_uv_dc", q.deltaQUvDc());
        delta(e, "delta_q_uv_ac", q.deltaQUvAc());

        var s = h.segmentation();
        e.put("segmentation_enabled", s.enabled());
        if (s.enabled()) {
            e.put("segmentation_update_map", s.updateMap());
            if (s.updateMap()) {
                for (var i = 0; i < Segmentation.TREE_PROBS; i++) {
                    prob(e, "segmentation_tree_probs[" + i + "]", s.treeProbs().get(i));
                }
                e.put("segmentation_temporal_update", s.temporalUpdate());
                if (s.temporalUpdate()) {
                    for (var i = 0; i < Segmentation.PRED_PROBS; i++) {
                        prob(
                                e,
                                "segmentation_pred_prob[" + i + "]",
                                s.predProbs().get(i));
                    }
                }
            }
            e.put("segmentation_update_data", s.updateData());
            if (s.updateData()) {
                e.put("segmentation_abs_or_delta_update", s.absOrDeltaUpdate());
                var f = s.features();
                for (var i = 0; i < SegmentFeatures.MAX_SEGMENTS; i++) {
                    for (var j = 0; j < SegmentFeatures.SEG_LVL_MAX; j++) {
                        var at = "[" + i + "][" + j + "]";
                        e.put("feature_enabled" + at, f.enabled(i, j));
                        if (f.enabled(i, j) && SegmentFeatures.BITS.get(j) > 0) {
                            e.put("feature_value" + at, Math.abs(f.value(i, j)));
                            if (SegmentFeatures.SIGNED.get(j)) {
                                e.put("feature_sign" + at, f.value(i, j) < 0);
                            }
                        }
                    }
                }
            }
        }
        e.put("tile_cols_log2", h.tileColsLog2());
        e.put("tile_rows_log2", h.tileRowsLog2());
        e.put("header_size_in_bytes", h.headerSizeInBytes());
        return e.map;
    }

    private static void sync(Elements e) {
        e.put("frame_sync_byte_0", 0x49);
        e.put("frame_sync_byte_1", 0x83);
        e.put("frame_sync_byte_2", 0x42);
    }

    private static void color(Elements e, UncompressedHeader h) {
        var c = h.color();
        if (h.profile() >= 2) {
            e.put("ten_or_twelve_bit", c.bitDepth() == 12);
        }
        e.put("color_space", c.colorSpace());
        var odd = h.profile() == 1 || h.profile() == 3;
        if (c.colorSpace() != ColorConfig.CS_RGB) {
            e.put("color_range", c.fullRange());
            if (odd) {
                e.put("subsampling_x", c.subsamplingX());
                e.put("subsampling_y", c.subsamplingY());
                e.put("reserved_zero", 0);
            }
        } else if (odd) {
            e.put("reserved_zero", 0);
        }
    }

    private static void size(Elements e, UncompressedHeader h) {
        e.put("frame_width_minus_1", h.frameWidth() - 1);
        e.put("frame_height_minus_1", h.frameHeight() - 1);
    }

    private static void render(Elements e, UncompressedHeader h) {
        e.put("render_and_frame_size_different", h.renderAndFrameSizeDifferent());
        if (h.renderAndFrameSizeDifferent()) {
            e.put("render_width_minus_1", h.renderWidth() - 1);
            e.put("render_height_minus_1", h.renderHeight() - 1);
        }
    }

    private static void delta(Elements e, String name, int value) {
        e.put(name + ".delta_coded", value != 0);
        if (value != 0) {
            e.put(name + ".delta_q", value);
        }
    }

    private static void prob(Elements e, String name, int value) {
        e.put(name + ".prob_coded", value != Segmentation.MAX_PROB);
        if (value != Segmentation.MAX_PROB) {
            e.put(name + ".prob", value);
        }
    }

    /// Names to values in order, the first of a name kept, as [HeaderTrace]
    /// keeps them.
    private static final class Elements {
        final Map<String, Long> map = new LinkedHashMap<>();

        void put(String name, long value) {
            map.putIfAbsent(name, value);
        }

        void put(String name, boolean value) {
            put(name, value ? 1 : 0);
        }
    }
}
