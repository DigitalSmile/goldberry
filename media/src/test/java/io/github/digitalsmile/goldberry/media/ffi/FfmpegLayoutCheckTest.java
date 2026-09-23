package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.paddingLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FfmpegLayoutCheck")
class FfmpegLayoutCheckTest {

    /// A layout file for one made-up struct, `S { int a; int64_t b; }`, and the
    /// pinned majors.
    private static FfmpegLayout probe(String extra) throws IOException {
        var text = new StringBuilder();
        for (var library : FfmpegLibrary.values()) {
            text.append("version.")
                    .append(library.stem())
                    .append(".major=")
                    .append(library.pinnedMajor())
                    .append('\n');
        }
        text.append("""
                struct.S.sizeof=16
                struct.S.alignof=8
                field.S.a.offset=0
                field.S.a.sizeof=4
                field.S.b.offset=8
                field.S.b.sizeof=8
                """);
        text.append(extra);
        return FfmpegLayout.parse(new StringReader(text.toString()));
    }

    private static final java.lang.foreign.StructLayout GOOD = structLayout(
                    JAVA_INT.withName("a"), paddingLayout(4), JAVA_LONG.withName("b"))
            .withName("S");

    @Test
    @DisplayName("finds nothing when Java and C agree")
    void agrees() throws IOException {
        assertEquals(List.of(), FfmpegLayoutCheck.verify(probe(""), List.of(GOOD)));
    }

    @Test
    @DisplayName("reports a field at the wrong offset, and the struct size it moves")
    void wrongOffset() throws IOException {
        var shifted = structLayout(JAVA_INT.withName("a"), paddingLayout(12), JAVA_LONG.withName("b"))
                .withName("S");
        var problems = FfmpegLayoutCheck.verify(probe(""), List.of(shifted));
        assertTrue(problems.contains("S.b: Java offset=16, C offset=8"), problems.toString());
        assertTrue(problems.contains("S: Java sizeof=24, C sizeof=16"), problems.toString());
    }

    @Test
    @DisplayName("reports a field the probe lists and Java forgot")
    void forgottenField() throws IOException {
        var problems = FfmpegLayoutCheck.verify(probe("field.S.c.offset=12\nfield.S.c.sizeof=4\n"), List.of(GOOD));
        assertEquals(List.of("S.c is reported by the probe but not declared in FfmpegStructs"), problems);
    }

    @Test
    @DisplayName("reports a struct Java declares and nothing verifies, and one the probe has and Java lacks")
    void structs() throws IOException {
        var other = structLayout(JAVA_INT.withName("x")).withName("T");
        var problems = FfmpegLayoutCheck.verify(probe("struct.U.sizeof=4\nstruct.U.alignof=4\n"), List.of(GOOD, other));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("T is declared in Java")), problems.toString());
        assertTrue(
                problems.contains("U is reported by the probe but not declared in FfmpegStructs"), problems.toString());
    }

    @Test
    @DisplayName("reports a layout file of another major, and one that says nothing about a library")
    void majors() throws IOException {
        var text = """
                version.avformat.major=61
                struct.S.sizeof=16
                struct.S.alignof=8
                field.S.a.offset=0
                field.S.a.sizeof=4
                field.S.b.offset=8
                field.S.b.sizeof=8
                """;
        var problems = FfmpegLayoutCheck.verify(FfmpegLayout.parse(new StringReader(text)), List.of(GOOD));
        assertTrue(
                problems.contains("the layout file describes avformat 61, but these bindings are written against 62"),
                problems.toString());
        assertTrue(problems.contains("the layout file does not say which avutil it describes"), problems.toString());
    }
}
