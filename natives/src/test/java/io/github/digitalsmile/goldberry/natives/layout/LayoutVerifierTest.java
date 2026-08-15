package io.github.digitalsmile.goldberry.natives.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativePlatform;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests the verifier itself, with synthetic tables.
///
/// The verifier is the whole safety argument for hand-writing bindings
/// (ADR-0010), so "it reports agreement" is not enough — it has to be shown to
/// actually catch each way a layout can be wrong. These run without any native
/// library.
class LayoutVerifierTest {

    private static final NativePlatform LINUX_X64 = NativePlatform.of("Linux", "amd64");
    private static final NativePlatform WINDOWS_X64 = NativePlatform.of("Windows 11", "amd64");

    @Test
    @DisplayName("a table that agrees with the Java layouts produces no mismatches")
    void agreementProducesNoMismatches() {
        var mismatches = LayoutVerifier.verify(LINUX_X64, Layouts.registry(), correctTable(LINUX_X64));

        assertEquals(List.of(), mismatches);
    }

    @Test
    @DisplayName("a wrong field offset is caught")
    void catchesWrongOffset() {
        var table = new ArrayList<>(correctTable(LINUX_X64));
        table.replaceAll(entry ->
                isField(entry, "c") ? withOffset(entry, 12) : entry);

        var mismatches = LayoutVerifier.verify(LINUX_X64, Layouts.registry(), table);

        assertEquals(1, mismatches.size(), () -> "expected exactly one mismatch, got " + mismatches);
        assertTrue(mismatches.getFirst().contains("goldberry_probe_self_t.c"), mismatches::toString);
    }

    @Test
    @DisplayName("a wrong struct size is caught")
    void catchesWrongStructSize() {
        var table = new ArrayList<>(correctTable(LINUX_X64));
        table.replaceAll(entry -> entry.describesStruct() && !entry.describesScalar()
                ? new LayoutEntry(entry.structName(), null, 32, 0, entry.alignment())
                : entry);

        var mismatches = LayoutVerifier.verify(LINUX_X64, Layouts.registry(), table);

        assertTrue(mismatches.stream().anyMatch(m -> m.contains("sizeof")), mismatches::toString);
    }

    @Test
    @DisplayName("a struct declared in Java but not registered in C is caught")
    void catchesUnregisteredStruct() {
        var scalarsOnly = correctTable(LINUX_X64).stream()
                .filter(LayoutEntry::describesScalar)
                .toList();

        var mismatches = LayoutVerifier.verify(LINUX_X64, Layouts.registry(), scalarsOnly);

        assertTrue(
                mismatches.stream().anyMatch(m -> m.contains("goldberry_shim.c")),
                mismatches::toString);
    }

    @Test
    @DisplayName("the Win64 4-byte C long is caught when it shows up on Linux")
    void catchesWrongCLongWidth() {
        // A table reporting Windows' 4-byte long, verified as though it were
        // Linux. This is the §3.1 trap the probe exists for.
        var table = new ArrayList<>(correctTable(WINDOWS_X64));

        var mismatches = LayoutVerifier.verify(LINUX_X64, Layouts.registry(), table);

        assertTrue(
                mismatches.stream().anyMatch(m -> m.contains("`long`")),
                mismatches::toString);
    }

    @Test
    @DisplayName("the same table verifies cleanly against the platform it came from")
    void windowsTableVerifiesOnWindows() {
        var mismatches = LayoutVerifier.verify(WINDOWS_X64, Layouts.registry(), correctTable(WINDOWS_X64));

        assertEquals(List.of(), mismatches);
    }

    @Test
    @DisplayName("a missing scalar row is caught")
    void catchesMissingScalar() {
        var withoutPointer = correctTable(LINUX_X64).stream()
                .filter(entry -> !(entry.describesScalar() && "pointer".equals(entry.fieldName())))
                .toList();

        var mismatches = LayoutVerifier.verify(LINUX_X64, Layouts.registry(), withoutPointer);

        assertTrue(
                mismatches.stream().anyMatch(m -> m.contains("pointer")),
                mismatches::toString);
    }

    /// Builds the table the C side would report on the given platform, derived
    /// from the Java layouts so the "agreement" case is genuinely round-tripped.
    private static List<LayoutEntry> correctTable(NativePlatform platform) {
        var entries = new ArrayList<LayoutEntry>();
        for (var struct : Layouts.registry()) {
            entries.add(new LayoutEntry(
                    struct.name(), null, (int) struct.byteSize(), 0, (int) struct.byteAlignment()));
            for (var field : struct.fieldNames()) {
                entries.add(new LayoutEntry(
                        struct.name(), field, (int) struct.sizeOf(field), (int) struct.offsetOf(field), 0));
            }
        }
        entries.add(scalar("char", 1));
        entries.add(scalar("short", 2));
        entries.add(scalar("int", 4));
        entries.add(scalar("long", platform.cLongSize()));
        entries.add(scalar("long long", 8));
        entries.add(scalar("float", 4));
        entries.add(scalar("double", 8));
        entries.add(scalar("pointer", 8));
        entries.add(scalar("size_t", 8));
        return List.copyOf(entries);
    }

    private static LayoutEntry scalar(String name, int size) {
        return new LayoutEntry(LayoutEntry.SCALAR, name, size, 0, size);
    }

    private static boolean isField(LayoutEntry entry, String field) {
        return !entry.describesScalar() && field.equals(entry.fieldName());
    }

    private static LayoutEntry withOffset(LayoutEntry entry, int offset) {
        return new LayoutEntry(
                entry.structName(), entry.fieldName(), entry.size(), offset, entry.alignment());
    }
}
