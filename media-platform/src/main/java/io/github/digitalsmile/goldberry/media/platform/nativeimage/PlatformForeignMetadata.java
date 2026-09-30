package io.github.digitalsmile.goldberry.media.platform.nativeimage;

import java.io.IOException;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.digitalsmile.goldberry.media.platform.linux.LinuxBindings;
import io.github.digitalsmile.goldberry.media.platform.macos.MacBindings;
import io.github.digitalsmile.goldberry.media.platform.windows.WindowsBindings;

/// Writes this module's `reachability-metadata.json`: every foreign-call shape a
/// native image of it needs.
///
/// `:media`'s `FfmpegForeignMetadata` for the system libraries (ADR-0339), on
/// all three systems at once: VideoToolbox and AudioToolbox, GStreamer, and
/// Media Foundation. An image is built for one system, but the metadata is the
/// same file for all of them, and a shape an image never calls costs it
/// nothing. Each system's package lists its bindings ([MacBindings],
/// [LinuxBindings], [WindowsBindings]), which initialise every binding class so
/// that each `FD_…` constant has been linked and recorded, and name the
/// callbacks the decoders hand the system. A traced run would record what that
/// run happened to call, on the one system it ran on.
///
/// Linking a descriptor needs no library, so this runs on any operating system
/// and opens nothing. There is no `resources` section: the module ships no
/// native code, and its service files are found by the image's own
/// `ServiceLoader` support.
///
/// The grammar is repeated from `FfmpegForeignMetadata` rather than shared: that
/// class is in `:media`'s unexported `…media.ffi`, and a metadata spelling is not
/// a reason to widen that seal (ADR-0280).
///
/// The class is package-private and so is its `main`: JDK 25's launcher (JEP 512)
/// runs it as it is, and `:media-platform:foreignMetadata` is its only caller.
final class PlatformForeignMetadata {

    /// Every upcall shape, of every system.
    static final List<FunctionDescriptor> UPCALLS = Stream.of(
                    MacBindings.UPCALLS, LinuxBindings.UPCALLS, WindowsBindings.UPCALLS)
            .flatMap(List::stream)
            .distinct()
            .toList();

    private PlatformForeignMetadata() {}

    /// Writes the file named by the one argument.
    static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: PlatformForeignMetadata <reachability-metadata.json>");
        }
        var target = Path.of(args[0]).toAbsolutePath();
        var directory = target.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Files.writeString(target, render(downcalls(), UPCALLS));
    }

    /// Every distinct downcall descriptor of every system, in the order each
    /// system's bindings first linked them. Two systems share shapes, and each is
    /// written once.
    static List<FunctionDescriptor> downcalls() {
        var all = new LinkedHashSet<FunctionDescriptor>();
        all.addAll(MacBindings.downcalls());
        all.addAll(LinuxBindings.downcalls());
        all.addAll(WindowsBindings.downcalls());
        return List.copyOf(all);
    }

    /// The whole file.
    static String render(List<FunctionDescriptor> downcalls, List<FunctionDescriptor> upcalls) {
        return "{\n  \"foreign\": {\n    \"downcalls\": [\n"
                + entries(downcalls)
                + "    ],\n    \"upcalls\": [\n"
                + entries(upcalls)
                + "    ]\n  }\n}\n";
    }

    private static String entries(List<FunctionDescriptor> descriptors) {
        return descriptors.stream().map(d -> "      " + entry(d)).collect(Collectors.joining(",\n", "", "\n"));
    }

    /// One descriptor, as the tracing agent writes it.
    static String entry(FunctionDescriptor descriptor) {
        var parameters = descriptor.argumentLayouts().stream()
                .map(layout -> "\"" + spell(layout) + "\"")
                .collect(Collectors.joining(", "));
        return "{\"returnType\": \""
                + descriptor.returnLayout().map(PlatformForeignMetadata::spell).orElse("void")
                + "\", \"parameterTypes\": [" + parameters + "]}";
    }

    /// A layout in the metadata's grammar.
    static String spell(MemoryLayout layout) {
        return switch (layout) {
            case ValueLayout value -> valueName(value);
            case StructLayout struct -> "struct(" + members(struct.memberLayouts()) + ")";
            case UnionLayout union -> "union(" + members(union.memberLayouts()) + ")";
            case SequenceLayout sequence ->
                "sequence(" + sequence.elementCount() + ", " + spell(sequence.elementLayout()) + ")";
            case PaddingLayout padding -> "padding(" + padding.byteSize() + ")";
        };
    }

    private static String members(List<MemoryLayout> layouts) {
        return layouts.stream().map(PlatformForeignMetadata::spell).collect(Collectors.joining(","));
    }

    /// A value layout's name, over the sealed hierarchy, so a carrier the grammar
    /// has no name for is a case here rather than a fall-through.
    private static String valueName(ValueLayout value) {
        return switch (value) {
            case AddressLayout _ -> "void*";
            case ValueLayout.OfInt _ -> "jint";
            case ValueLayout.OfLong _ -> "jlong";
            case ValueLayout.OfFloat _ -> "jfloat";
            case ValueLayout.OfDouble _ -> "jdouble";
            case ValueLayout.OfByte _ -> "jbyte";
            case ValueLayout.OfShort _ -> "jshort";
            case ValueLayout.OfChar _ -> "jchar";
            case ValueLayout.OfBoolean _ -> "jboolean";
        };
    }
}
