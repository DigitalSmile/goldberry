package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.log.bridge.NativeLogBridge;
import io.github.digitalsmile.goldberry.log.bridge.NativeLogLevel;
import io.github.digitalsmile.goldberry.natives.sdl.log.SdlLogCategory;
import io.github.digitalsmile.goldberry.natives.sdl.log.SdlLogPriority;

/// The SDL side of ADR-0443, checked without SDL.
///
/// The values themselves are held to the C compiler by `LayoutVerifier` through
/// `NativeConstants`, which is where a renumbered `SDL_LogPriority` is caught.
/// What is here is everything downstream of the number: which SLF4J level it
/// becomes, and which logger name a category makes.
@DisplayName("the SDL log bridge")
class SdlLogTest {

    @Nested
    @DisplayName("reads a priority")
    class Priorities {

        @Test
        @DisplayName("as the one SDL sent, whichever it is")
        void readsEachPriority() {
            for (var priority : SdlLogPriority.values()) {
                assertEquals(priority, SdlLogPriority.of(priority.value()), priority.name());
            }
        }

        /// SDL's names and SLF4J's mean the same things, so the mapping is name
        /// to name in the middle. The joins are at the ends, where SDL has one
        /// rung more than SLF4J at each.
        @Test
        @DisplayName("mapping name to name, and joining only at the two ends")
        void mapsNameToName() {
            assertEquals(NativeLogLevel.WARN, SdlLogPriority.WARN.level());
            assertEquals(NativeLogLevel.INFO, SdlLogPriority.INFO.level());
            assertEquals(NativeLogLevel.DEBUG, SdlLogPriority.DEBUG.level());

            assertEquals(
                    SdlLogPriority.ERROR.level(),
                    SdlLogPriority.CRITICAL.level(),
                    "SLF4J has no rung above error, so the two join");
            assertEquals(
                    SdlLogPriority.TRACE.level(),
                    SdlLogPriority.VERBOSE.level(),
                    "and none below trace, so these two join");
        }

        /// `SDL_LOG_PRIORITY_TRACE` was inserted at 1, below `VERBOSE`, and
        /// everything above it moved up by one. A binding that predates the
        /// insertion reports every message one rung too loud and says nothing
        /// about it — which is the reason these are on the layout table.
        @Test
        @DisplayName("from the ordinals SDL has already renumbered once")
        void usesTheOrdinalsSdlRenumbered() {
            assertEquals(1, SdlLogPriority.TRACE.value());
            assertEquals(2, SdlLogPriority.VERBOSE.value());
            assertEquals(7, SdlLogPriority.CRITICAL.value());
        }

        /// The rule `SdlSubsystem.decode` states: this is read inside an upcall,
        /// on a message SDL is emitting right now.
        @Test
        @DisplayName("answering INFO for a priority a future SDL invented")
        void doesNotThrowOnAnUnknownPriority() {
            assertEquals(SdlLogPriority.INFO, SdlLogPriority.of(99));
            assertEquals(SdlLogPriority.INFO, SdlLogPriority.of(-1));
        }
    }

    @Nested
    @DisplayName("names a logger from the category")
    class Categories {

        @Test
        @DisplayName("so one subsystem can be raised without the rest")
        void namesOneSubsystem() {
            assertEquals(
                    "native.sdl.video",
                    NativeLogBridge.loggerName(SdlLog.SOURCE, SdlLogCategory.nameOf(SdlLogCategory.VIDEO.value())));
            assertEquals("render", SdlLogCategory.nameOf(SdlLogCategory.RENDER.value()));
            assertEquals("input", SdlLogCategory.nameOf(SdlLogCategory.INPUT.value()));
        }

        /// The nine reserved values between GPU and CUSTOM are SDL's room to
        /// grow and name nothing. A category SDL starts using tomorrow, or an
        /// application's own above CUSTOM, must still be *routable* even though
        /// it cannot be named.
        @Test
        @DisplayName("by number when it is one SDL has not named")
        void namesAnUnknownCategoryByNumber() {
            assertEquals("category-12", SdlLogCategory.nameOf(12));
            assertEquals("category-40", SdlLogCategory.nameOf(40));
            assertTrue(NativeLogBridge.loggerName(SdlLog.SOURCE, SdlLogCategory.nameOf(12))
                    .endsWith(".category-12"));
        }

        @Test
        @DisplayName("and every named category is its own logger")
        void everyCategoryIsItsOwnLogger() {
            for (var category : SdlLogCategory.values()) {
                for (var other : SdlLogCategory.values()) {
                    if (category != other) {
                        assertNotEquals(category.segment(), other.segment(), category + " vs " + other);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("is called from C, so it")
    class NeverThrows {

        @Test
        @DisplayName("survives a null message and a category nobody has heard of")
        void survivesAnything() {
            assertDoesNotThrow(() -> SdlLog.report(
                    SdlLogCategory.VIDEO.value(), SdlLogPriority.WARN.value(), "no driver could be initialized"));
            assertDoesNotThrow(() -> SdlLog.report(-7, 99, null));
        }
    }
}
