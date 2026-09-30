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

/// FFmpeg's half of the metadata an image of this module is built from. None of
/// it needs FFmpeg: linking a descriptor needs no library.
@DisplayName("FfmpegDescriptors")
class FfmpegDescriptorsTest {

    @Test
    @DisplayName("records a descriptor for every holder of every …Calls record, each shape once")
    void everyHolder() {
        var downcalls = FfmpegDescriptors.downcalls();
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
        for (var calls : FfmpegDescriptors.CALLS) {
            assertTrue(calls.isRecord(), calls.getName());
            Arrays.stream(calls.getRecordComponents())
                    .forEach(component -> assertTrue(
                            Modifier.isFinal(component.getType().getModifiers())
                                    && component.getType().getEnclosingClass() == calls,
                            component.getType().getName()));
        }
    }

    @Test
    @DisplayName("lists both AVIOContext callbacks and get_format as upcalls")
    void upcalls() {
        assertTrue(FfmpegDescriptors.UPCALLS.contains(AvioBridge.READ_PACKET));
        assertTrue(FfmpegDescriptors.UPCALLS.contains(AvioBridge.SEEK));
        assertTrue(FfmpegDescriptors.UPCALLS.contains(HardwareDecoder.GET_FORMAT));
    }
}
