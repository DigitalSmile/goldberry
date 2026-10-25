package dev.goldberry.media.bitstream.av1;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.bitstream.HeaderTrace;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Containers;

/// The AV1 readers against FFmpeg's `trace_headers` of the same streams:
/// every syntax element FFmpeg reads, by name, value and bit, and the sizes it
/// works out of each frame.
///
/// The fixtures between them have one tile and four, uniform and uneven,
/// film grain, segmentation, delta q and lf, quantizer matrices, loop
/// restoration, CDEF, global motion, superres, intra-only frames, frames
/// shown again, skip mode, frame ids and error resilient frames, a lossless
/// monochrome still picture, timing info with a decoder model, 8-bit and
/// 10-bit HDR.
@DisplayName("AV1 headers")
class Av1HeadersTest {

    /// The fixtures with traces.
    static List<String> fixtures() {
        return List.of(
                "clip-av1.mkv",
                "clip-av1-pq.mkv",
                "clip-av1-tools.mkv",
                "clip-av1-superres.mkv",
                "clip-av1-resilient.mkv",
                "clip-av1-still.mkv",
                "clip-av1-timing.mkv");
    }

    /// The sections both FFmpeg and the parser have; FFmpeg's metadata and
    /// frame header copies are not read here.
    private static final Set<String> READ = Set.of("OBU header", "Sequence Header", "Frame Header", "Tile Group");

    /// FFmpeg's summary after each frame header it reads.
    private static final Pattern SUMMARY = Pattern.compile("Frame (\\d+): size (\\d+)x(\\d+) upscaled (\\d+) render"
            + " (\\d+)x(\\d+) subsample (\\d)x(\\d) bitdepth (\\d+) tiles (\\d+)x(\\d+)\\.");

    /// A fixture's video as the container hands it over: its `av1C` record,
    /// and its temporal units.
    private record Stream(byte[] av1C, List<byte[]> units) {

        static Stream of(String name) {
            try (var container = Containers.open(ClipFixtures.source(name), ClipFixtures.io(name))) {
                var track = container.info().defaultTrack(MediaType.VIDEO).orElseThrow();
                container.select(Set.of(track.index()));
                var record = container.request(track.index()).extradata().toArray(JAVA_BYTE);
                var units = new ArrayList<byte[]>();
                for (var next = container.read(); next != null; next = container.read()) {
                    try (var packet = next) {
                        units.add(packet.data().toArray(JAVA_BYTE));
                    }
                }
                return new Stream(record, units);
            }
        }
    }

    /// One section the parser read, as FFmpeg's trace has it.
    private record Section(String heading, List<HeaderTrace.Element> elements) {}

    /// The sections the parser read.
    private static final class Recorder implements Syntax.Trace {

        final List<Section> sections = new ArrayList<>();

        @Override
        public void heading(String heading) {
            sections.add(new Section(heading, new ArrayList<>()));
        }

        @Override
        public void element(long bit, String name, long value) {
            sections.getLast().elements().add(new HeaderTrace.Element(bit, name, value));
        }
    }

    /// The fixture `name` read by a parser that records what it reads, and
    /// what it read.
    private record Run(List<Section> sections, List<Av1Parser.Parsed> parsed, Stream stream) {

        static Run of(String name) {
            var stream = Stream.of(name);
            var recorder = new Recorder();
            var parser = new Av1Parser(recorder);
            var parsed = new ArrayList<>(parser.configure(stream.av1C()));
            for (var unit : stream.units()) {
                parsed.addAll(parser.parse(unit));
            }
            return new Run(kept(recorder.sections), parsed, stream);
        }

        List<FrameHeader> frameHeaders() {
            var headers = new ArrayList<FrameHeader>();
            for (var each : parsed) {
                switch (each.content()) {
                    case FrameHeader header -> headers.add(header);
                    case Frame frame -> headers.add(frame.header());
                    default -> {}
                }
            }
            return headers;
        }
    }

    private static List<Section> kept(List<Section> sections) {
        return sections.stream()
                .filter(s -> READ.contains(s.heading()) && !s.elements().isEmpty())
                .toList();
    }

    /// FFmpeg's sections for `name`, as the parser's are kept: its summary
    /// line, which splits a frame header from its byte alignment, joined back,
    /// and its trailing bits, which the parser does not read, left out.
    private static List<Section> ffmpeg(String name) {
        var joined = new ArrayList<Section>();
        for (var section : HeaderTrace.load(trace(name)).sections()) {
            var elements = section.elements().stream()
                    .filter(e -> !e.name().startsWith("trailing_"))
                    .toList();
            if (SUMMARY.matcher(section.heading()).matches() && !joined.isEmpty()) {
                joined.getLast().elements().addAll(elements);
            } else {
                joined.add(new Section(section.heading(), new ArrayList<>(elements)));
            }
        }
        return kept(joined);
    }

    private static String trace(String name) {
        return name.replace('.', '-') + ".trace";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("every element reads as FFmpeg reads it, at the same bit")
    void everyElement(String name) {
        var expected = ffmpeg(name);
        var actual = Run.of(name).sections();
        for (var i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            var want = expected.get(i);
            var got = actual.get(i);
            var where = name + " section " + i + " (" + want.heading() + ")";
            assertEquals(want.heading(), got.heading(), where);
            for (var j = 0; j < Math.min(want.elements().size(), got.elements().size()); j++) {
                assertEquals(want.elements().get(j), got.elements().get(j), where + " element " + j);
            }
            assertEquals(want.elements().size(), got.elements().size(), where + ": elements");
        }
        assertEquals(expected.size(), actual.size(), name + ": sections");
        assertTrue(expected.size() > 4, "the trace is of more than its sequence header");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("every frame's size, bit depth and tiles are FFmpeg's")
    void frameSizes(String name) {
        var summaries = HeaderTrace.load(trace(name)).sections().stream()
                .map(s -> SUMMARY.matcher(s.heading()))
                .filter(Matcher::matches)
                .toList();
        var headers = Run.of(name).frameHeaders();
        assertEquals(summaries.size(), headers.size(), name + ": frames");
        for (var i = 0; i < headers.size(); i++) {
            var m = summaries.get(i);
            var header = headers.get(i);
            var size = header.frameSize();
            var color = header.sequenceHeader().colorConfig();
            var where = name + " frame " + i;
            assertEquals(Integer.parseInt(m.group(1)), header.orderHint(), where);
            assertEquals(Integer.parseInt(m.group(2)), size.frameWidth(), where);
            assertEquals(Integer.parseInt(m.group(3)), size.frameHeight(), where);
            assertEquals(Integer.parseInt(m.group(4)), size.upscaledWidth(), where);
            assertEquals(Integer.parseInt(m.group(5)), size.renderWidth(), where);
            assertEquals(Integer.parseInt(m.group(6)), size.renderHeight(), where);
            assertEquals(Integer.parseInt(m.group(7)), color.subsamplingX() + 1, where);
            assertEquals(Integer.parseInt(m.group(8)), color.subsamplingY() + 1, where);
            assertEquals(Integer.parseInt(m.group(9)), color.bitDepth(), where);
            assertEquals(Integer.parseInt(m.group(10)), header.tileInfo().tileRows(), where);
            assertEquals(Integer.parseInt(m.group(11)), header.tileInfo().tileCols(), where);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("the OBUs tile each temporal unit, and the tiles each frame's tile groups")
    void tiles(String name) {
        var run = Run.of(name);
        for (var unit : run.stream().units()) {
            var obus = Obu.split(unit);
            assertEquals(0, obus.getFirst().offset());
            for (var i = 1; i < obus.size(); i++) {
                assertEquals(obus.get(i - 1).end(), obus.get(i).offset());
            }
            assertEquals(unit.length, obus.getLast().end());
        }
        var frames = 0;
        var tiles = 0;
        FrameHeader current = null;
        for (var parsed : run.parsed()) {
            TileGroup group;
            switch (parsed.content()) {
                case Frame frame -> {
                    group = frame.tileGroup();
                    current = frame.header();
                }
                case FrameHeader header -> {
                    current = header;
                    continue;
                }
                case TileGroup only -> group = only;
                default -> {
                    continue;
                }
            }
            assertTrue(current != null, "a tile group after its frame header");
            var sizeBytes = current.tileInfo().tileSizeBytes();
            var list = group.tiles();
            for (var i = 0; i + 1 < list.size(); i++) {
                var tile = list.get(i);
                var next = list.get(i + 1);
                assertEquals(tile.number() + 1, next.number());
                // Each tile but the group's last has its size before it.
                var sizeField = i + 1 < list.size() - 1 ? sizeBytes : 0;
                assertEquals(tile.offset() + tile.size() + sizeField, next.offset(), name + " tile " + i);
            }
            var last = list.getLast();
            assertEquals(parsed.obu().size(), last.offset() + last.size(), "the last tile ends with its OBU");
            assertTrue(list.stream().allMatch(t -> t.size() > 0));
            tiles += list.size();
            if (group.last()) {
                frames++;
            }
        }
        var headers = run.frameHeaders();
        assertEquals(headers.size(), frames, name + ": every frame complete");
        assertEquals(headers.stream().mapToInt(h -> h.tileInfo().tiles()).sum(), tiles, name + ": every tile");
    }

    @Test
    @DisplayName("the tiles of a frame of four are in raster order, each where its size says")
    void fourTiles() {
        var run = Run.of("clip-av1-tools.mkv");
        var frame = run.parsed().stream()
                .map(Av1Parser.Parsed::content)
                .filter(Frame.class::isInstance)
                .map(Frame.class::cast)
                .findFirst()
                .orElseThrow();
        var info = frame.header().tileInfo();
        assertEquals(List.of(2, 2), List.of(info.tileCols(), info.tileRows()));
        assertEquals(List.of(0, 48, 80), info.miColStarts(), "5 superblocks: 3 and 2, MiCols 80");
        assertEquals(List.of(0, 32, 46), info.miRowStarts(), "3 superblocks: 2 and 1, MiRows 46");
        assertEquals(List.of(2, 1), info.widthInSbsMinus1());
        assertEquals(List.of(1, 0), info.heightInSbsMinus1());
        var tiles = frame.tileGroup().tiles();
        assertEquals(
                List.of(List.of(0, 0), List.of(0, 1), List.of(1, 0), List.of(1, 1)),
                tiles.stream().map(t -> List.of(t.row(), t.column())).toList());
    }

    @Test
    @DisplayName("tiles of uneven sizes start where their widths and heights say")
    void unevenTiles() {
        var run = Run.of("clip-av1-resilient.mkv");
        var header = run.frameHeaders().getFirst();
        var info = header.tileInfo();
        assertFalse(info.uniformTileSpacing());
        assertEquals(List.of(1, 2), info.widthInSbsMinus1(), "two and three superblocks wide");
        assertEquals(List.of(0, 1), info.heightInSbsMinus1(), "one and two high");
        assertEquals(List.of(0, 32, 80), info.miColStarts());
        assertEquals(List.of(0, 16, 46), info.miRowStarts());
        // Each frame's four tiles come in two tile group OBUs, which say
        // where they start and end.
        var groups = run.parsed().stream()
                .map(Av1Parser.Parsed::content)
                .filter(TileGroup.class::isInstance)
                .map(TileGroup.class::cast)
                .toList();
        assertEquals(2 * run.frameHeaders().size(), groups.size());
        for (var i = 0; i < groups.size(); i += 2) {
            var first = groups.get(i);
            var second = groups.get(i + 1);
            assertTrue(first.tileStartAndEndPresent() && second.tileStartAndEndPresent());
            assertEquals(List.of(0, 1, false), List.of(first.tgStart(), first.tgEnd(), first.last()));
            assertEquals(List.of(2, 3, true), List.of(second.tgStart(), second.tgEnd(), second.last()));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-av1.mkv", "clip-av1-pq.mkv", "clip-av1-tools.mkv"})
    @DisplayName("the av1C record's sequence header is its configOBUs'")
    void av1C(String name) {
        var stream = Stream.of(name);
        var configured = new Av1Parser()
                .configure(stream.av1C()).stream()
                        .map(Av1Parser.Parsed::content)
                        .filter(SequenceHeader.class::isInstance)
                        .findFirst()
                        .orElseThrow();
        assertEquals(configured, SequenceHeader.fromAv1C(stream.av1C()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> SequenceHeader.fromAv1C(new byte[] {1, 2, 3, 4}));
    }

    @Test
    @DisplayName("an HDR10 stream's sequence header says 10-bit PQ in BT.2020")
    void colour() {
        var stream = Stream.of("clip-av1-pq.mkv");
        var color = SequenceHeader.fromAv1C(stream.av1C()).orElseThrow().colorConfig();
        assertEquals(10, color.bitDepth());
        assertEquals(
                List.of(9, 16, 9),
                List.of(color.colorPrimaries(), color.transferCharacteristics(), color.matrixCoefficients()));
        assertEquals(List.of(1, 1), List.of(color.subsamplingX(), color.subsamplingY()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("each frame is read against the slots the frames before it left, and stored where it says")
    void references(String name) {
        var run = Run.of(name);
        var slots = References.NONE;
        FrameHeader pending = null;
        var pendingSlots = References.NONE;
        for (var parsed : run.parsed()) {
            switch (parsed.content()) {
                case ShowExistingFrame shown -> {
                    assertEquals(slots, parsed.references());
                    var slot = slots.slot(shown.frameToShowMapIdx());
                    assertTrue(slot.valid() && slot.showable(), name + ": shows a showable frame");
                    assertEquals(slot.header(), shown.shown());
                    slots = slots.updated(shown);
                }
                case FrameHeader header -> {
                    assertEquals(slots, parsed.references());
                    pending = header;
                    pendingSlots = slots;
                }
                case Frame frame -> {
                    assertEquals(slots, parsed.references());
                    check(name, frame.header(), slots);
                    slots = slots.updated(frame.header());
                    stored(frame.header(), slots);
                }
                case TileGroup group -> {
                    assertTrue(pending != null, "a tile group after its frame header");
                    assertEquals(pendingSlots, parsed.references());
                    check(name, pending, pendingSlots);
                    if (group.last()) {
                        slots = pendingSlots.updated(pending);
                        stored(pending, slots);
                        pending = null;
                    }
                }
                default -> {}
            }
        }
    }

    /// What `header` must agree with in the slots it was read against.
    private static void check(String name, FrameHeader header, References slots) {
        if (header.frameIsIntra()) {
            assertEquals(List.of(), header.refFrameIdx());
            return;
        }
        assertEquals(References.REFS_PER_FRAME, header.refFrameIdx().size());
        for (var i = 0; i < References.REFS_PER_FRAME; i++) {
            var slot = slots.slot(header.refFrameIdx().get(i));
            assertTrue(slot.valid(), name + ": reference " + i + " is valid");
            assertEquals(slot.orderHint(), header.orderHints().get(References.LAST_FRAME + i));
            assertEquals(
                    header.sequenceHeader().relativeDistance(slot.orderHint(), header.orderHint()) > 0,
                    header.refFrameSignBias(References.LAST_FRAME + i));
            if (header.sequenceHeader().frameIdNumbersPresent()) {
                // expectedFrameId must be the frame id the slot holds.
                assertEquals(slot.frameId(), header.expectedFrameIds().get(i), name + ": frame id of reference " + i);
            }
        }
        if (header.errorResilientMode() && header.sequenceHeader().enableOrderHint()) {
            for (var i = 0; i < References.NUM_REF_FRAMES; i++) {
                assertEquals(slots.slot(i).orderHint(), header.refOrderHint().get(i), "ref_order_hint[" + i + "]");
            }
        }
    }

    /// That `header` is in each slot it refreshes, and in no other.
    private static void stored(FrameHeader header, References slots) {
        for (var i = 0; i < References.NUM_REF_FRAMES; i++) {
            if ((header.refreshFrameFlags() & (1 << i)) != 0) {
                assertEquals(ReferenceFrame.of(header), slots.slot(i));
            } else {
                assertFalse(slots.slot(i).frame().filter(header::equals).isPresent());
            }
        }
    }

    @Test
    @DisplayName("showing a key frame again stores it in every slot, no longer showable")
    void showExistingKeyFrame() {
        var run = Run.of("clip-av1.mkv");
        var key = run.frameHeaders().getFirst();
        assertEquals(FrameHeader.KEY_FRAME, key.frameType());
        var slots = References.NONE.updated(key);
        var hidden = new ReferenceFrame(true, key.orderHint(), true, Optional.of(key));
        slots = new References(List.of(
                slots.slot(0),
                slots.slot(1),
                hidden,
                slots.slot(3),
                slots.slot(4),
                slots.slot(5),
                slots.slot(6),
                slots.slot(7)));
        var shown = new ShowExistingFrame(2, 0, 0, key);
        assertEquals(References.ALL_FRAMES, shown.refreshFrameFlags());
        var after = slots.updated(shown);
        for (var slot : after.slots()) {
            assertEquals(new ReferenceFrame(true, key.orderHint(), false, Optional.of(key)), slot);
        }
    }

    @Test
    @DisplayName("showing an inter frame again changes no slot")
    void showExistingInterFrame() {
        var run = Run.of("clip-av1.mkv");
        var parsed = run.parsed().stream()
                .filter(p -> p.content() instanceof ShowExistingFrame)
                .findFirst()
                .orElseThrow();
        var shown = (ShowExistingFrame) parsed.content();
        assertEquals(FrameHeader.INTER_FRAME, shown.frameType());
        assertEquals(0, shown.refreshFrameFlags());
        assertEquals(parsed.references(), parsed.references().updated(shown));
    }

    @Test
    @DisplayName("an error resilient frame that expects another order hint in a slot invalidates it")
    void expectedOrderHint() {
        var key = Run.of("clip-av1.mkv").frameHeaders().getFirst();
        var slots = References.NONE.updated(key);
        var hints = new ArrayList<>(
                key.refOrderHint().stream().map(h -> key.orderHint()).toList());
        hints.set(3, 5);
        var expecting = slots.expecting(hints);
        assertFalse(expecting.slot(3).valid());
        assertEquals(5, expecting.slot(3).orderHint());
        assertTrue(expecting.slot(2).valid());
        assertEquals(slots, slots.expecting(Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    @DisplayName("set_frame_refs() fills the references from the two it is given and the slots' order hints")
    void setFrameRefs() {
        var seq = Run.of("clip-av1.mkv").frameHeaders().getFirst().sequenceHeader();
        assertEquals(7, seq.orderHintBits());
        // This frame is at 10; LAST is slot 1 at 9, GOLDEN slot 5 at 4.
        // After it: slot 4 at 14 (ALTREF, the latest), slot 6 at 11 (BWDREF,
        // the earliest), slot 3 at 12 (ALTREF2). Before it, latest first:
        // slot 0 at 8 (LAST2), slot 7 at 7 (LAST3).
        assertEquals(
                List.of(1, 0, 7, 5, 6, 3, 4),
                Syntax.list(FrameHeaderReader.setFrameRefs(1, 5, new int[] {8, 9, 6, 12, 14, 4, 11, 7}, 10, seq)));
        // Nothing before it but LAST and GOLDEN: LAST2 and LAST3 take the
        // earliest frame of all, GOLDEN's slot.
        assertEquals(
                List.of(0, 1, 1, 1, 2, 3, 7),
                Syntax.list(FrameHeaderReader.setFrameRefs(0, 1, new int[] {9, 4, 12, 12, 12, 12, 12, 12}, 10, seq)));
    }

    @Test
    @DisplayName("global motion is reconstructed against the primary reference's, near the identity for a pan")
    void globalMotion() {
        var warped = 0;
        for (var header : Run.of("clip-av1-tools.mkv").frameHeaders()) {
            var gm = header.globalMotion();
            for (var ref = References.LAST_FRAME; ref <= References.ALTREF_FRAME; ref++) {
                if (gm.type().get(ref) != GlobalMotion.ROTZOOM) {
                    continue;
                }
                warped++;
                var one = 1 << GlobalMotion.WARPEDMODEL_PREC_BITS;
                assertTrue(
                        Math.abs(gm.params(ref, 2) - one) < one / 16,
                        "scale near 1: " + gm.params().get(ref));
                assertTrue(
                        Math.abs(gm.params(ref, 3)) < one / 16,
                        "rotation near 0: " + gm.params().get(ref));
                assertEquals(-gm.params(ref, 3), gm.params(ref, 4));
                assertEquals(gm.params(ref, 2), gm.params(ref, 5));
            }
        }
        assertTrue(warped > 0, "the pan has global motion");
        // Coded against a reference r in [-16, 17): 0 is r, then r - 1 and
        // r + 1 by turns, mirrored in the upper half; past twice the room on
        // the near side, the value as it stands.
        assertEquals(
                List.of(-10, -11, -9, 4),
                List.of(0, 1, 2, 20).stream()
                        .map(v -> FrameHeaderReader.decodeSignedSubexpWithRef(v, -16, 17, -10))
                        .toList());
        assertEquals(
                List.of(10, 11, 9),
                List.of(0, 1, 2).stream()
                        .map(v -> FrameHeaderReader.decodeSignedSubexpWithRef(v, -16, 17, 10))
                        .toList());
    }

    @Test
    @DisplayName("film grain, segmentation, restoration, delta q and lf and quantizer matrices are read")
    void codingTools() {
        var headers = Run.of("clip-av1-tools.mkv").frameHeaders();
        assertTrue(headers.stream()
                .anyMatch(h -> h.filmGrain().applyGrain()
                        && !h.filmGrain().pointYValue().isEmpty()));
        assertTrue(headers.stream()
                .anyMatch(h -> h.segmentation().enabled() && h.segmentation().lastActiveSegId() > 0));
        assertTrue(headers.stream().anyMatch(h -> h.loopRestoration().usesLr()));
        assertTrue(headers.stream().anyMatch(h -> h.deltaLfPresent()));
        assertTrue(headers.stream().anyMatch(h -> h.quantization().usingQmatrix()));
        var restored = headers.stream()
                .map(FrameHeader::loopRestoration)
                .filter(LoopRestoration::usesLr)
                .findFirst()
                .orElseThrow();
        assertEquals(64 << restored.unitShift(), restored.loopRestorationSize(0));
        var superres = Run.of("clip-av1-superres.mkv").frameHeaders();
        assertTrue(superres.stream().anyMatch(h -> h.frameType() == FrameHeader.INTRA_ONLY_FRAME));
        assertTrue(superres.stream()
                .filter(h -> h.frameSize().useSuperres())
                .allMatch(h ->
                        h.frameSize().superresDenom() == 12 && h.frameSize().frameWidth() == 213));
    }

    @Test
    @DisplayName("a lossless monochrome still picture has the reduced header, and no filters")
    void stillPicture() {
        var header = Run.of("clip-av1-still.mkv").frameHeaders().getFirst();
        var seq = header.sequenceHeader();
        assertTrue(seq.stillPicture() && seq.reducedStillPictureHeader());
        assertEquals(1, seq.colorConfig().numPlanes());
        assertEquals(FrameHeader.KEY_FRAME, header.frameType());
        assertTrue(header.codedLossless() && header.allLossless());
        assertEquals(FrameHeader.ONLY_4X4, header.txMode());
        assertEquals(Cdef.NONE, header.cdef());
        assertEquals(List.of(0, 0, 0, 0), header.loopFilter().level());
        assertEquals(References.ALL_FRAMES, header.refreshFrameFlags());
    }

    @Test
    @DisplayName("with a decoder model, each frame has its presentation and removal times")
    void decoderModel() {
        var headers = Run.of("clip-av1-timing.mkv").frameHeaders();
        var seq = headers.getFirst().sequenceHeader();
        assertEquals(25, seq.timingInfo().orElseThrow().timeScale());
        assertTrue(seq.decoderModelInfo().isPresent());
        assertEquals(
                List.of(0L, 1L, 2L),
                headers.stream().map(FrameHeader::framePresentationTime).toList());
        assertEquals(
                List.of(List.of(0L), List.of(1L), List.of(2L)),
                headers.stream().map(FrameHeader::bufferRemovalTimes).toList());
    }

    @Test
    @DisplayName("a malformed OBU is IllegalArgumentException, and leaves the parser as it was")
    void malformed() {
        var stream = Stream.of("clip-av1.mkv");
        var parser = new Av1Parser();
        parser.configure(stream.av1C());
        var first = stream.units().getFirst();
        assertThrows(IllegalArgumentException.class, () -> Obu.split(new byte[] {(byte) 0x80}));
        assertThrows(IllegalArgumentException.class, () -> Obu.split(Arrays.copyOf(first, first.length - 1)));
        // A tile group OBU with no frame header before it.
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[] {0x22, 0x01, 0x00}));
        // The first frame, cut short within its header.
        var obus = Obu.split(first);
        var frame = obus.stream()
                .filter(o -> o.header().type() == ObuHeader.FRAME)
                .findFirst()
                .orElseThrow();
        var cut = Arrays.copyOf(first, frame.payloadOffset() + 2);
        var shortened = new Obu(frame.header(), frame.offset(), frame.headerSize(), 2);
        assertThrows(IllegalArgumentException.class, () -> parser.read(cut, shortened));
        assertEquals(References.NONE, parser.references());
        parser.parse(first);
        assertTrue(parser.references().slot(0).valid());
    }
}
