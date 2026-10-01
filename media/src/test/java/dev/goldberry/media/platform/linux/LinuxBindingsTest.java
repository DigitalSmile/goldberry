package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.FunctionDescriptor;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.platform.fixtures.BindingSurface;

/// The GStreamer package's foreign-call surface, as the metadata generator
/// reads it. None of it needs GStreamer: linking a descriptor opens no library.
@DisplayName("LinuxBindings")
class LinuxBindingsTest {

    @Test
    @DisplayName("records a descriptor for every binding, each shape once")
    void everyBinding() {
        var downcalls = LinuxBindings.downcalls();
        assertEquals(downcalls.size(), new HashSet<>(downcalls).size());
        // Spot checks of shapes only one binding has.
        assertTrue(downcalls.contains(
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS))); // gst_buffer_new_allocate
        assertTrue(
                downcalls.contains(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG))); // gst_app_sink_try_pull_sample
        assertTrue(downcalls.contains(
                FunctionDescriptor.of(ADDRESS, JAVA_LONG, JAVA_INT))); // gst_element_factory_list_get_elements
        assertTrue(downcalls.contains(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT))); // gst_video_info_set_format
    }

    @Test
    @DisplayName("lists every class in the package that links a downcall")
    void bindingsAreComplete() {
        assertEquals(new HashSet<>(LinuxBindings.BINDINGS), new HashSet<>(BindingSurface.linking(LinuxBindings.class)));
    }

    @Test
    @DisplayName("declares no callback: GStreamer is driven from the decode thread")
    void noUpcalls() {
        assertTrue(LinuxBindings.UPCALLS.isEmpty());
        assertTrue(BindingSurface.callbacks(LinuxBindings.class).isEmpty());
    }
}
