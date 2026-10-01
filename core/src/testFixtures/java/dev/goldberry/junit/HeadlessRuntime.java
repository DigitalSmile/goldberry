package dev.goldberry.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.render.backend.headless.HeadlessBackend;

/// Gives each test a running Goldberry runtime on the `headless` backend, and
/// takes it down again afterwards.
///
/// A widget that follows something off the UI thread — a media player's status,
/// a load that finishes on a virtual thread — posts its rebuild through
/// [dev.goldberry.Goldberry#ui()], and that **starts the runtime** if nothing
/// has: the desktop backend, SDL, and under it `libgoldberry`. A test that mounts
/// such a widget without a window therefore needs a runtime to be there already,
/// or the first status change boots SDL from a decoder thread — and in a build
/// with no native library fails there, with the library's `Holder` poisoned for
/// every test after it (ADR-0517).
///
/// `:core`'s own tests install a [HeadlessBackend] by hand in `@BeforeEach` and
/// shut it down in `@AfterEach`. This is that pair as a JUnit extension, for a
/// test in another module:
///
/// ```java
/// @ExtendWith(HeadlessRuntime.class)
/// class AudioPlayerTest { ... }
/// ```
///
/// Installed before the test's own `@BeforeEach` methods and shut down after its
/// `@AfterEach` methods, which is JUnit's order for extension callbacks — so a
/// player closed in `@AfterEach` publishes its last status to a runtime that is
/// still there. A test that installs its own backend as well fails loudly: the
/// runtime refuses a second installation.
public final class HeadlessRuntime implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        GoldberryTestAccess.install(new HeadlessBackend());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        GoldberryTestAccess.shutdown();
    }
}
