package io.github.digitalsmile.goldberry.media.platform.windows;

import java.lang.foreign.FunctionDescriptor;
import java.util.List;

/// This package's foreign-call surface, for the native-image metadata the
/// module's generator writes (ADR-0339).
///
/// Public so that the generator, in its own package, can reach it; the
/// package is not exported, so nothing outside the module can. Initialising the
/// binding classes links their descriptors and opens no library, so this works
/// on any operating system.
public final class WindowsBindings {

    /// The classes whose `FD_…` constants are the downcall surface. A test checks
    /// that every class in this package holding one is listed.
    public static final List<Class<?>> BINDINGS = List.of(
            Com.class,
            MfPlat.class,
            Ole32.class,
            MfAttributes.class,
            MfSample.class,
            MfBuffer.class,
            MfTransform.class);

    /// Every upcall shape: none. The MFTs are synchronous and call nothing back.
    public static final List<FunctionDescriptor> UPCALLS = List.of();

    private WindowsBindings() {}

    /// Every downcall descriptor, after initialising every binding class.
    public static List<FunctionDescriptor> downcalls() {
        for (var binding : BINDINGS) {
            try {
                Class.forName(binding.getName(), true, binding.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return WindowsLibrary.linked();
    }
}
