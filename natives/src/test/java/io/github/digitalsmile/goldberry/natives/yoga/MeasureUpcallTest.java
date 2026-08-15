package io.github.digitalsmile.goldberry.natives.yoga;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// The proof that a `YGSize` returned **by value** from a Java upcall arrives
/// intact in C.
///
/// This is the check ADR-0017 exists for, and it cannot be replaced by anything
/// on the Java side: a wrong return convention produces a stub the JVM builds
/// happily and C reads as garbage. It has to run on every target, because the
/// convention differs on each — packed in XMM0 on SysV x86-64, `s0`/`s1` on
/// AArch64, folded into RAX on Win64.
class MeasureUpcallTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("the struct returned by value arrives in C unchanged")
    void structSurvivesTheCrossing() {
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> new MeasuredSize(123.5f, 45.25f))) {
            var measured = MeasureProbe.measure(callback, 200f, MeasureMode.AT_MOST, 100f, MeasureMode.EXACTLY);

            // Two distinct, exactly representable values: swapping the fields or
            // reading one register for both would still pass with equal numbers.
            assertEquals(123.5f, measured.width(), "width");
            assertEquals(45.25f, measured.height(), "height");
        }
    }

    @Test
    @DisplayName("the constraints reach Java as they were passed to C")
    void argumentsArriveIntact() {
        var seen = new ArrayList<String>();
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> {
            seen.add(w + "/" + wm + "/" + h + "/" + hm);
            return new MeasuredSize(1f, 2f);
        })) {
            MeasureProbe.measure(callback, 320.75f, MeasureMode.AT_MOST, 240.5f, MeasureMode.UNDEFINED);
        }

        assertEquals(List.of("320.75/AT_MOST/240.5/UNDEFINED"), seen);
    }

    @ParameterizedTest
    @EnumSource(MeasureMode.class)
    @DisplayName("every measure mode crosses as itself")
    void everyModeCrosses(MeasureMode mode) {
        var seen = new MeasureMode[1];
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> {
            seen[0] = wm;
            return new MeasuredSize(0f, 0f);
        })) {
            MeasureProbe.measure(callback, 10f, mode, 10f, MeasureMode.UNDEFINED);
        }

        assertSame(mode, seen[0]);
    }

    @Test
    @DisplayName("the reused return buffer is not reused too early")
    void repeatedCallsAreIndependent() {
        // The segment handed back to the linker is allocated once per callback,
        // which is only safe because the call is synchronous. If it were not,
        // successive results would bleed into each other.
        var results = new ArrayList<MeasuredSize>();
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> new MeasuredSize(w * 2f, h + 1f))) {
            for (var i = 1; i <= 5; i++) {
                results.add(MeasureProbe.measure(
                        callback, i, MeasureMode.EXACTLY, i * 10f, MeasureMode.AT_MOST));
            }
        }

        assertEquals(
                List.of(
                        new MeasuredSize(2f, 11f),
                        new MeasuredSize(4f, 21f),
                        new MeasuredSize(6f, 31f),
                        new MeasuredSize(8f, 41f),
                        new MeasuredSize(10f, 51f)),
                results);
    }

    @Test
    @DisplayName("a failing measure function is reported in Java, not as a dead JVM")
    void failureIsHeldAndRethrown() {
        var boom = new IllegalStateException("no font loaded");
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> {
            throw boom;
        })) {
            // If the exception escaped into C the process would be gone, so
            // reaching the assertion at all is half the result.
            var thrown = assertThrows(
                    IllegalStateException.class,
                    () -> MeasureProbe.measure(callback, 10f, MeasureMode.EXACTLY, 10f, MeasureMode.EXACTLY));

            assertSame(boom, thrown);
        }
    }

    @Test
    @DisplayName("a failure is cleared once rethrown, so the next call starts clean")
    void failureIsClearedAfterRethrow() {
        var fail = new boolean[] {true};
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> {
            if (fail[0]) {
                throw new IllegalStateException("first call fails");
            }
            return new MeasuredSize(7f, 8f);
        })) {
            assertThrows(
                    IllegalStateException.class,
                    () -> MeasureProbe.measure(callback, 1f, MeasureMode.EXACTLY, 1f, MeasureMode.EXACTLY));

            fail[0] = false;
            var measured = MeasureProbe.measure(callback, 1f, MeasureMode.EXACTLY, 1f, MeasureMode.EXACTLY);

            assertEquals(new MeasuredSize(7f, 8f), measured);
        }
    }

    @Test
    @DisplayName("an unusable measurement is caught before it reaches Yoga")
    void nanNeverCrosses() {
        // MeasuredSize rejects NaN in its constructor, so the failure happens on
        // the Java side of the boundary and surfaces through the same path as
        // any other callback failure.
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> new MeasuredSize(Float.NaN, 1f))) {
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> MeasureProbe.measure(callback, 1f, MeasureMode.EXACTLY, 1f, MeasureMode.EXACTLY));

            assertEquals(true, thrown.getMessage().contains("NaN"));
        }
    }

    @Test
    @DisplayName("nothing is pending when nothing failed")
    void noFailureIsNoOp() {
        try (var callback = MeasureCallback.of((w, wm, h, hm) -> new MeasuredSize(1f, 1f))) {
            MeasureProbe.measure(callback, 1f, MeasureMode.EXACTLY, 1f, MeasureMode.EXACTLY);

            assertDoesNotThrow(callback::throwIfFailed);
        }
    }
}
