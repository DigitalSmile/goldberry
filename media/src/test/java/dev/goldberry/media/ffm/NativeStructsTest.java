package dev.goldberry.media.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemoryLayout;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The struct table against the probe output committed for linux-x64, which is
/// the LP64 shape every target but Windows has, and the LLP64 shape by its
/// arithmetic.
@DisplayName("NativeStructs")
class NativeStructsTest {

    @Test
    @DisplayName("agrees with the linux-x64 probe, struct for struct and field for field")
    void agreesWithTheProbe() {
        var problems = LayoutCheck.verify(LayoutFixture.load(), NativeStructs.all(8));
        assertEquals(List.of(), problems, String.join("\n", problems));
    }

    @Test
    @DisplayName("declares a layout for every struct the probe reports, and no other")
    void sameStructs() {
        var declared = NativeStructs.all(8).stream()
                .map(s -> s.name().orElseThrow())
                .sorted()
                .toList();
        var reported = LayoutFixture.load().structs().keySet().stream().sorted().toList();
        assertEquals(reported, declared);
    }

    @Test
    @DisplayName("every constant the bindings need is in the probe's output")
    void constants() {
        var constants = NativeConstants.from(LayoutFixture.load());
        assertEquals(-11, constants.dav1d().errAgain());
        assertEquals(1, constants.dav1d().layoutI420());
        assertEquals(0, constants.vpx().codecOk());
        assertEquals(2304, constants.mp3().maxSamplesPerFrame());
        assertEquals(4, constants.mp3().sampleBytes());
        assertEquals(0, constants.flac().initStatusOk());
        assertEquals(0, constants.opus().ok());
    }

    @Test
    @DisplayName("the Windows shapes: a 4-byte long moves what follows it")
    void llp64() {
        assertEquals(56, NativeStructs.vpxCodecCtx(4).byteSize());
        assertEquals(
                4,
                NativeStructs.vpxCodecCtx(4)
                        .select(MemoryLayout.PathElement.groupElement("init_flags"))
                        .byteSize());
        assertEquals(40, NativeStructs.oggPacket(4).byteSize());
        assertEquals(24, NativeStructs.oggPacket(4).byteOffset(MemoryLayout.PathElement.groupElement("granulepos")));
        assertEquals(40, NativeStructs.vorbisInfo(4).byteSize());
        assertEquals(32, NativeStructs.vorbisInfo(4).byteOffset(MemoryLayout.PathElement.groupElement("codec_setup")));
        assertEquals(128, NativeStructs.vorbisDspState(4).byteSize());
        assertEquals(144, NativeStructs.vorbisBlock(4).byteSize());
        // Every struct of a pointer or an int aligns to four at least; the
        // content light level, two shorts, aligns to two as C aligns it.
        assertEquals(2, NativeStructs.DAV1D_CONTENT_LIGHT_LEVEL.byteAlignment());
        assertTrue(NativeStructs.all(4).stream()
                .filter(s -> s != NativeStructs.DAV1D_CONTENT_LIGHT_LEVEL)
                .allMatch(s -> s.byteAlignment() >= 4));
    }

    @Test
    @DisplayName("this target's table is one of the two shapes")
    void thisTarget() {
        assertEquals(NativeStructs.all(NativeTypes.C_LONG_SIZE).size(), NativeStructs.ALL.size());
        assertEquals(NativeTypes.C_LONG_SIZE == 8 ? 48 : 40, NativeStructs.OGG_PACKET.byteSize());
    }
}
