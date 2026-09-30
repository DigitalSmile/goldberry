package io.github.digitalsmile.goldberry.media.platform.macos;

import java.lang.foreign.FunctionDescriptor;
import java.util.List;

/// This package's foreign-call surface, for the native-image metadata
/// `MediaForeignMetadata` writes (ADR-0339).
///
/// Public so that the generator, in its own package, can reach it; the package
/// is not exported, so nothing outside the module can. Initialising the binding
/// classes links their descriptors and opens no framework, so this works on any
/// operating system.
public final class MacBindings {

    /// The classes whose `FD_…` constants are the downcall surface. A test checks
    /// that every class in this package holding one is listed.
    public static final List<Class<?>> BINDINGS = List.of(
            CoreFoundation.class,
            CoreMedia.class,
            CoreVideo.class,
            VideoToolbox.class,
            AudioToolbox.class,
            CoreAudio.class);

    /// Every upcall shape: VideoToolbox's output callback and AudioToolbox's
    /// input procedure. A test checks that these are the descriptors the
    /// decoders' `upcallStub`s are made with.
    public static final List<FunctionDescriptor> UPCALLS =
            List.of(VideoToolbox.OUTPUT_CALLBACK, AudioToolbox.INPUT_PROC);

    private MacBindings() {}

    /// Every downcall descriptor, after initialising every binding class.
    public static List<FunctionDescriptor> downcalls() {
        for (var binding : BINDINGS) {
            try {
                Class.forName(binding.getName(), true, binding.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return Framework.linked();
    }
}
