package dev.goldberry.vector.nativeimage;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The tracing agent's spelling, which is what a native image reads back.
@DisplayName("MetadataGrammar")
class MetadataGrammarTest {

    @Test
    @DisplayName("spells a downcall as the agent does")
    void entry() {
        assertEquals(
                "{\"returnType\": \"jint\", \"parameterTypes\": [\"void*\", \"jfloat\", \"jboolean\"]}",
                MetadataGrammar.entry(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_BOOLEAN)));
        assertEquals(
                "{\"returnType\": \"void\", \"parameterTypes\": []}",
                MetadataGrammar.entry(FunctionDescriptor.ofVoid()));
    }

    @Test
    @DisplayName("spells a struct by its members, padding included")
    void struct() {
        assertEquals(
                "struct(jint,padding(4),jlong)",
                MetadataGrammar.spell(MemoryLayout.structLayout(JAVA_INT, MemoryLayout.paddingLayout(4), JAVA_LONG)));
    }

    @Test
    @DisplayName("writes resources first, then the downcalls")
    void render() {
        assertEquals("""
                {
                  "resources": [
                    {"glob": "a/**"}
                  ],
                  "foreign": {
                    "downcalls": [
                      {"returnType": "jint", "parameterTypes": []}
                    ]
                  }
                }
                """, MetadataGrammar.render(List.of("a/**"), List.of(FunctionDescriptor.of(JAVA_INT))));
    }
}
