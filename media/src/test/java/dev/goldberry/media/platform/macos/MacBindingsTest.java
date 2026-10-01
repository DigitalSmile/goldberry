package dev.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.FunctionDescriptor;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.platform.fixtures.BindingSurface;

/// The macOS package's foreign-call surface, as the metadata generator reads
/// it. None of it needs a Mac: linking a descriptor opens no framework.
@DisplayName("MacBindings")
class MacBindingsTest {

    @Test
    @DisplayName("records a descriptor for every binding, each shape once")
    void everyBinding() {
        var downcalls = MacBindings.downcalls();
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
    void bindingsAreComplete() {
        assertEquals(new HashSet<>(MacBindings.BINDINGS), new HashSet<>(BindingSurface.linking(MacBindings.class)));
    }

    @Test
    @DisplayName("lists every callback descriptor a binding declares as an upcall")
    void upcallsAreComplete() {
        assertEquals(new HashSet<>(MacBindings.UPCALLS), new HashSet<>(BindingSurface.callbacks(MacBindings.class)));
    }
}
