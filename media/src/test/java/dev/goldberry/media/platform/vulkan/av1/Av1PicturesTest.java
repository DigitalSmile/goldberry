package dev.goldberry.media.platform.vulkan.av1;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.bitstream.av1.Av1Parser;
import dev.goldberry.media.bitstream.av1.Frame;
import dev.goldberry.media.bitstream.av1.FrameHeader;
import dev.goldberry.media.bitstream.av1.SequenceHeader;
import dev.goldberry.media.bitstream.av1.ShowExistingFrame;
import dev.goldberry.media.bitstream.av1.TileGroup;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.Containers;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoAV1Quantization;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoAV1SequenceHeader;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoAV1TileInfo;
import dev.goldberry.natives.vulkan.VkStructs.StdVideoDecodeAV1PictureInfo;

/// AV1's headers as Vulkan's structs, held to the parser's fixtures: what the
/// decoder would hand the device for every frame of a stream, with no device.
@DisplayName("AV1's headers for Vulkan")
@SuppressWarnings("restricted")
class Av1PicturesTest {

    /// A fixture's `av1C` record and its temporal units.
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

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-av1.mkv", "clip-av1-tools.mkv", "clip-av1-resilient.mkv", "clip-av1-timing.mkv"})
    @DisplayName(
            "every frame's picture info places its header and tiles in its temporal unit, and its references in slots")
    void everyFrame(String name) {
        var stream = Stream.of(name);
        var parser = new Av1Parser();
        parser.configure(stream.av1C());
        var slots = new Av1Slots();
        var frames = 0;
        try (var arena = Arena.ofConfined()) {
            for (var unit : stream.units()) {
                FrameHeader header = null;
                Av1Pictures.Layout layout = null;
                for (var parsed : parser.parse(unit)) {
                    var offset = parsed.obu().offset();
                    TileGroup group = null;
                    switch (parsed.content()) {
                        case FrameHeader h -> {
                            header = h;
                            layout = new Av1Pictures.Layout(offset, List.of(), List.of());
                        }
                        case Frame f -> {
                            header = f.header();
                            layout = new Av1Pictures.Layout(offset, List.of(), List.of());
                            group = f.tileGroup();
                        }
                        case TileGroup t -> group = t;
                        case ShowExistingFrame shown -> {
                            assertNotEquals(Av1Slots.NONE, slots.slotOf(shown.frameToShowMapIdx()));
                            if (shown.frameType() == FrameHeader.KEY_FRAME) {
                                slots.loadEverywhere(shown.frameToShowMapIdx());
                            }
                        }
                        default -> {}
                    }
                    if (group == null || layout == null || header == null) {
                        continue;
                    }
                    layout = layout.with(group, offset);
                    if (!group.last()) {
                        continue;
                    }
                    check(arena, unit, header, layout, slots);
                    slots.refresh(header.refreshFrameFlags(), slots.free());
                    frames++;
                }
            }
        }
        assertTrue(frames > 0);
    }

    private static void check(Arena arena, byte[] unit, FrameHeader header, Av1Pictures.Layout layout, Av1Slots slots) {
        var named = slots.named(header.refFrameIdx());
        if (!header.frameIsIntra()) {
            for (var slot : named) {
                assertNotEquals(Av1Slots.NONE, slot, "an inter frame's references are all in slots");
            }
        }
        var info = Av1Pictures.pictureInfo(arena, header, layout, named);
        var tiles = info.get(JAVA_INT, VkStructs.VkVideoDecodeAV1PictureInfoKHR.tileCount);
        assertEquals(header.tileInfo().tiles(), tiles, "every tile of the frame");
        var offsets = info.get(ADDRESS, VkStructs.VkVideoDecodeAV1PictureInfoKHR.pTileOffsets)
                .reinterpret(4L * tiles);
        var sizes = info.get(ADDRESS, VkStructs.VkVideoDecodeAV1PictureInfoKHR.pTileSizes)
                .reinterpret(4L * tiles);
        var headerOffset = info.get(JAVA_INT, VkStructs.VkVideoDecodeAV1PictureInfoKHR.frameHeaderOffset);
        for (var i = 0; i < tiles; i++) {
            var at = offsets.getAtIndex(JAVA_INT, i);
            var size = sizes.getAtIndex(JAVA_INT, i);
            assertTrue(at > headerOffset && size > 0 && at + size <= unit.length, "tile " + i + " inside the unit");
        }
        var std = info.get(ADDRESS, VkStructs.VkVideoDecodeAV1PictureInfoKHR.pStdPictureInfo)
                .reinterpret(StdVideoDecodeAV1PictureInfo.LAYOUT.byteSize());
        assertEquals(header.frameType(), std.get(JAVA_INT, StdVideoDecodeAV1PictureInfo.frame_type));
        assertEquals(
                header.orderHint(), Byte.toUnsignedInt(std.get(JAVA_BYTE, StdVideoDecodeAV1PictureInfo.OrderHint)));
        var tileInfo = std.get(ADDRESS, StdVideoDecodeAV1PictureInfo.pTileInfo)
                .reinterpret(StdVideoAV1TileInfo.LAYOUT.byteSize());
        assertEquals(header.tileInfo().tileCols(), tileInfo.get(JAVA_BYTE, StdVideoAV1TileInfo.TileCols));
        var colStarts = tileInfo.get(ADDRESS, StdVideoAV1TileInfo.pMiColStarts).reinterpret(2L);
        assertEquals(0, colStarts.get(JAVA_SHORT, 0));
        var quantization = std.get(ADDRESS, StdVideoDecodeAV1PictureInfo.pQuantization)
                .reinterpret(StdVideoAV1Quantization.LAYOUT.byteSize());
        assertEquals(
                header.quantization().baseQIdx(),
                Byte.toUnsignedInt(quantization.get(JAVA_BYTE, StdVideoAV1Quantization.base_q_idx)));
        for (var i = 0; i < 7; i++) {
            assertEquals(
                    named[i],
                    info.get(JAVA_INT, VkStructs.VkVideoDecodeAV1PictureInfoKHR.referenceNameSlotIndices + 4L * i));
        }
        assertNotEquals(MemorySegment.NULL, Av1Pictures.slotInfo(arena, header, header.orderHint()));
    }

    @Test
    @DisplayName("the sequence header goes over whole: profile, sizes, colour")
    void sequenceHeader() {
        var sequence =
                SequenceHeader.fromAv1C(Stream.of("clip-av1-pq.mkv").av1C()).orElseThrow();
        try (var arena = Arena.ofConfined()) {
            var info = Av1Pictures.createInfo(arena, sequence);
            var s = info.get(ADDRESS, VkStructs.VkVideoDecodeAV1SessionParametersCreateInfoKHR.pStdSequenceHeader)
                    .reinterpret(StdVideoAV1SequenceHeader.LAYOUT.byteSize());
            assertEquals(sequence.seqProfile(), s.get(JAVA_INT, StdVideoAV1SequenceHeader.seq_profile));
            assertEquals(
                    sequence.maxFrameWidthMinus1(),
                    Short.toUnsignedInt(s.get(JAVA_SHORT, StdVideoAV1SequenceHeader.max_frame_width_minus_1)));
            var colour = s.get(ADDRESS, StdVideoAV1SequenceHeader.pColorConfig)
                    .reinterpret(VkStructs.StdVideoAV1ColorConfig.LAYOUT.byteSize());
            assertEquals(10, colour.get(JAVA_BYTE, VkStructs.StdVideoAV1ColorConfig.BitDepth));
            assertEquals(16, colour.get(JAVA_INT, VkStructs.StdVideoAV1ColorConfig.transfer_characteristics), "PQ");
        }
    }

    @Test
    @DisplayName("Main 4:2:0 is claimed; film grain and a monochrome still are left to the built-in decoder")
    void profiles() {
        var plain = SequenceHeader.fromAv1C(Stream.of("clip-av1.mkv").av1C()).orElseThrow();
        assertTrue(Av1VulkanDecoder.profile(plain).isPresent());
        var grain =
                SequenceHeader.fromAv1C(Stream.of("clip-av1-tools.mkv").av1C()).orElseThrow();
        assertTrue(grain.filmGrainParamsPresent());
        assertTrue(Av1VulkanDecoder.profile(grain).isEmpty());
        var still =
                SequenceHeader.fromAv1C(Stream.of("clip-av1-still.mkv").av1C()).orElseThrow();
        assertTrue(Av1VulkanDecoder.profile(still).isEmpty());
    }
}
