package dev.goldberry.media.platform.windows;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// GUIDs in the byte order Windows keeps them in, checked against encodings
/// written out by hand from `guiddef.h`'s rule: the first three fields
/// little-endian, the last eight bytes as written.
@DisplayName("Guid")
class GuidTest {

    @Test
    @DisplayName("writes the first three fields little-endian and the last eight bytes as written")
    void byteOrder() {
        // IID_IMFTransform, {bf94c121-5b05-4e6f-8000-ba598961414d}.
        assertArrayEquals(
                HexFormat.of().parseHex("21c194bf" + "055b" + "6f4e" + "8000" + "ba598961414d"),
                MfGuids.IID_IMFTransform.bytes());
    }

    @Test
    @DisplayName("a FourCC subtype starts with its four characters, in order")
    void fourCc() {
        assertArrayEquals(
                HexFormat.of().parseHex("4e563132" + "0000" + "1000" + "800000aa00389b71"),
                MfGuids.MFVideoFormat_NV12.bytes());
        assertEquals(Guid.parse("3231564E-0000-0010-8000-00AA00389B71"), MfGuids.MFVideoFormat_NV12);
        assertEquals("NV12", new String(MfGuids.MFVideoFormat_NV12.bytes(), 0, 4, StandardCharsets.US_ASCII));
        // The major types are FourCC GUIDs too: 'vids' and 'auds'.
        assertEquals(Guid.fourCc("vids"), MfGuids.MFMediaType_Video);
        assertEquals(Guid.fourCc("auds"), MfGuids.MFMediaType_Audio);
        assertEquals("34363248-0000-0010-8000-00aa00389b71", MfGuids.MFVideoFormat_H264.toString());
        assertEquals("43564548-0000-0010-8000-00aa00389b71", MfGuids.MFVideoFormat_HEVC.toString());
        assertEquals("30313050-0000-0010-8000-00aa00389b71", MfGuids.MFVideoFormat_P010.toString());
    }

    @Test
    @DisplayName("a WAVE_FORMAT tag subtype is the tag in the first field")
    void waveFormatTag() {
        assertArrayEquals(
                HexFormat.of().parseHex("10160000" + "0000" + "1000" + "800000aa00389b71"),
                MfGuids.MFAudioFormat_AAC.bytes());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "e06d802c-db46-11cf-b4d1-00805f6cbbea",
                "{E06D802C-DB46-11CF-B4D1-00805F6CBBEA}",
                "E06d802c-dB46-11cf-B4d1-00805f6cBBea"
            })
    @DisplayName("reads either case, with or without braces, and prints lower case without")
    void text(String text) {
        var guid = Guid.parse(text);
        assertEquals("e06d802c-db46-11cf-b4d1-00805f6cbbea", guid.toString());
        assertEquals(MfGuids.MFAudioFormat_Dolby_AC3, guid);
        assertEquals(MfGuids.MFAudioFormat_Dolby_AC3.hashCode(), guid.hashCode());
    }

    @Test
    @DisplayName("refuses what is not a GUID or a FourCC")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> Guid.parse("e06d802c-db46-11cf-b4d1"));
        assertThrows(IllegalArgumentException.class, () -> Guid.parse("g06d802c-db46-11cf-b4d1-00805f6cbbea"));
        assertThrows(IllegalArgumentException.class, () -> Guid.fourCc("NV1"));
        assertThrows(IllegalArgumentException.class, () -> Guid.fourCc("NV1é"));
    }

    @Test
    @DisplayName("round-trips through native memory, and the constant's segment holds its bytes")
    void memory() {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(Guid.LAYOUT.byteSize() + 3);
            MfGuids.MF_MT_SUBTYPE.write(segment, 3);
            assertEquals(MfGuids.MF_MT_SUBTYPE, Guid.read(segment, 3));
            assertNotEquals(MfGuids.MF_MT_MAJOR_TYPE, Guid.read(segment, 3));
        }
        var constant = MfGuids.MF_MT_FRAME_SIZE.segment();
        assertEquals(MfGuids.MF_MT_FRAME_SIZE, Guid.read(constant, 0));
        assertEquals(constant, MfGuids.MF_MT_FRAME_SIZE.segment(), "allocated once");
        assertEquals(16, Guid.LAYOUT.byteSize());
        assertEquals(4, Guid.LAYOUT.byteAlignment());
    }

    @Test
    @DisplayName("every GUID constant is distinct")
    void distinct() {
        var guids = Arrays.stream(MfGuids.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == Guid.class)
                .map(field -> {
                    try {
                        return field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new AssertionError(e);
                    }
                })
                .toList();
        assertEquals(guids.size(), new HashSet<>(guids).size());
        assertEquals(31, guids.size());
    }
}
