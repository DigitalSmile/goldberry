package dev.goldberry.media.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NativeLayout")
class NativeLayoutTest {

    private static NativeLayout parse(String text) throws IOException {
        return NativeLayout.parse(new StringReader(text));
    }

    @Test
    @DisplayName("reads the five kinds of key, skipping comments and blank lines")
    void reads() throws IOException {
        var layout = parse("""
                # a comment

                abi=1
                version.dav1d.api.major=7
                struct.Dav1dData.sizeof=72
                struct.Dav1dData.alignof=8
                field.Dav1dData.sz.offset=8
                field.Dav1dData.sz.sizeof=8
                const.DAV1D_ERR(EAGAIN)=-11
                """);
        assertEquals(OptionalInt.of(1), layout.abi());
        assertEquals(OptionalInt.of(7), layout.major("dav1d"));
        assertEquals(Optional.of(new NativeLayout.Extent(72, 8)), layout.struct("Dav1dData"));
        assertEquals(Optional.of(new NativeLayout.Extent(8, 8)), layout.field("Dav1dData", "sz"));
        assertEquals(OptionalLong.of(-11), layout.constant("DAV1D_ERR(EAGAIN)"));
        assertEquals(OptionalLong.empty(), layout.constant("DAV1D_ERR(ENOENT)"));
        assertEquals(-11, layout.require("DAV1D_ERR(EAGAIN)"));
    }

    @Test
    @DisplayName("has no ABI until the file says one")
    void noAbi() throws IOException {
        assertEquals(OptionalInt.empty(), parse("const.A=1\n").abi());
    }

    @Test
    @DisplayName("keeps the probe's order")
    void order() throws IOException {
        var layout = parse("const.B=2\nconst.A=1\n");
        assertEquals(List.of("B", "A"), List.copyOf(layout.constants().keySet()));
    }

    @Test
    @DisplayName("names a constant it was asked for and does not have")
    void missingConstant() throws IOException {
        var layout = parse("const.A=1\n");
        var e = assertThrows(IllegalArgumentException.class, () -> layout.require("B"));
        assertTrue(e.getMessage().contains("const.B"), e.getMessage());
    }

    @Test
    @DisplayName("refuses a malformed line, naming its number")
    void malformed() {
        var noValue = assertThrows(IllegalArgumentException.class, () -> parse("const.A=1\nnonsense\n"));
        assertTrue(noValue.getMessage().startsWith("layout file line 2"), noValue.getMessage());
        var notANumber = assertThrows(IllegalArgumentException.class, () -> parse("const.A=one\n"));
        assertTrue(notANumber.getMessage().contains("not a number"), notANumber.getMessage());
        var unknownKind = assertThrows(IllegalArgumentException.class, () -> parse("enum.A=1\n"));
        assertTrue(unknownKind.getMessage().contains("unknown kind"), unknownKind.getMessage());
        var half = assertThrows(IllegalArgumentException.class, () -> parse("struct.S.sizeof=8\n"));
        assertTrue(half.getMessage().contains("half"), half.getMessage());
    }
}
