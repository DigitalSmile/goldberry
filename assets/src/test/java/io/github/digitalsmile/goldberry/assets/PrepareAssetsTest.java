package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
/// `:emoji` fetches the emoji face alone and must not pull the 90 MB `:core` takes
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
                    PrepareAssets.root(new String[] {"cache", "out", "--only=noto-emoji", "--root=" + root}),
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
            var one = PrepareAssets.selection(new String[] {"cache", "out", "--only=noto-emoji"});
            assertEquals(Set.of("noto-emoji"), one);

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
                    () -> PrepareAssets.selection(new String[] {"cache", "out", "--only=noto-emojo"}));

            assertTrue(thrown.getMessage().contains("noto-emojo"), thrown.getMessage());
            assertTrue(
                    thrown.getMessage().contains("noto-emoji"),
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

    @Nested
    @DisplayName("a single-file asset")
    class SingleFile {

        @Test
        @DisplayName("is copied to its one destination, byte for byte, under the root")
        void isCopiedWhole(@TempDir Path directory) throws IOException {
            // What Noto's emoji face is: the download is the font, and there is
            // no archive to open. Treating it as a zip would fail with "not a zip
            // file", which says nothing about the manifest.
            var download = directory.resolve("noto-emoji.ttf");
            var bytes = new byte[] {0, 1, 0, 0, 42, 7};
            Files.write(download, bytes);
            var asset = new Asset(
                    "noto-emoji",
                    "1",
                    "https://example.invalid/Noto-COLRv1.ttf",
                    "0".repeat(64),
                    Map.of("Noto-COLRv1.ttf", "fonts/NotoColorEmoji.ttf"),
                    Map.of(),
                    null,
                    null,
                    Asset.Packaging.FILE);

            var resources = directory.resolve("out");
            PrepareAssets.extract(asset, download, resources);

            var written = resources.resolve("fonts/NotoColorEmoji.ttf");
            assertTrue(Files.isRegularFile(written), "the font landed where the manifest said");
            assertEquals(java.util.HexFormat.of().formatHex(bytes),
                    java.util.HexFormat.of().formatHex(Files.readAllBytes(written)));
        }
    }
}
