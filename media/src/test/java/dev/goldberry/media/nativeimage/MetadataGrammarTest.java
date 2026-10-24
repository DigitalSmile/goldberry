package dev.goldberry.media.nativeimage;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.ffm.DowncallShape;

/// The tracing agent's grammar, which both halves of the module's metadata are
/// written in.
@DisplayName("MetadataGrammar")
class MetadataGrammarTest {

    @Test
    @DisplayName("spells a descriptor as the tracing agent does")
    void spelling() {
        assertEquals(
                "{\"returnType\": \"jint\", \"parameterTypes\": [\"void*\", \"void*\", \"void*\", \"void*\", \"void*\"]}",
                MetadataGrammar.entry(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)));
        assertEquals(
                "{\"returnType\": \"void\", \"parameterTypes\": [\"jlong\"]}",
                MetadataGrammar.entry(FunctionDescriptor.ofVoid(JAVA_LONG)));
        // Core Media's CMTime, the struct VideoToolbox's callback takes by value.
        assertEquals(
                "struct(jlong,jint,jint,jlong)",
                MetadataGrammar.spell(MemoryLayout.structLayout(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG)));
    }

    @Test
    @DisplayName("spells a variadic downcall with the option that says where its arguments begin")
    void variadic() {
        // opus_decoder_ctl(st, OPUS_RESET_STATE): no variadic argument at all.
        var reset = FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT);
        assertEquals(
                "{\"returnType\": \"jint\", \"parameterTypes\": [\"void*\", \"jint\"],"
                        + " \"options\": {\"firstVariadicArg\": 2}}",
                MetadataGrammar.entry(DowncallShape.variadic(reset, 2)));
        assertEquals(MetadataGrammar.entry(reset), MetadataGrammar.entry(DowncallShape.of(reset)));
    }

    @Test
    @DisplayName("names every value layout, and spells unions, sequences and padding")
    void everyLayout() {
        assertEquals(
                List.of("void*", "jint", "jlong", "jfloat", "jdouble", "jbyte", "jshort", "jchar", "jboolean"),
                Stream.of(
                                ADDRESS,
                                JAVA_INT,
                                JAVA_LONG,
                                JAVA_FLOAT,
                                JAVA_DOUBLE,
                                JAVA_BYTE,
                                JAVA_SHORT,
                                JAVA_CHAR,
                                JAVA_BOOLEAN)
                        .map(MetadataGrammar::spell)
                        .toList());
        assertEquals(
                "union(jint,sequence(2, jshort))",
                MetadataGrammar.spell(MemoryLayout.unionLayout(JAVA_INT, MemoryLayout.sequenceLayout(2, JAVA_SHORT))));
        assertEquals(
                "struct(jint,padding(4),void*)",
                MetadataGrammar.spell(MemoryLayout.structLayout(JAVA_INT, MemoryLayout.paddingLayout(4), ADDRESS)));
    }

    @Test
    @DisplayName("writes a resources section only when there are globs")
    void resources() {
        var call = FunctionDescriptor.ofVoid(JAVA_LONG);

        var without = MetadataGrammar.render(List.of(), List.of(DowncallShape.of(call)), List.of());
        assertTrue(without.startsWith("{\n  \"foreign\": {\n"), without);
        assertFalse(without.contains("resources"), without);

        var with = MetadataGrammar.render(List.of("a/**", "b/**"), List.of(DowncallShape.of(call)), List.of(call));
        assertTrue(
                with.startsWith(
                        "{\n  \"resources\": [\n    {\"glob\": \"a/**\"},\n    {\"glob\": \"b/**\"}\n  ],\n  \"foreign\""),
                with);
        assertEquals(
                2, with.lines().filter(line -> line.contains("\"returnType\"")).count());
        assertTrue(with.endsWith("}\n"));
    }
}
