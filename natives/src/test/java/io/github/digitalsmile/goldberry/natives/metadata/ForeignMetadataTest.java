package io.github.digitalsmile.goldberry.natives.metadata;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.CompiledClasses;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// The generated `foreign` section has to say what GraalVM's agent would have
/// said, in the agent's spelling — the one grammar `native-image` reads.
@DisplayName("the generated foreign metadata")
class ForeignMetadataTest {

    /// One traced entry, whichever of the three lists it is in. The agent's
    /// output is pretty-printed, so whitespace inside the list is free.
    private static final Pattern TRACED_ENTRY =
            Pattern.compile("\"returnType\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"parameterTypes\"\\s*:\\s*\\[([^\\]]*)\\]");

    @Test
    @DisplayName("spells a value layout by its JNI name and an address as void*")
    void valueNames() {
        assertAll(
                () -> assertEquals("jint", ForeignMetadata.spell(JAVA_INT)),
                () -> assertEquals("jlong", ForeignMetadata.spell(JAVA_LONG)),
                () -> assertEquals("jfloat", ForeignMetadata.spell(JAVA_FLOAT)),
                () -> assertEquals("jdouble", ForeignMetadata.spell(JAVA_DOUBLE)),
                () -> assertEquals("jboolean", ForeignMetadata.spell(JAVA_BOOLEAN)),
                () -> assertEquals("jbyte", ForeignMetadata.spell(JAVA_BYTE)),
                () -> assertEquals("jshort", ForeignMetadata.spell(JAVA_SHORT)),
                () -> assertEquals("jchar", ForeignMetadata.spell(JAVA_CHAR)),
                () -> assertEquals("void*", ForeignMetadata.spell(ADDRESS)));
    }

    @Test
    @DisplayName("spells Yoga's size struct as the agent recorded it")
    void structByValue() {
        assertEquals("struct(jfloat,jfloat)", ForeignMetadata.spell(Layouts.YG_SIZE.layout()));
    }

    @Test
    @DisplayName("composes padding, sequences and unions the way the grammar nests them")
    void composedLayouts() {
        var layout = MemoryLayout.structLayout(
                JAVA_BYTE,
                MemoryLayout.paddingLayout(7),
                ADDRESS,
                MemoryLayout.sequenceLayout(10, MemoryLayout.unionLayout(JAVA_LONG, JAVA_DOUBLE)));
        assertEquals(
                "struct(jbyte,padding(7),void*,sequence(10, union(jlong,jdouble)))", ForeignMetadata.spell(layout));
    }

    @Test
    @DisplayName("writes one entry per descriptor, void for no return")
    void entries() {
        assertAll(
                () -> assertEquals(
                        "{\"returnType\": \"void*\", \"parameterTypes\": [\"void*\", \"jint\", \"jint\"]}",
                        ForeignMetadata.entry(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT))),
                () -> assertEquals(
                        "{\"returnType\": \"void\", \"parameterTypes\": [\"void*\"]}",
                        ForeignMetadata.entry(FunctionDescriptor.ofVoid(ADDRESS))),
                () -> assertEquals(
                        "{\"returnType\": \"jint\", \"parameterTypes\": []}",
                        ForeignMetadata.entry(FunctionDescriptor.of(JAVA_INT))));
    }

    @Test
    @DisplayName("renders the file as one foreign object with its two lists")
    void renders() {
        var text = ForeignMetadata.render(
                List.of(FunctionDescriptor.of(JAVA_INT), FunctionDescriptor.ofVoid(ADDRESS)),
                List.of(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS)));
        assertEquals("""
                {
                  "foreign": {
                    "downcalls": [
                      {"returnType": "jint", "parameterTypes": []},
                      {"returnType": "void", "parameterTypes": ["void*"]}
                    ],
                    "upcalls": [
                      {"returnType": "void", "parameterTypes": ["void*", "void*"]}
                    ]
                  }
                }
                """, text);
    }

    /// The agent's own record of a run is the reference: every shape it saw has
    /// to be among what is generated, or the generator is spelling something
    /// differently from the one reader that matters.
    @Test
    @DisplayName("covers every descriptor the showcase's trace ever recorded")
    void coversTheTrace() throws IOException {
        var trace = repositoryRoot()
                .resolve("example/src/main/resources/META-INF/native-image/io.github.digitalsmile/goldberry-example")
                .resolve("reachability-metadata.json");
        assertTrue(Files.isRegularFile(trace), "the showcase's trace is expected at " + trace);

        var generated = new LinkedHashSet<String>();
        for (var descriptor : ForeignSurface.downcalls()) {
            generated.add(shape(ForeignMetadata.entry(descriptor)));
        }
        for (var descriptor : ForeignSurface.upcalls()) {
            generated.add(shape(ForeignMetadata.entry(descriptor)));
        }

        var traced = new LinkedHashSet<String>();
        var matcher = TRACED_ENTRY.matcher(Files.readString(trace));
        while (matcher.find()) {
            traced.add(matcher.group(1) + "(" + matcher.group(2).replaceAll("\\s+", "") + ")");
        }
        assertTrue(traced.size() > 40, "the trace should hold dozens of descriptors, found " + traced.size());

        var missing = new LinkedHashSet<>(traced);
        missing.removeAll(generated);
        assertEquals(Set.of(), missing, "traced by the agent and not generated here");
    }

    private static String shape(String entry) {
        var matcher = TRACED_ENTRY.matcher(entry);
        assertTrue(matcher.find(), entry);
        return matcher.group(1) + "(" + matcher.group(2).replaceAll("\\s+", "") + ")";
    }

    /// `natives/build/classes/java/main` is five levels below the root.
    private static Path repositoryRoot() {
        var main = CompiledClasses.mainClassesBeside(ForeignMetadataTest.class);
        return main.getParent().getParent().getParent().getParent().getParent();
    }
}
