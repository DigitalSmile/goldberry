package dev.goldberry.vector.nativeimage;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.vector.thorvg.VectorDescriptors;

/// The file a native image of this module is built from: every shape the
/// ThorVG bindings link, and the natives jar's resources. Written from the
/// holders, so none of it needs the library.
@DisplayName("VectorForeignMetadata")
class VectorForeignMetadataTest {

    @Test
    @DisplayName("holds every holder's shape, each once")
    void everyHolder() {
        var downcalls = VectorForeignMetadata.downcalls();
        var holders = VectorDescriptors.CALLS.stream()
                .mapToLong(calls -> calls.getRecordComponents().length)
                .sum();
        assertAll(
                () -> assertEquals(downcalls.size(), new HashSet<>(downcalls).size(), "no shape twice"),
                // Shapes are shared between holders, never more than there are holders.
                () -> assertTrue(downcalls.size() <= holders, downcalls.size() + " shapes, " + holders + " holders"),
                // tvg_swcanvas_create, tvg_canvas_draw, tvg_picture_set_size and tvg_engine_init.
                () -> assertTrue(downcalls.contains(FunctionDescriptor.of(ADDRESS, JAVA_INT))),
                () -> assertTrue(downcalls.contains(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_BOOLEAN))),
                () -> assertTrue(downcalls.contains(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_FLOAT))),
                () -> assertTrue(downcalls.contains(FunctionDescriptor.of(JAVA_INT, JAVA_INT))));
    }

    @Test
    @DisplayName("names a component of every …Calls record as a holder class that initialises")
    void callsRecords() {
        for (var calls : VectorDescriptors.CALLS) {
            assertTrue(calls.isRecord(), calls::getName);
            assertTrue(
                    Arrays.stream(calls.getRecordComponents())
                            .allMatch(component -> component.getType().getEnclosingClass() == calls),
                    () -> calls.getSimpleName() + "'s components are its own nested holders");
        }
    }

    @Test
    @DisplayName("writes the file, with the natives jar's glob and every shape")
    void writes(@TempDir Path directory) throws IOException {
        var target = directory.resolve("META-INF/native-image/x/reachability-metadata.json");
        VectorForeignMetadata.main(new String[] {target.toString()});

        var json = Files.readString(target);
        assertTrue(json.contains("{\"glob\": \"" + VectorForeignMetadata.NATIVES_GLOB + "\"}"), json);
        for (var downcall : VectorForeignMetadata.downcalls()) {
            assertTrue(json.contains(MetadataGrammar.entry(downcall)), downcall::toString);
        }
        assertEquals(
                VectorForeignMetadata.downcalls().size(),
                json.lines().filter(line -> line.contains("\"returnType\"")).count());
        assertTrue(json.endsWith("}\n"));
    }

    @Test
    @DisplayName("refuses to run without exactly one path")
    void usage() {
        assertThrows(IllegalArgumentException.class, () -> VectorForeignMetadata.main(new String[0]));
    }
}
