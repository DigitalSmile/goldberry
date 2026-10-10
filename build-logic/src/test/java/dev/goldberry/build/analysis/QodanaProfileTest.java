package dev.goldberry.build.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goldberry.build.repository.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The owned-resource list in {@code config/qodana/profile.yaml}, held to the
 * source tree.
 *
 * <p>{@code AutoCloseableResource} ignores the types a window, a backend or a
 * player owns, by fully qualified name. A name that matches no class is
 * accepted and ignores nothing, so a typo, a rename or a move turns a reviewed
 * decision back into findings on the next run, with nothing in the build to
 * say why. The cursor types were added this way on 2026-10-10, after three
 * high-severity findings failed the gate.
 */
@DisplayName("Qodana's profile")
class QodanaProfileTest {

    private static final Pattern IGNORED_TYPES = Pattern.compile("ignoredTypes: \"([^\"]+)\"");

    /** IntelliJ's own defaults, which setting the option replaces rather than extends. */
    private static final int INTELLIJ_DEFAULTS = 16;

    private static List<String> ignoredTypes() {
        var matcher = IGNORED_TYPES.matcher(Repository.read("config/qodana/profile.yaml"));
        assertTrue(matcher.find(), "profile.yaml names no ignoredTypes for AutoCloseableResource");
        return Arrays.asList(matcher.group(1).split(","));
    }

    @Test
    @DisplayName("ignores only owned types that exist, each by the name of its source file")
    void everyOwnedTypeExists() {
        var owned = ignoredTypes().stream()
                .filter(name -> name.startsWith("dev.goldberry."))
                .toList();
        var missing = owned.stream()
                .filter(name -> Repository.files("*/src/main/java/" + name.replace('.', '/') + ".java")
                        .isEmpty())
                .toList();
        assertAll(
                () -> assertFalse(owned.isEmpty(), "read no dev.goldberry types; is the pattern wrong?"),
                () -> assertEquals(List.of(), missing, "these names match no class, so they ignore nothing"));
    }

    @Test
    @DisplayName("keeps IntelliJ's own defaults first, since the option replaces them")
    void keepsTheDefaults() {
        var defaults = ignoredTypes().subList(0, INTELLIJ_DEFAULTS);
        assertAll(
                () -> assertTrue(defaults.contains("java.util.stream.Stream"), defaults::toString),
                () -> assertTrue(defaults.contains("java.util.Scanner"), defaults::toString),
                () -> assertTrue(
                        defaults.stream().noneMatch(name -> name.startsWith("dev.goldberry.")),
                        "a goldberry type sits among IntelliJ's defaults: " + defaults));
    }

    @Test
    @DisplayName("names each owned type once")
    void namesEachTypeOnce() {
        var types = ignoredTypes();
        assertEquals(types.size(), types.stream().distinct().count(), "a type is listed twice: " + types);
    }
}
