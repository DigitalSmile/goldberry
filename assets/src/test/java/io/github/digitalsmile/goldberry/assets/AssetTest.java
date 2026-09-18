package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The manifest an asset is declared in, and what it refuses.
///
/// `:assets` had no test naming [Asset]'s compact constructor at all (the
/// 2026-09-18 review, §6), which is the one place a typo in the manifest is
/// catchable *before* a build spends ninety megabytes finding out. Every
/// assertion here is about a mistake somebody could make editing the table in
/// `Asset` — a truncated checksum, a licence URL with nowhere to put it — rather
/// than about the values themselves, which are what `checkLicenses` and the
/// download cache verify.
class AssetTest {

    private static final String GOOD_SHA = "0".repeat(64);

    private static Asset asset(String sha256) {
        return new Asset("inter", "4.0", "https://example.invalid/inter.zip", sha256, Map.of(), Map.of(), null, null);
    }

    @Nested
    @DisplayName("what the constructor refuses")
    class Refusals {

        @Test
        @DisplayName("a checksum that is not 64 lowercase hex characters, and it says which asset")
        void theChecksumIsAChecksum() {
            // The name is in the message because a build prepares several assets
            // and "not a SHA-256" without one is a hunt through the manifest.
            var thrown = assertThrows(IllegalArgumentException.class, () -> asset("abc"));
            assertTrue(thrown.getMessage().contains("inter"), thrown.getMessage());

            assertThrows(IllegalArgumentException.class, () -> asset("A".repeat(64)), "uppercase is not hex here");
            assertThrows(IllegalArgumentException.class, () -> asset("0".repeat(63)), "63 is not 64");
            assertThrows(IllegalArgumentException.class, () -> asset("0".repeat(65)), "nor is 65");
        }

        @Test
        @DisplayName("a licence URL with nowhere to put it, and a destination with no URL")
        void aLicenceIsBothOrNeither() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Asset(
                            "inter", "4.0", "u", GOOD_SHA, Map.of(), Map.of(), "https://example.invalid/OFL", null));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Asset("inter", "4.0", "u", GOOD_SHA, Map.of(), Map.of(), null, "inter.txt"));
        }

        @Test
        @DisplayName("a missing name, version, url or checksum")
        void theFourRequiredOnes() {
            assertThrows(
                    NullPointerException.class,
                    () -> new Asset(null, "4.0", "u", GOOD_SHA, Map.of(), Map.of(), null, null));
            assertThrows(
                    NullPointerException.class,
                    () -> new Asset("inter", null, "u", GOOD_SHA, Map.of(), Map.of(), null, null));
            assertThrows(
                    NullPointerException.class,
                    () -> new Asset("inter", "4.0", null, GOOD_SHA, Map.of(), Map.of(), null, null));
            assertThrows(
                    NullPointerException.class,
                    () -> new Asset("inter", "4.0", "u", null, Map.of(), Map.of(), null, null));
        }
    }

    @Nested
    @DisplayName("what it normalises")
    class Normalising {

        @Test
        @DisplayName("a null extract or licence map is an empty one, not a null field")
        void nullMapsBecomeEmpty() {
            var asset = new Asset("inter", "4.0", "u", GOOD_SHA, null, null, null, null);

            assertEquals(Map.of(), asset.extract());
            assertEquals(Map.of(), asset.licence());
        }

        @Test
        @DisplayName("the maps are copied, so the manifest cannot be edited through the caller's map")
        void theMapsAreCopies() {
            var extract = new java.util.HashMap<String, String>();
            extract.put("Inter.ttf", "fonts/inter.ttf");
            var asset = new Asset("inter", "4.0", "u", GOOD_SHA, extract, Map.of(), null, null);

            extract.put("smuggled.ttf", "fonts/smuggled.ttf");

            assertEquals(1, asset.extract().size(), "the asset took a copy, not the caller's map");
            assertThrows(UnsupportedOperationException.class, () -> asset.extract().put("k", "v"));
        }

        @Test
        @DisplayName("the archive is cached under the asset's own name")
        void theArchiveName() {
            assertEquals("inter.zip", asset(GOOD_SHA).archiveName());
        }
    }

    @Nested
    @DisplayName("the shipped manifest")
    class TheManifest {

        @Test
        @DisplayName("every asset is named once, so --only= resolves to exactly one")
        void namesAreUnique() {
            var seen = new HashSet<String>();
            for (var asset : Asset.all()) {
                assertTrue(seen.add(asset.name()), () -> asset.name() + " is declared twice");
            }
            assertFalse(seen.isEmpty(), "the manifest is empty");
        }

        @Test
        @DisplayName("and every one of them survives its own constructor")
        void theManifestIsWellFormed() {
            // The table is built by static initialisers, so this is really an
            // assertion that the class loaded — which is exactly the check that
            // catches a checksum somebody shortened while pasting.
            for (var asset : Asset.all()) {
                assertTrue(asset.sha256().matches("[0-9a-f]{64}"), () -> asset.name() + ": " + asset.sha256());
                assertFalse(asset.url().isBlank(), () -> asset.name() + " has no URL");
                assertEquals(
                        asset.licenceUrl() == null,
                        asset.licenceAs() == null,
                        () -> asset.name() + ": a licence URL and its destination go together");
            }
        }
    }
}
