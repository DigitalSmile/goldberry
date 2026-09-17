package io.github.digitalsmile.goldberry.natives.metadata;

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

/// Writes the `foreign` section of a `reachability-metadata.json` from
/// [ForeignSurface], in the spelling GraalVM's own tracing agent uses
/// (ADR-0339).
///
/// Run by `:natives:foreignMetadata` before the jar is built, and the file
/// travels inside the jar under `META-INF/native-image/`, where `native-image`
/// finds it for any application on whose module path this module sits. No trace
/// is needed for a foreign call any more: a run records the screens it reached,
/// and this records every holder and every upcall owner there is, whether or
/// not the showcase's 120 headless frames ever opened the screen that uses them.
///
/// The type names are the agent's — `jint` and not `int`, `void*` for every
/// pointer, `struct(jfloat,jfloat)` for a struct by value — so what this writes
/// and what the agent wrote can be compared line for line, and a test does.
public final class ForeignMetadata {

    private ForeignMetadata() {}

    /// Writes the file named by the one argument.
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: ForeignMetadata <reachability-metadata.json>");
        }
        var downcalls = ForeignSurface.downcalls();
        var upcalls = ForeignSurface.upcalls();
        var target = Path.of(args[0]);
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, render(downcalls, upcalls));
        System.out.println("foreign metadata: " + downcalls.size() + " downcall and " + upcalls.size()
                + " upcall descriptors -> " + target);
    }

    /// The whole file: one `foreign` object with its two arrays.
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

    /// One descriptor, as the agent writes it.
    static String entry(FunctionDescriptor descriptor) {
        var parameters = descriptor.argumentLayouts().stream()
                .map(layout -> "\"" + spell(layout) + "\"")
                .collect(Collectors.joining(", "));
        return "{\"returnType\": \""
                + descriptor.returnLayout().map(ForeignMetadata::spell).orElse("void") + "\", \"parameterTypes\": ["
                + parameters + "]}";
    }

    /// A layout in the metadata's grammar: the JNI names for values, `void*`
    /// for an address, and `struct(...)`, `union(...)`, `sequence(n, ...)` and
    /// `padding(n)` composed as the layouts are.
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
        return layouts.stream().map(ForeignMetadata::spell).collect(Collectors.joining(","));
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
        if (carrier == boolean.class) {
            return "jboolean";
        }
        if (carrier == byte.class) {
            return "jbyte";
        }
        if (carrier == short.class) {
            return "jshort";
        }
        if (carrier == char.class) {
            return "jchar";
        }
        throw new IllegalArgumentException("no metadata name for a value layout carried as " + carrier.getName());
    }
}
