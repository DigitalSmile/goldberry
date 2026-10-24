package dev.goldberry.media.ffm;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.FunctionDescriptor;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MediaDescriptors")
class MediaDescriptorsTest {

    @Test
    @DisplayName("records a shape for every holder, each once, without the library")
    void everyHolder() {
        var downcalls = MediaDescriptors.downcalls();
        var holders = MediaDescriptors.CALLS.stream()
                .mapToInt(calls -> calls.getRecordComponents().length)
                .sum();
        // Several holders share a shape (every `(void*) -> void`, say), so the
        // distinct descriptors are fewer than the holders and more than a few.
        assertTrue(downcalls.size() >= 20 && downcalls.size() <= holders, downcalls.size() + " shapes for " + holders);
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        // opus_decoder_ctl and opus_multistream_decoder_ctl are variadic, and are
        // recorded so: an image refuses a variadic call registered as a plain one.
        var variadic = downcalls.stream()
                .filter(shape -> shape.firstVariadic().isPresent())
                .toList();
        assertEquals(
                List.of(
                        DowncallShape.variadic(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), 2),
                        DowncallShape.variadic(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT), 2)),
                variadic);
        assertEquals(
                MediaDescriptors.UPCALLS.size(),
                new HashSet<>(MediaDescriptors.UPCALLS).size(),
                MediaDescriptors.UPCALLS.toString());
    }
}
