package io.github.digitalsmile.goldberry.input.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The suite presses the same modifier on every desktop — [ADR-0396].
///
/// `PrimaryModifier.current()` is `Cmd` on macOS and `Ctrl` elsewhere
/// (ADR-0378), and the editing accelerators follow it. Every test that types
/// `Modifiers.of(Mod.CTRL)` into a field is therefore a different test on a
/// macOS runner unless the build pins the answer, which the test conventions do
/// with `-Dgoldberry.input.primary=ctrl`. This holds that pin in place: it
/// fails on every platform if the property is dropped, rather than on the one
/// runner where the twenty-five tests it protects would fail instead.
///
/// What macOS resolves to without the override is [ShortcutTest]'s business,
/// through `resolve(osName, override)`, which needs no macOS to run.
@DisplayName("the primary modifier under the test suite")
class PrimaryModifierTest {

    @Test
    @DisplayName("the build pins it to Ctrl, so a test that presses Ctrl is the same test everywhere")
    void pinnedByTheBuild() {
        assertEquals(
                "ctrl",
                System.getProperty(PrimaryModifier.PROPERTY),
                "goldberry.java-conventions.gradle sets -D" + PrimaryModifier.PROPERTY
                        + "=ctrl on every test task; without it the editing accelerators answer Cmd on macOS"
                        + " and every Ctrl+A/C/V/X/Z test fails there");
        assertEquals(Mod.CTRL, PrimaryModifier.current());
    }
}
