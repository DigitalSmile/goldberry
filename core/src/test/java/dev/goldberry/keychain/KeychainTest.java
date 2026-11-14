package dev.goldberry.keychain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.natives.desktop.keychain.SecretStoreException;

/// What an application sees of the system's secret store. The stores
/// themselves are tested in `:natives`, against a Secret Service of the
/// test's own; nothing here touches the keyring of whoever runs the suite.
@DisplayName("a keychain")
class KeychainTest {

    @Nested
    @DisplayName("in memory")
    class InMemory {

        private final Keychain keychain = Keychain.inMemory();

        @Test
        @DisplayName("gives back what it was given, as bytes or as text")
        void roundTrip() {
            keychain.store("tessera", "me", new byte[] {1, 2, 3});
            keychain.store("tessera", "you", "pässword");

            assertArrayEquals(
                    new byte[] {1, 2, 3}, keychain.load("tessera", "me").orElseThrow());
            assertEquals(Optional.of("pässword"), keychain.loadText("tessera", "you"));
            assertEquals(Optional.empty(), keychain.load("other", "me"));
        }

        @Test
        @DisplayName("keeps a copy, so the caller clearing its array clears nothing stored")
        void copies() {
            var secret = new byte[] {7, 7};
            keychain.store("tessera", "me", secret);
            secret[0] = 0;
            keychain.load("tessera", "me").orElseThrow()[1] = 0;

            assertArrayEquals(new byte[] {7, 7}, keychain.load("tessera", "me").orElseThrow());
        }

        @Test
        @DisplayName("deletes once")
        void deletes() {
            keychain.store("tessera", "me", "token");

            assertTrue(keychain.delete("tessera", "me"));
            assertFalse(keychain.delete("tessera", "me"));
            assertEquals(Optional.empty(), keychain.loadText("tessera", "me"));
        }

        @Test
        @DisplayName("refuses a blank service, which no store could find again")
        void blankService() {
            assertThrows(IllegalArgumentException.class, () -> keychain.store(" ", "me", "token"));
        }

        @Test
        @DisplayName("is its own, not shared with another in-memory keychain")
        void separate() {
            keychain.store("tessera", "me", "token");

            assertTrue(Keychain.inMemory().load("tessera", "me").isEmpty());
        }
    }

    @Test
    @DisplayName("is the platform's store, the same one on every call")
    void system() {
        var system = Keychain.system();

        assertSame(system, Keychain.system());
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            assertEquals("macOS Keychain", system.name());
        } else if (os.contains("win")) {
            assertEquals("Windows Credential Manager", system.name());
        } else if (system.isAvailable()) {
            assertEquals("Secret Service", system.name());
        } else {
            assertEquals("none", system.name());
        }
    }

    @Test
    @DisplayName("without a store, says so on every call rather than pretending")
    void unavailable() {
        var none = new PlatformKeychain(null);

        assertFalse(none.isAvailable());
        assertEquals("none", none.name());
        var refused = assertThrows(KeychainException.class, () -> none.load("tessera", "me"));
        assertEquals(KeychainException.Reason.UNAVAILABLE, refused.reason());
    }

    @ParameterizedTest
    @EnumSource(SecretStoreException.Reason.class)
    @DisplayName("puts the toolkit's words on a store's refusal, keeping the store's own as the cause")
    void translates(SecretStoreException.Reason reason) {
        var original = new SecretStoreException(reason, "the keyring's prompt was dismissed");

        var translated = PlatformKeychain.translate(original);

        assertEquals(reason.name(), translated.reason().name());
        assertEquals("the keyring's prompt was dismissed", translated.getMessage());
        assertSame(original, translated.getCause());
    }

    @Test
    @DisplayName("labels an item with its service and account, for the store's own viewer")
    void label() {
        assertEquals("tessera (me@example.org)", PlatformKeychain.label("tessera", "me@example.org"));
    }
}
