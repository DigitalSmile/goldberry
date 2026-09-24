package io.github.digitalsmile.goldberry.media.platform.macos;

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
import java.util.List;
import java.util.stream.Collectors;

/// Writes this module's `reachability-metadata.json`: every foreign-call shape a
/// native image of it needs.
///
/// `:media`'s `FfmpegForeignMetadata` for the system frameworks (ADR-0339). It
/// initialises every binding class, so each `FD_…` constant has been through
/// [Framework#link] and is recorded, and it names the two callbacks the
/// decoders hand the operating system. A traced run would record what that run
/// happened to call, and a run on anything but a Mac would call nothing.
///
/// Linking a descriptor needs no library, so this runs on any operating system
/// and opens no framework. There is no `resources` section: the module ships no
/// native code, and its two service files are found by the image's own
/// `ServiceLoader` support.
///
/// The grammar is repeated from `FfmpegForeignMetadata` rather than shared: that
/// class is in `:media`'s unexported `…media.ffi`, and a metadata spelling is not
/// a reason to widen that seal (ADR-0280).
///
/// The class is package-private and so is its `main`: JDK 25's launcher (JEP 512)
/// runs it as it is, and `:media-platform:foreignMetadata` is its only caller.
final class PlatformForeignMetadata {

    /// The classes whose `FD_…` constants are the downcall surface. A test checks
    /// that every class in this package holding one is listed.
    static final List<Class<?>> BINDINGS = List.of(
            CoreFoundation.class,
            CoreMedia.class,
            CoreVideo.class,
            VideoToolbox.class,
            AudioToolbox.class,
            CoreAudio.class);

    /// Every upcall shape: VideoToolbox's output callback and AudioToolbox's
    /// input procedure. A test checks that these are the descriptors the
    /// decoders' `upcallStub`s are made with.
    static final List<FunctionDescriptor> UPCALLS = List.of(VideoToolbox.OUTPUT_CALLBACK, AudioToolbox.INPUT_PROC);

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

    /// Every downcall descriptor, after initialising every binding class.
    static List<FunctionDescriptor> downcalls() {
        for (var binding : BINDINGS) {
            try {
                Class.forName(binding.getName(), true, binding.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return Framework.linked();
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
