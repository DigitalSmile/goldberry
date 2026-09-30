package io.github.digitalsmile.goldberry.media.platform.linux;

import java.lang.foreign.FunctionDescriptor;
import java.util.List;

/// This package's foreign-call surface, for the native-image metadata
/// `MediaForeignMetadata` writes (ADR-0339).
///
/// Public so that the generator, in its own package, can reach it; the package
/// is not exported, so nothing outside the module can. Initialising the binding
/// classes links their descriptors and opens no library, so this works on any
/// operating system.
public final class LinuxBindings {

    /// The classes whose `FD_…` constants are the downcall surface. A test checks
    /// that every class in this package holding one is listed.
    public static final List<Class<?>> BINDINGS = List.of(GLib.class, Gst.class, GstApp.class, GstVideo.class);

    /// Every upcall shape: none. The pipelines are driven from the decode thread,
    /// and GStreamer calls nothing back.
    public static final List<FunctionDescriptor> UPCALLS = List.of();

    private LinuxBindings() {}

    /// Every downcall descriptor, after initialising every binding class.
    public static List<FunctionDescriptor> downcalls() {
        for (var binding : BINDINGS) {
            try {
                Class.forName(binding.getName(), true, binding.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return GstLibrary.linked();
    }
}
