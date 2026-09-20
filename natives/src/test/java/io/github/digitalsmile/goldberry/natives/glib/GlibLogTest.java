package io.github.digitalsmile.goldberry.natives.glib;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.log.bridge.NativeLogBridge;
import io.github.digitalsmile.goldberry.log.bridge.NativeLogLevel;

/// The GLib side of ADR-0443, checked without a GLib.
///
/// Everything below runs on a machine with no desktop libraries at all: the
/// translation from `GLogLevelFlags` to an SLF4J level and from a log domain to
/// a logger name is arithmetic and string handling, and it is the whole of what
/// can be wrong in a way a reader would not notice. What needs a real GLib — the
/// `dlopen`, the upcall stub, the handler actually being called — is checked by
/// running an application with a tray, which is what `:example` is for.
@DisplayName("the GLib log bridge")
class GlibLogTest {

    @Nested
    @DisplayName("reads a GLogLevelFlags")
    class Levels {

        @Test
        @DisplayName("as the level GLib set, whichever it is")
        void readsEachLevel() {
            for (var level : GlibLogLevel.values()) {
                assertEquals(level, GlibLogLevel.of(level.bit()), level.name());
            }
        }

        /// GLib ORs `G_LOG_FLAG_FATAL` into the level for anything on the fatal
        /// mask, and always for `ERROR`. A reader that did not mask the flags
        /// off would classify every fatal warning as a `MESSAGE`.
        @Test
        @DisplayName("ignoring the two flag bits that are not levels")
        void masksOffTheFlagBits() {
            assertEquals(GlibLogLevel.WARNING, GlibLogLevel.of(GlibLogLevel.WARNING.bit() | GlibLogLevel.FATAL));
            assertEquals(GlibLogLevel.WARNING, GlibLogLevel.of(GlibLogLevel.WARNING.bit() | 1));
        }

        /// The rule `SdlSubsystem.decode` states: a value the enum does not know
        /// is read inside an upcall, on a message GLib is emitting right now,
        /// and throwing would turn a log line into a crash.
        @Test
        @DisplayName("answering MESSAGE for a level a future GLib invented")
        void doesNotThrowOnAnUnknownLevel() {
            assertEquals(GlibLogLevel.MESSAGE, GlibLogLevel.of(1 << 20));
            assertEquals(GlibLogLevel.MESSAGE, GlibLogLevel.of(0));
        }

        /// The line GLib's own default handler draws — `ERROR`, `CRITICAL`,
        /// `WARNING` and `MESSAGE` are printed without being asked for, `INFO`
        /// and `DEBUG` are not — is the line SLF4J draws at `info`. Mapping name
        /// to name instead would put GTK's per-frame `INFO` chatter on a level
        /// most applications leave switched on.
        @Test
        @DisplayName("and puts GLib's quiet levels below SLF4J's info")
        void keepsGlibsOwnQuietLine() {
            assertEquals(NativeLogLevel.ERROR, GlibLogLevel.ERROR.level());
            assertEquals(NativeLogLevel.ERROR, GlibLogLevel.CRITICAL.level());
            assertEquals(NativeLogLevel.WARN, GlibLogLevel.WARNING.level());
            assertEquals(NativeLogLevel.INFO, GlibLogLevel.MESSAGE.level());
            assertEquals(NativeLogLevel.DEBUG, GlibLogLevel.INFO.level());
            assertEquals(NativeLogLevel.TRACE, GlibLogLevel.DEBUG.level());
        }

        @Test
        @DisplayName("and knows when GLib is about to abort")
        void reportsFatal() {
            assertTrue(GlibLogLevel.isFatal(GlibLogLevel.ERROR.bit()), "G_LOG_LEVEL_ERROR is always fatal");
            assertTrue(GlibLogLevel.isFatal(GlibLogLevel.WARNING.bit() | GlibLogLevel.FATAL));
            assertFalse(GlibLogLevel.isFatal(GlibLogLevel.WARNING.bit()));
        }
    }

    @Nested
    @DisplayName("names the logger a message lands on")
    class Naming {

        /// The message this whole bridge was built for, and the one line an
        /// application is most likely to want switched off by name.
        @Test
        @DisplayName("so the libayatana-appindicator deprecation is addressable")
        void namesTheAppindicatorLogger() {
            assertEquals(
                    "native.glib.libayatana-appindicator",
                    NativeLogBridge.loggerName(GlibLog.SOURCE, "libayatana-appindicator"));
        }

        @Test
        @DisplayName("under the source alone when GLib passed no domain")
        void namesTheSourceAloneWithoutADomain() {
            assertEquals("native.glib", NativeLogBridge.loggerName(GlibLog.SOURCE, null));
        }
    }

    @Nested
    @DisplayName("is called from C, so it")
    class NeverThrows {

        /// `report` is what the upcall stub reduces to once the two C strings
        /// have been read. A throw from here would cross back into GLib, with a
        /// GLib lock held, possibly on its way to `abort()`.
        @Test
        @DisplayName("survives a null domain, a null message and a nonsense level")
        void survivesAnything() {
            assertDoesNotThrow(() -> GlibLog.report("Gtk", GlibLogLevel.WARNING.bit(), "a message"));
            assertDoesNotThrow(() -> GlibLog.report(null, 0, null));
            assertDoesNotThrow(() -> GlibLog.report("", Integer.MIN_VALUE, ""));
        }
    }

    @Nested
    @DisplayName("looks for GLib")
    class Discovery {

        /// macOS and Windows have no GLib unless somebody installed one, and
        /// neither SDL's tray nor its web view uses it there. Asking would be a
        /// `dlopen` that fails on every Mac, once per process.
        @Test
        @DisplayName("only where a desktop that has one runs")
        void onlyOnPlatformsWithGlib() {
            assertTrue(GlibLibrary.isPlatformWithGlib("Linux"));
            assertTrue(GlibLibrary.isPlatformWithGlib("FreeBSD"));
            assertFalse(GlibLibrary.isPlatformWithGlib("Mac OS X"));
            assertFalse(GlibLibrary.isPlatformWithGlib("Windows 11"));
        }

        /// By the runtime soname rather than by `libglib-2.0.so`, which is the
        /// symlink a `-dev` package installs and which a machine merely running
        /// an application does not have.
        @Test
        @DisplayName("by its runtime soname")
        void byTheRuntimeSoname() {
            assertEquals("libglib-2.0.so.0", GlibLibrary.SONAME);
        }
    }
}
