package dev.goldberry.media.ffm;

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

@DisplayName("LayoutCheck")
class LayoutCheckTest {

    private static NativeLayout probe(String text) throws IOException {
        return NativeLayout.parse(new StringReader(text));
    }

    private static final String PAIR = """
            struct.Pair.sizeof=16
            struct.Pair.alignof=8
            field.Pair.first.offset=0
            field.Pair.first.sizeof=4
            field.Pair.second.offset=8
            field.Pair.second.sizeof=8
            """;

    @Test
    @DisplayName("agrees with a struct declared as C laid it out")
    void agrees() throws IOException {
        var pair = structLayout(JAVA_INT.withName("first"), paddingLayout(4), JAVA_LONG.withName("second"))
                .withName("Pair");
        assertEquals(List.of(), LayoutCheck.verify(probe(PAIR), List.of(pair)));
    }

    @Test
    @DisplayName("names every disagreement, not the first: a size, an alignment, an offset")
    void disagrees() throws IOException {
        var pair = structLayout(JAVA_INT.withName("first"), JAVA_INT.withName("second"))
                .withName("Pair");
        var problems = LayoutCheck.verify(probe(PAIR), List.of(pair));
        assertTrue(problems.stream().anyMatch(p -> p.contains("Java sizeof=8, C sizeof=16")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Java alignof=4, C alignof=8")), problems.toString());
        assertTrue(
                problems.stream().anyMatch(p -> p.contains("Pair.second: Java offset=4, C offset=8")),
                problems.toString());
    }

    @Test
    @DisplayName("names a field or a struct only one side knows")
    void oneSided() throws IOException {
        var pair = structLayout(JAVA_INT.withName("first"), JAVA_INT.withName("third"), JAVA_LONG.withName("second"))
                .withName("Pair");
        var other = structLayout(JAVA_INT.withName("x")).withName("Other");
        var problems = LayoutCheck.verify(
                probe(PAIR + "struct.Extra.sizeof=4\nstruct.Extra.alignof=4\n"), List.of(pair, other));
        assertTrue(problems.stream().anyMatch(p -> p.contains("Pair.third is declared in Java")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Other is declared in Java")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Extra is reported by the probe")), problems.toString());
    }
}
