package dev.goldberry.vector.nativeimage;

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
/// This module's copy. `:media` and `:natives` keep one each, because neither
/// exports the package it is in, and a dependency on either for forty lines
/// would pull a module this one does not otherwise need.
final class MetadataGrammar {

    private MetadataGrammar() {}

    /// The whole file: the `resources` globs, then every downcall.
    static String render(List<String> resourceGlobs, List<FunctionDescriptor> downcalls) {
        var resources = resourceGlobs.stream()
                .map(glob -> "    {\"glob\": \"" + glob + "\"}")
                .collect(Collectors.joining(",\n", "  \"resources\": [\n", "\n  ],\n"));
        var calls = downcalls.stream()
                .map(descriptor -> "      " + entry(descriptor))
                .collect(Collectors.joining(",\n", "", "\n"));
        return "{\n" + resources + "  \"foreign\": {\n    \"downcalls\": [\n" + calls + "    ]\n  }\n}\n";
    }

    /// One descriptor, as the tracing agent writes it.
    static String entry(FunctionDescriptor descriptor) {
        var parameters = descriptor.argumentLayouts().stream()
                .map(layout -> "\"" + spell(layout) + "\"")
                .collect(Collectors.joining(", "));
        return "{\"returnType\": \""
                + descriptor.returnLayout().map(MetadataGrammar::spell).orElse("void") + "\", \"parameterTypes\": ["
                + parameters + "]}";
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
        return layouts.stream().map(MetadataGrammar::spell).collect(Collectors.joining(","));
    }

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
