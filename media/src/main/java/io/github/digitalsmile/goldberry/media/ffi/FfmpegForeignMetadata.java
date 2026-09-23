package io.github.digitalsmile.goldberry.media.ffi;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import io.github.digitalsmile.goldberry.media.ffi.calls.AvCodecCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.AvFormatCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.AvUtilCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.SwResampleCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.SwScaleCalls;

/// Writes this module's `reachability-metadata.json`: every foreign-call shape a
/// native image of it needs, and the natives jar's resources.
///
/// `:natives`' `ForeignMetadata` for FFmpeg (ADR-0339). It writes the same
/// spelling, the tracing agent's (`jint`, `void*`), and does the same thing
/// before asking: it initialises every holder, so each `FD_…` constant has been
/// through [FfmpegDowncalls#link] and is recorded. A traced run would record only
/// what that run happened to call. This records every holder there is.
///
/// The spelling is repeated rather than shared, because `ForeignMetadata` is in
/// `:natives`' unexported packages, and a metadata grammar is not a reason to
/// widen that seal (ADR-0280). The resources entry is the half `:natives` does not
/// need: the libraries inside `goldberry-ffmpeg-natives` are read with
/// `getResourceAsStream`, and an image includes only resources it is told about.
public final class FfmpegForeignMetadata {

    /// The `…Calls` records whose nested holders are the downcall surface.
    static final List<Class<?>> CALLS = List.of(
            AvUtilCalls.class, AvFormatCalls.class, AvCodecCalls.class, SwResampleCalls.class, SwScaleCalls.class);

    /// Every upcall shape: the two `AVIOContext` callbacks. `get_format` joins in
    /// phase 5.
    static final List<FunctionDescriptor> UPCALLS = List.of(AvioBridge.READ_PACKET, AvioBridge.SEEK);

    private FfmpegForeignMetadata() {}

    /// Writes the file named by the one argument.
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: FfmpegForeignMetadata <reachability-metadata.json>");
        }
        var target = Path.of(args[0]).toAbsolutePath();
        var directory = target.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Files.writeString(target, render(downcalls(), UPCALLS));
    }

    /// Every downcall descriptor, after initialising every holder.
    static List<FunctionDescriptor> downcalls() {
        for (var calls : CALLS) {
            for (var component : calls.getRecordComponents()) {
                try {
                    Class.forName(component.getType().getName(), true, calls.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return FfmpegDowncalls.linked();
    }

    /// The whole file.
    static String render(List<FunctionDescriptor> downcalls, List<FunctionDescriptor> upcalls) {
        return "{\n  \"resources\": [\n"
                + "    {\"glob\": \"io/github/digitalsmile/goldberry/media/natives/**\"}\n"
                + "  ],\n  \"foreign\": {\n    \"downcalls\": [\n"
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
                + descriptor.returnLayout().map(FfmpegForeignMetadata::spell).orElse("void")
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
        return layouts.stream().map(FfmpegForeignMetadata::spell).collect(Collectors.joining(","));
    }

    private static String valueName(ValueLayout value) {
        var carrier = value.carrier();
        if (carrier == MemorySegment.class) {
            return "void*";
        }
        if (carrier == int.class) {
            return "jint";
        }
        if (carrier == long.class) {
            return "jlong";
        }
        if (carrier == float.class) {
            return "jfloat";
        }
        if (carrier == double.class) {
            return "jdouble";
        }
        if (carrier == byte.class) {
            return "jbyte";
        }
        if (carrier == short.class) {
            return "jshort";
        }
        throw new IllegalArgumentException("no metadata name for a value layout carried as " + carrier.getName());
    }
}
