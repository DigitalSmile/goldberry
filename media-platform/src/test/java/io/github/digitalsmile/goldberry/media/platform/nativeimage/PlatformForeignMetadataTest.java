package io.github.digitalsmile.goldberry.media.platform.nativeimage;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.digitalsmile.goldberry.media.platform.linux.LinuxBindings;
import io.github.digitalsmile.goldberry.media.platform.macos.MacBindings;
import io.github.digitalsmile.goldberry.media.platform.windows.WindowsBindings;

/// The metadata an image of this module is built from, for all three systems.
/// None of it needs any of them: linking a descriptor opens no library.
@DisplayName("PlatformForeignMetadata")
class PlatformForeignMetadataTest {

    @Test
    @DisplayName("spells a descriptor as the tracing agent does")
    void spelling() {
        assertEquals(
                "{\"returnType\": \"jint\", \"parameterTypes\": [\"void*\", \"void*\", \"void*\", \"void*\", \"void*\"]}",
                PlatformForeignMetadata.entry(
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)));
        assertEquals(
                "{\"returnType\": \"void\", \"parameterTypes\": [\"jlong\"]}",
                PlatformForeignMetadata.entry(FunctionDescriptor.ofVoid(JAVA_LONG)));
        // Core Media's CMTime, the struct VideoToolbox's callback takes by value.
        assertEquals(
                "struct(jlong,jint,jint,jlong)",
                PlatformForeignMetadata.spell(MemoryLayout.structLayout(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_LONG)));
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
                        .map(PlatformForeignMetadata::spell)
                        .toList());
        assertEquals(
                "union(jint,sequence(2, jshort))",
                PlatformForeignMetadata.spell(
                        MemoryLayout.unionLayout(JAVA_INT, MemoryLayout.sequenceLayout(2, JAVA_SHORT))));
        assertEquals(
                "struct(jint,padding(4),void*)",
                PlatformForeignMetadata.spell(
                        MemoryLayout.structLayout(JAVA_INT, MemoryLayout.paddingLayout(4), ADDRESS)));
    }

    @Test
    @DisplayName("records every system's downcalls, each shape once")
    void everySystem() {
        var downcalls = PlatformForeignMetadata.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        for (var system : List.of(MacBindings.downcalls(), LinuxBindings.downcalls(), WindowsBindings.downcalls())) {
            assertFalse(system.isEmpty());
            assertTrue(downcalls.containsAll(system));
        }
    }

    @Test
    @DisplayName("records every system's callbacks as upcalls")
    void everyCallback() {
        var expected = new HashSet<FunctionDescriptor>();
        expected.addAll(MacBindings.UPCALLS);
        expected.addAll(LinuxBindings.UPCALLS);
        expected.addAll(WindowsBindings.UPCALLS);
        assertEquals(expected, new HashSet<>(PlatformForeignMetadata.UPCALLS));
    }

    @Test
    @DisplayName("writes the file, with every downcall and upcall and no resources")
    void writes(@TempDir Path directory) throws IOException {
        var target = directory.resolve("META-INF/native-image/x/reachability-metadata.json");
        PlatformForeignMetadata.main(new String[] {target.toString()});

        var json = Files.readString(target);
        assertTrue(json.startsWith("{\n  \"foreign\": {\n"), json);
        for (var upcall : PlatformForeignMetadata.UPCALLS) {
            assertTrue(json.contains(PlatformForeignMetadata.entry(upcall)), upcall::toString);
        }
        assertFalse(json.contains("resources"), json);
        assertEquals(
                PlatformForeignMetadata.downcalls().size() + PlatformForeignMetadata.UPCALLS.size(),
                json.lines().filter(line -> line.contains("\"returnType\"")).count());
        assertTrue(json.endsWith("}\n"));
    }

    @Test
    @DisplayName("refuses to run without exactly one path")
    void usage() {
        assertThrows(IllegalArgumentException.class, () -> PlatformForeignMetadata.main(new String[0]));
    }
}
