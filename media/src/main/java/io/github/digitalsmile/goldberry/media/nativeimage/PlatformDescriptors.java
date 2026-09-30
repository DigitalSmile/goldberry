package io.github.digitalsmile.goldberry.media.nativeimage;

import java.lang.foreign.FunctionDescriptor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import io.github.digitalsmile.goldberry.media.platform.linux.LinuxBindings;
import io.github.digitalsmile.goldberry.media.platform.macos.MacBindings;
import io.github.digitalsmile.goldberry.media.platform.windows.WindowsBindings;

/// Every foreign-call shape of the system decoders, on all three systems at
/// once: VideoToolbox and AudioToolbox, GStreamer, and Media Foundation
/// (ADR-0339, ADR-0472, ADR-0489).
///
/// An image is built for one system, but the metadata is the same file for all
/// of them, and a shape an image never calls costs it nothing. Each system's
/// package lists its bindings ([MacBindings], [LinuxBindings], [WindowsBindings]),
/// which initialise every binding class so that each `FD_…` constant has been
/// linked and recorded, and name the callbacks the decoders hand the system. A
/// traced run would record what that run happened to call, on the one system it
/// ran on.
///
/// Linking a descriptor needs no library, so this runs on any operating system
/// and opens nothing.
public final class PlatformDescriptors {

    /// Every upcall shape, of every system.
    public static final List<FunctionDescriptor> UPCALLS = Stream.of(
                    MacBindings.UPCALLS, LinuxBindings.UPCALLS, WindowsBindings.UPCALLS)
            .flatMap(List::stream)
            .distinct()
            .toList();

    private PlatformDescriptors() {}

    /// Every distinct downcall descriptor of every system, in the order each
    /// system's bindings first linked them. Two systems share shapes, and each is
    /// listed once.
    public static List<FunctionDescriptor> downcalls() {
        var all = new LinkedHashSet<FunctionDescriptor>();
        all.addAll(MacBindings.downcalls());
        all.addAll(LinuxBindings.downcalls());
        all.addAll(WindowsBindings.downcalls());
        return List.copyOf(all);
    }
}
