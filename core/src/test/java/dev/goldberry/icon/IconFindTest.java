package dev.goldberry.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `Icon.find`: a name from outside the program, looked up without an exception.
///
/// Nothing here paints, so none of it needs the renderer: an icon is a path
/// value, and whether one was found is a question about the bundled table.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#icons).
class IconFindTest {

    @Test
    @DisplayName("a bundled name is found at the size asked for")
    void found() {
        var icon = Icon.find("plus", 18).orElseThrow();

        assertEquals("plus", icon.name());
        assertEquals(18, icon.size());
        assertEquals(1.5, icon.strokeWidth(), 1e-9, "Lucide's 2 in 24, at 18");
    }

    @Test
    @DisplayName("a name the set does not have is empty rather than thrown")
    void absent() {
        assertTrue(Icon.find("no-such-icon", 16).isEmpty());
    }

    @Test
    @DisplayName("an unusable size is still a mistake in the program, and is refused")
    void sizeIsStillChecked() {
        assertThrows(IllegalArgumentException.class, () -> Icon.find("plus", 0));
    }

    @Test
    @DisplayName("what find hands back is what bundled builds")
    void sameAsBundled() {
        var found = Icon.find("check", 24).orElseThrow();
        var bundled = Icon.bundled("check", 24);

        assertEquals(bundled.outline(), found.outline());
    }
}
