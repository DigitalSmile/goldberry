package dev.goldberry.natives.desktop.keychain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.natives.NativePlatform;
import dev.goldberry.natives.desktop.dbus.PrivateBus;
import dev.goldberry.natives.desktop.keychain.SecretStoreException.Reason;

/// A secret goes into the Secret Service and comes back out, through a locked
/// keyring too.
///
/// Against a service of the test's own on a bus of its own — see
/// [FakeSecretService] — so the keyring of whoever runs the suite is never
/// touched. Skipped where there is no `dbus-daemon` or no libdbus.
@DisplayName("the Secret Service")
@Timeout(60)
class SecretServiceTest {

    private static final byte[] TOKEN = "refresh-7f3a".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    private FakeSecretService fake;
    private SecretService store;

    @BeforeEach
    void start() throws Exception {
        assumeTrue(NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX, "D-Bus is Linux's");
        fake = FakeSecretService.start(directory);
        assumeTrue(fake != null, "no dbus-daemon or no libdbus here");
        store = SecretService.at(fake.address(), Duration.ofSeconds(10));
    }

    @AfterEach
    void stop() {
        if (fake != null) {
            fake.close();
        }
    }

    @Test
    @DisplayName("stores a secret under the service and user name other keyrings use, and reads it back")
    void storesAndLoads() {
        store.store("tessera", "me@example.org", "Tessera (me@example.org)", TOKEN, SecretStore.TEXT);

        assertArrayEquals(TOKEN, store.load("tessera", "me@example.org").orElseThrow());
        var item = fake.items.values().iterator().next();
        assertEquals("Tessera (me@example.org)", item.label());
        assertEquals(
                Map.of("service", "tessera", "username", "me@example.org", "xdg:schema", SecretService.SCHEMA),
                item.attributes());
        assertEquals("text/plain", item.contentType());
    }

    @Test
    @DisplayName("replaces the secret rather than adding a second item")
    void replaces() {
        store.store("tessera", "me", "label", TOKEN, SecretStore.BYTES);
        store.store("tessera", "me", "label", new byte[] {1, 2, 3}, SecretStore.BYTES);

        assertEquals(1, fake.items.size(), fake.paths().toString());
        assertArrayEquals(new byte[] {1, 2, 3}, store.load("tessera", "me").orElseThrow());
    }

    @Test
    @DisplayName("finds an item another library stored without the schema attribute")
    void findsForeignItems() {
        fake.put(Map.of("service", "tessera", "username", "me"), TOKEN);

        assertArrayEquals(TOKEN, store.load("tessera", "me").orElseThrow());
    }

    @Test
    @DisplayName("answers nothing for a secret it does not have, and deletes once")
    void missingAndDeleted() {
        store.store("tessera", "me", "label", TOKEN, SecretStore.BYTES);

        assertTrue(store.load("tessera", "someone else").isEmpty());
        assertTrue(store.delete("tessera", "me"));
        assertFalse(store.delete("tessera", "me"));
        assertTrue(store.load("tessera", "me").isEmpty());
    }

    @Test
    @DisplayName("closes every session it opens")
    void closesSessions() {
        store.store("tessera", "me", "label", TOKEN, SecretStore.BYTES);
        store.load("tessera", "me");

        assertEquals(2, fake.sessionsOpened.get());
        assertEquals(fake.sessionsOpened.get(), fake.sessionsClosed.get());
    }

    @Nested
    @DisplayName("with the keyring locked")
    class Locked {

        @BeforeEach
        void lock() {
            fake.put(Map.of("service", "tessera", "username", "me"), TOKEN);
            fake.locked = true;
        }

        @Test
        @DisplayName("prompts once, and reads the secret when the user unlocks")
        void unlocks() {
            assertArrayEquals(TOKEN, store.load("tessera", "me").orElseThrow());
            assertEquals(1, fake.promptsShown.get());
            assertFalse(fake.locked);
        }

        @Test
        @DisplayName("prompts before it stores")
        void storesAfterUnlocking() {
            store.store("tessera", "other", "label", TOKEN, SecretStore.BYTES);

            assertEquals(1, fake.promptsShown.get());
            assertEquals(2, fake.items.size());
        }

        @Test
        @DisplayName("is denied when the user dismisses the prompt")
        void dismissed() {
            fake.answer = FakeSecretService.Answer.DISMISS;

            var refused = assertThrows(SecretStoreException.class, () -> store.load("tessera", "me"));

            assertEquals(Reason.DENIED, refused.reason());
        }

        @Test
        @DisplayName("is denied, and dismisses the prompt itself, when nobody answers it in time")
        void unanswered() {
            fake.answer = FakeSecretService.Answer.IGNORE;
            var impatient = SecretService.at(fake.address(), Duration.ofMillis(300));

            var refused = assertThrows(SecretStoreException.class, () -> impatient.load("tessera", "me"));

            assertEquals(Reason.DENIED, refused.reason());
            assertEquals(1, fake.promptsDismissedByCaller.get());
        }
    }

    @Test
    @DisplayName("is unavailable when nobody serves secrets on the bus")
    void nobodyThere() throws Exception {
        var empty = directory.resolve("empty");
        Files.createDirectories(empty);
        try (var bus = PrivateBus.start(empty)) {
            assumeTrue(bus != null);
            var refused = assertThrows(
                    SecretStoreException.class,
                    () -> SecretService.at(bus.address(), Duration.ofSeconds(1)).load("tessera", "me"));

            assertEquals(Reason.UNAVAILABLE, refused.reason());
        }
    }
}
