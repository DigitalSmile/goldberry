package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// How `PrepareAssets` reads its command line.
///
/// `main` reads two positional arguments and two flags and then downloads ninety
/// megabytes, so the download is not a thing a test drives — but the *parsing* is
/// pure, and it is where a build script's mistake becomes a jar with the fonts in
/// the wrong package or with an asset missing. `:assets` had no test naming
/// either parser (the 2026-09-18 review, §6).
///
/// Both matter for a reason recorded elsewhere: `--root=` exists because a
/// resource directory **is a package** to the module system, and two modules
/// writing a font into `…goldberry.assets.fonts` is one package in two modules
/// and an application that refuses to start (ADR-0387). `--only=` exists because
/// `:emoji` fetches OpenMoji alone and must not pull the 90 MB `:core` takes
/// (ADR-0384).
class PrepareAssetsTest {

    private static Set<String> everyAsset() {
        return Asset.all().stream().map(Asset::name).collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("--root=")
    class Root {

        @Test
        @DisplayName("is the default package when nothing says otherwise")
        void defaultsToTheAssetsPackage() {
            assertEquals(PrepareAssets.RESOURCE_ROOT, PrepareAssets.root(new String[] {"cache", "out"}));
        }

        @Test
        @DisplayName("is the path the flag names, wherever the flag sits")
        void takesTheFlag() {
            var root = "io/github/digitalsmile/goldberry/emoji";

            assertEquals(root, PrepareAssets.root(new String[] {"cache", "out", "--root=" + root}));
            assertEquals(
                    root,
                    PrepareAssets.root(new String[] {"cache", "out", "--only=openmoji", "--root=" + root}),
                    "the flags are read by name, not by position");
        }
    }

    @Nested
    @DisplayName("--only=")
    class Only {

        @Test
        @DisplayName("is every asset when nothing says otherwise")
        void defaultsToEverything() {
            assertEquals(everyAsset(), PrepareAssets.selection(new String[] {"cache", "out"}));
        }

        @Test
        @DisplayName("is the named one, and a comma-separated list of them")
        void takesTheFlag() {
            var one = PrepareAssets.selection(new String[] {"cache", "out", "--only=openmoji"});
            assertEquals(Set.of("openmoji"), one);

            var several = PrepareAssets.selection(new String[] {"cache", "out", "--only=inter,lucide"});
            assertEquals(Set.of("inter", "lucide"), several);
        }

        @Test
        @DisplayName("refuses a name no asset has, and says what there is")
        void refusesAnUnknownName() {
            // A typo here would otherwise be a silent no-op: the loop would
            // prepare nothing, the build would succeed, and the jar would ship
            // without the font.
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> PrepareAssets.selection(new String[] {"cache", "out", "--only=openmojo"}));

            assertTrue(thrown.getMessage().contains("openmojo"), thrown.getMessage());
            assertTrue(
                    thrown.getMessage().contains("openmoji"),
                    () -> "the message should list what there is: " + thrown.getMessage());
        }

        @Test
        @DisplayName("refuses a list where only one name is wrong")
        void refusesAPartlyWrongList() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PrepareAssets.selection(new String[] {"cache", "out", "--only=inter,lucid"}));
        }
    }
}
