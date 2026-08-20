package io.github.digitalsmile.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// What `-Dgoldberry.trace.frames` means, tested without setting it.
///
/// [FrameTrace#ENABLED] and [FrameTrace#ALL_FRAMES] are read once into `static
/// final` fields — that is what makes the counters free when tracing is off
/// (ADR-0101) — so a test cannot set the property and observe them. The reading
/// is therefore a pure function of the property's value, and this is a test of
/// that function.
///
/// The case it exists for: **`all` used to switch tracing off.** `ENABLED` was
/// `Boolean.getBoolean`, false for anything but `true`, while `ALL_FRAMES`
/// looked for `all` — so the setting that asks for *more* output produced none
/// at all, and every call site guarded by `ENABLED` was skipped.
class FrameTraceFlagsTest {

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "all", "ALL"})
    @DisplayName("both spellings turn tracing on")
    void bothSpellingsTrace(String value) {
        assertTrue(FrameTrace.enabled(value), value + " should enable tracing");
    }

    @Test
    @DisplayName("`all` implies `true` — the stronger setting never does less")
    void allImpliesEnabled() {
        assertTrue(FrameTrace.allFrames("all"));
        assertTrue(FrameTrace.enabled("all"),
                "`all` asks for more output than `true`, so it cannot trace less");
    }

    @Test
    @DisplayName("`true` traces, but not the quiet frames")
    void trueIsNotAll() {
        assertTrue(FrameTrace.enabled("true"));
        assertFalse(FrameTrace.allFrames("true"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "false", "yes", "1", "everything"})
    @DisplayName("anything else is off, including the plausible spellings")
    void everythingElseIsOff(String value) {
        assertFalse(FrameTrace.enabled(value), value + " should not enable tracing");
        assertFalse(FrameTrace.allFrames(value), value + " should not report quiet frames");
    }
}
