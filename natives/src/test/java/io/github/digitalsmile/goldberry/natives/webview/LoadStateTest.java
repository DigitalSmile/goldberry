package io.github.digitalsmile.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What the shim's `goldberry_webview_load_state` returns, read back.
///
/// The numbers are the shim's own — there is no upstream enum to check them
/// against, unlike SDL's and Yoga's, because this state is computed in C out of
/// two WebKit calls rather than reported by anything. So what is asserted here
/// is that the Java side agrees with the four values `goldberry_webview.cc`
/// documents at the top of that function, and that an unknown one is survivable.
@DisplayName("a page's load state")
class LoadStateTest {

    @Test
    @DisplayName("is read back from the number the shim returns")
    void readsEachValue() {
        for (var state : LoadState.values()) {
            assertEquals(state, LoadState.of(state.value()), state.name());
        }
    }

    /// The four the shim's doc comment names, spelled out: this is the one place
    /// the two sides of an ad-hoc contract can be compared, since there is no
    /// header to include.
    @Test
    @DisplayName("and the numbers are the ones the shim documents")
    void agreesWithTheShim() {
        assertEquals(-1, LoadState.UNKNOWN.value());
        assertEquals(0, LoadState.IDLE.value());
        assertEquals(1, LoadState.LOADING.value());
        assertEquals(2, LoadState.FINISHED.value());
    }

    /// Asked once a frame from inside a painter. A shim that grew a fifth state
    /// should make a page appear, not make a frame throw — the rule
    /// `SdlSubsystem.decode` states.
    @Test
    @DisplayName("answering UNKNOWN for a number this build has never heard of")
    void survivesAnUnknownNumber() {
        assertEquals(LoadState.UNKNOWN, LoadState.of(7));
        assertEquals(LoadState.UNKNOWN, LoadState.of(Integer.MIN_VALUE));
    }

    /// `IDLE` is the state a page is in between being created and being
    /// navigated, and it exists because it is **not** the same as `FINISHED`:
    /// collapsing the two is a page shown white for the length of its first
    /// load, which is the defect this whole mechanism is for.
    @Test
    @DisplayName("and a page that has not started is not a page that has finished")
    void idleIsNotFinished() {
        assertNotEquals(LoadState.IDLE, LoadState.FINISHED);
    }
}
