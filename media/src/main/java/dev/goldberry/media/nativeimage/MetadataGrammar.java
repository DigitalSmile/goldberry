package dev.goldberry.media.nativeimage;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.stream.Collectors;

/// How `reachability-metadata.json` spells a foreign call: the tracing agent's
/// grammar (`jint`, `void*`, `struct(…)`), which is what a native image reads.
///
/// One copy for the module: FFmpeg's generator and the system decoders' share
/// it. `:natives`' `ForeignMetadata` keeps a copy of its own, because `:natives`
/// exports its internals to `:core` and to nobody else.
public final class MetadataGrammar {

    private MetadataGrammar() {}

    /// The whole file: a `resources` section when `resourceGlobs` is not empty,
    /// then every downcall and every upcall.
    public static String render(
            List<String> resourceGlobs, List<FunctionDescriptor> downcalls, List<FunctionDescriptor> upcalls) {
        var resources = resourceGlobs.isEmpty()
                ? ""
                : resourceGlobs.stream()
                        .map(glob -> "    {\"glob\": \"" + glob + "\"}")
                        .collect(Collectors.joining(",\n", "  \"resources\": [\n", "\n  ],\n"));
        return "{\n" + resources + "  \"foreign\": {\n    \"downcalls\": [\n"
                + entries(downcalls)
                + "    ],\n    \"upcalls\": [\n"
                + entries(upcalls)
                + "    ]\n  }\n}\n";
    }

    private static String entries(List<FunctionDescriptor> descriptors) {
        return descriptors.stream().map(d -> "      " + entry(d)).collect(Collectors.joining(",\n", "", "\n"));
    }

    /// One descriptor, as the tracing agent writes it.
    public static String entry(FunctionDescriptor descriptor) {
        var parameters = descriptor.argumentLayouts().stream()
                .map(layout -> "\"" + spell(layout) + "\"")
                .collect(Collectors.joining(", "));
        return "{\"returnType\": \""
                + descriptor.returnLayout().map(MetadataGrammar::spell).orElse("void")
                + "\", \"parameterTypes\": [" + parameters + "]}";
    }

    /// A layout in the metadata's grammar.
    public static String spell(MemoryLayout layout) {
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
        return layouts.stream().map(MetadataGrammar::spell).collect(Collectors.joining(","));
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
