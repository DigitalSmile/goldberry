package io.github.digitalsmile.goldberry.natives.layout;

import io.github.digitalsmile.goldberry.natives.NativePlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Compares the hand-written layouts in [Layouts] against what the compiled
/// library reports.
///
/// This is the check that replaces what jextract would have guaranteed
/// (ADR-0010), and it is a stronger one: it compares the Java declaration
/// against the actual library for the actual target, rather than comparing
/// generated code across platforms.
///
/// Mismatches are collected rather than thrown one at a time — one wrong padding
/// declaration shifts every field after it, and seeing all of them at once is the
/// difference between a five-minute fix and an afternoon.
public final class LayoutVerifier {

    private LayoutVerifier() {
    }

    /// Verifies every registered layout, plus the primitive widths Goldberry
    /// depends on.
    ///
    /// @return one human-readable message per disagreement; empty means agreement
    public static List<String> verify(
            NativePlatform platform, List<NativeStructLayout> registry, List<LayoutEntry> entries) {
        return verify(platform, registry, List.of(), entries);
    }

    /// Verifies every registered layout and constant, plus the primitive widths.
    ///
    /// @return one human-readable message per disagreement; empty means agreement
    public static List<String> verify(
            NativePlatform platform,
            List<NativeStructLayout> registry,
            List<NativeConstant> constants,
            List<LayoutEntry> entries) {

        var mismatches = new ArrayList<String>();

        var describesLayout = (java.util.function.Predicate<LayoutEntry>)
                entry -> !entry.describesScalar() && !entry.describesConstant();

        var structRows = entries.stream()
                .filter(describesLayout)
                .filter(LayoutEntry::describesStruct)
                .collect(Collectors.toMap(LayoutEntry::structName, entry -> entry, (a, b) -> a));

        var fieldRows = entries.stream()
                .filter(describesLayout)
                .filter(entry -> !entry.describesStruct())
                .collect(Collectors.toMap(
                        entry -> entry.structName() + "." + entry.fieldName(), entry -> entry, (a, b) -> a));

        for (var struct : registry) {
            var reported = structRows.get(struct.name());
            if (reported == null) {
                mismatches.add(struct.name()
                        + " is declared in Layouts but not registered in goldberry_shim.c,"
                        + " so nothing verifies it");
                continue;
            }
            if (struct.byteSize() != reported.size()) {
                mismatches.add(struct.name() + ": Java sizeof=" + struct.byteSize()
                        + ", C sizeof=" + reported.size());
            }
            if (struct.byteAlignment() != reported.alignment()) {
                mismatches.add(struct.name() + ": Java alignment=" + struct.byteAlignment()
                        + ", C alignment=" + reported.alignment());
            }

            for (var field : struct.fieldNames()) {
                var reportedField = fieldRows.get(struct.name() + "." + field);
                if (reportedField == null) {
                    mismatches.add(struct.name() + "." + field
                            + " is declared in Java but not registered in goldberry_shim.c");
                    continue;
                }
                if (struct.offsetOf(field) != reportedField.offset()) {
                    mismatches.add(struct.name() + "." + field
                            + ": Java offset=" + struct.offsetOf(field)
                            + ", C offset=" + reportedField.offset());
                }
                if (struct.sizeOf(field) != reportedField.size()) {
                    mismatches.add(struct.name() + "." + field
                            + ": Java sizeof=" + struct.sizeOf(field)
                            + ", C sizeof=" + reportedField.size());
                }
            }
        }

        mismatches.addAll(verifyConstants(constants, entries));
        mismatches.addAll(verifyScalars(platform, entries));
        return List.copyOf(mismatches);
    }

    /// Checks the C constants the Java side hard-codes.
    ///
    /// The value travels in the row's `size` field, which is unsigned on the C
    /// side, so it is widened here rather than compared as a signed `int`.
    private static List<String> verifyConstants(
            List<NativeConstant> constants, List<LayoutEntry> entries) {

        var reported = entries.stream()
                .filter(LayoutEntry::describesConstant)
                .collect(Collectors.toMap(
                        LayoutEntry::fieldName, LayoutEntry::value, (a, b) -> a));

        var mismatches = new ArrayList<String>();
        for (var constant : constants) {
            var value = reported.get(constant.name());
            if (value == null) {
                mismatches.add(constant.name()
                        + " is declared in Java but not registered in goldberry_shim.c,"
                        + " so nothing verifies it");
            } else if (value != constant.value()) {
                mismatches.add(constant.name() + ": Java says " + constant.value()
                        + ", C says " + value);
            }
        }
        return mismatches;
    }

    /// Checks the primitive widths Goldberry's bindings assume.
    ///
    /// `long` is the one that matters: 4 bytes on Win64, 8 on Linux and macOS.
    /// It is the most common way a hand-written binding is wrong on exactly one
    /// platform, which is the hardest kind of wrong to notice.
    private static List<String> verifyScalars(NativePlatform platform, List<LayoutEntry> entries) {
        var scalars = entries.stream()
                .filter(LayoutEntry::describesScalar)
                .collect(Collectors.toMap(LayoutEntry::fieldName, LayoutEntry::size, (a, b) -> a));

        var expected = Map.of(
                "char", 1,
                "short", 2,
                "int", 4,
                "long", platform.cLongSize(),
                "long long", 8,
                "float", 4,
                "double", 8,
                "pointer", 8,
                "size_t", 8);

        var mismatches = new ArrayList<String>();
        expected.forEach((name, expectedSize) -> {
            var reported = scalars.get(name);
            if (reported == null) {
                mismatches.add("libgoldberry does not report the width of C `" + name + "`");
            } else if (!reported.equals(expectedSize)) {
                mismatches.add("C `" + name + "` is " + reported + " bytes on "
                        + platform.classifier() + ", but Goldberry assumes " + expectedSize);
            }
        });
        return mismatches;
    }
}
