package io.github.digitalsmile.goldberry.media.platform.macos;

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
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The metadata an image of this module is built from. None of it needs a Mac:
/// linking a descriptor opens no framework.
@DisplayName("PlatformForeignMetadata")
class PlatformForeignMetadataTest {

    @Test
    @DisplayName("spells a descriptor as the tracing agent does")
    void spelling() {
        assertEquals(
                "{\"returnType\": \"jint\", \"parameterTypes\": [\"void*\", \"void*\", \"void*\", \"void*\", \"void*\"]}",
                PlatformForeignMetadata.entry(AudioToolbox.INPUT_PROC));
        assertEquals(
                "{\"returnType\": \"void\", \"parameterTypes\": [\"jlong\"]}",
                PlatformForeignMetadata.entry(FunctionDescriptor.ofVoid(JAVA_LONG)));
        assertEquals("struct(jlong,jint,jint,jlong)", PlatformForeignMetadata.spell(CoreMedia.CM_TIME));
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
    @DisplayName("records a descriptor for every binding, each shape once")
    void everyBinding() {
        var downcalls = PlatformForeignMetadata.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        // Spot checks, one per framework, of shapes only one binding has.
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS))); // CFDictionaryCreate
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS))); // VTDecompressionSessionCreate
        assertTrue(downcalls.contains(FunctionDescriptor.of(JAVA_BYTE, JAVA_INT))); // VTIsHardwareDecodeSupported
        assertTrue(downcalls.contains(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG))); // CVPixelBuffer…OfPlane
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS))); // AudioObjectGetPropertyData
        assertTrue(downcalls.contains(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS))); // AudioConverterSetProperty
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT,
                ADDRESS))); // CMVideoFormatDescriptionCreateFromH264ParameterSets
    }

    @Test
    @DisplayName("lists every class in the package that links a downcall")
    void bindingsAreComplete() throws IOException, URISyntaxException {
        var linking = packageClasses()
                .filter(type -> Arrays.stream(type.getDeclaredFields())
                        .anyMatch(PlatformForeignMetadataTest::isDowncallConstant))
                .toList();
        assertEquals(new HashSet<>(PlatformForeignMetadata.BINDINGS), new HashSet<>(linking));
    }

    @Test
    @DisplayName("lists every callback descriptor a binding declares as an upcall")
    void upcallsAreComplete() throws IOException, URISyntaxException {
        var declared = packageClasses()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == FunctionDescriptor.class)
                .map(PlatformForeignMetadataTest::read)
                .toList();
        assertEquals(new HashSet<>(PlatformForeignMetadata.UPCALLS), new HashSet<>(declared));
    }

    @Test
    @DisplayName("writes the file, with both callbacks as upcalls and no resources")
    void writes(@TempDir Path directory) throws IOException {
        var target = directory.resolve("META-INF/native-image/x/reachability-metadata.json");
        PlatformForeignMetadata.main(new String[] {target.toString()});

        var json = Files.readString(target);
        assertTrue(json.startsWith("{\n  \"foreign\": {\n"), json);
        assertTrue(json.contains(PlatformForeignMetadata.entry(VideoToolbox.OUTPUT_CALLBACK)));
        assertTrue(json.contains(PlatformForeignMetadata.entry(AudioToolbox.INPUT_PROC)));
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

    /// Whether `field` is a binding's unbound handle, `private static final FD_…`.
    private static boolean isDowncallConstant(Field field) {
        return Modifier.isStatic(field.getModifiers())
                && field.getType() == MethodHandle.class
                && field.getName().startsWith("FD_");
    }

    private static FunctionDescriptor read(Field field) {
        try {
            field.setAccessible(true);
            return (FunctionDescriptor) Objects.requireNonNull(field.get(null));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /// Every top-level and nested class compiled into this package's main output,
    /// loaded but not initialised. The tests run from the class directories, not a
    /// jar, and the main one is found through the generator's own code source, so
    /// the test classes of the same package are not in it.
    private static Stream<Class<?>> packageClasses() throws IOException, URISyntaxException {
        var packageName = PlatformForeignMetadata.class.getPackageName();
        var root = Path.of(PlatformForeignMetadata.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        var directory = root.resolve(packageName.replace('.', '/'));
        try (var files = Files.list(directory)) {
            return files
                    .map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".class"))
                    .map(name -> packageName + "." + name.substring(0, name.length() - ".class".length()))
                    .map(PlatformForeignMetadataTest::load)
                    .toList()
                    .stream();
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, PlatformForeignMetadataTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
