package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.FunctionDescriptor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The metadata an image of this module is built from. None of it needs FFmpeg:
/// linking a descriptor needs no library.
@DisplayName("FfmpegForeignMetadata")
class FfmpegForeignMetadataTest {

    @Test
    @DisplayName("spells a descriptor as the tracing agent does")
    void spelling() {
        assertEquals(
                "{\"returnType\": \"jint\", \"parameterTypes\": [\"void*\", \"void*\", \"jint\"]}",
                FfmpegForeignMetadata.entry(AvioBridge.READ_PACKET));
        assertEquals(
                "{\"returnType\": \"void\", \"parameterTypes\": [\"jlong\"]}",
                FfmpegForeignMetadata.entry(FunctionDescriptor.ofVoid(JAVA_LONG)));
        assertEquals("struct(jint,jint)", FfmpegForeignMetadata.spell(FfmpegStructs.AV_RATIONAL));
    }

    @Test
    @DisplayName("records a descriptor for every holder of every …Calls record, each shape once")
    void everyHolder() {
        var downcalls = FfmpegForeignMetadata.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        // Spot checks, one per library, of shapes only one holder has.
        assertTrue(downcalls.contains(FunctionDescriptor.of(
                ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS))); // avio_alloc_context
        assertTrue(downcalls.contains(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG))); // av_strerror
        assertTrue(downcalls.contains(FunctionDescriptor.of(ADDRESS, JAVA_INT))); // avcodec_get_name
    }

    @Test
    @DisplayName("every component of a …Calls record is a holder the generator initialises")
    void holdersAreNestedClasses() {
        for (var calls : FfmpegForeignMetadata.CALLS) {
            assertTrue(calls.isRecord(), calls.getName());
            Arrays.stream(calls.getRecordComponents())
                    .forEach(component -> assertTrue(
                            Modifier.isFinal(component.getType().getModifiers())
                                    && component.getType().getEnclosingClass() == calls,
                            component.getType().getName()));
        }
    }

    @Test
    @DisplayName("lists both AVIOContext callbacks as upcalls, and the natives jar's resources")
    void upcallsAndResources() {
        var json = FfmpegForeignMetadata.render(FfmpegForeignMetadata.downcalls(), FfmpegForeignMetadata.UPCALLS);
        assertTrue(json.contains("\"glob\": \"io/github/digitalsmile/goldberry/media/natives/**\""));
        assertTrue(json.contains(FfmpegForeignMetadata.entry(AvioBridge.SEEK)));
        assertTrue(json.endsWith("}\n"));
    }
}
