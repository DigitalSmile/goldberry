package dev.goldberry.natives.desktop.keychain;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.desktop.keychain.SecretStoreException.Reason;

/// What each store's own failures mean to an application, and the parts of
/// the macOS and Windows stores that can be checked on any machine.
@DisplayName("a secret store's refusals")
class SecretStoreReasonsTest {

    @Test
    @DisplayName("names a Secret Service error by what it means to an application")
    void secretService() {
        assertEquals(
                Reason.UNAVAILABLE,
                SecretService.failure("Unlock", "org.freedesktop.DBus.Error.ServiceUnknown", "")
                        .reason());
        assertEquals(
                Reason.DENIED,
                SecretService.failure("GetSecret", "org.freedesktop.Secret.Error.IsLocked", "")
                        .reason());
        assertEquals(
                Reason.FAILED,
                SecretService.failure("CreateItem", "org.freedesktop.Secret.Error.NoSuchObject", "gone")
                        .reason());
    }

    @Test
    @DisplayName("names a Keychain OSStatus by what it means to an application")
    void keychain() {
        assertDoesNotThrow(() -> MacKeychain.check("SecItemAdd", MacKeychain.SUCCESS));
        assertEquals(Reason.DENIED, refusal(MacKeychain.USER_CANCELED));
        assertEquals(Reason.DENIED, refusal(MacKeychain.AUTH_FAILED));
        assertEquals(Reason.DENIED, refusal(MacKeychain.INTERACTION_NOT_ALLOWED));
        assertEquals(Reason.UNAVAILABLE, refusal(MacKeychain.NOT_AVAILABLE));
        assertEquals(Reason.FAILED, refusal(-50));
    }

    private static Reason refusal(int status) {
        return assertThrows(SecretStoreException.class, () -> MacKeychain.check("SecItemAdd", status))
                .reason();
    }

    @Test
    @DisplayName("is the platform's own store, or none")
    void forThisPlatform() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var store = SecretStore.forThisPlatform();
        if (os.contains("linux") && SecretService.connect().isPresent()) {
            assertEquals("Secret Service", store.orElseThrow().name());
        }
        if (!os.contains("win")) {
            assertTrue(WindowsCredentials.open().isEmpty());
        }
        if (!os.contains("mac")) {
            assertTrue(MacKeychain.open().isEmpty());
        }
    }

    /// The offsets [WindowsCredentials] writes `CREDENTIALW` at are the ones
    /// the 64-bit Windows SDK lays it out with, checked against the structure
    /// spelled out field by field, because the store runs only on Windows.
    @Nested
    @DisplayName("the Windows credential structure")
    class WindowsLayout {

        /// `CREDENTIALW`, from `wincred.h`, with the padding the x64 ABI puts
        /// before each pointer. `FILETIME` is two `DWORD`s.
        private static final StructLayout CREDENTIALW = MemoryLayout.structLayout(
                JAVA_INT.withName("Flags"),
                JAVA_INT.withName("Type"),
                ADDRESS.withName("TargetName"),
                ADDRESS.withName("Comment"),
                JAVA_INT.withName("LastWrittenLow"),
                JAVA_INT.withName("LastWrittenHigh"),
                JAVA_INT.withName("CredentialBlobSize"),
                MemoryLayout.paddingLayout(4),
                ADDRESS.withName("CredentialBlob"),
                JAVA_INT.withName("Persist"),
                JAVA_INT.withName("AttributeCount"),
                ADDRESS.withName("Attributes"),
                ADDRESS.withName("TargetAlias"),
                ADDRESS.withName("UserName"));

        private static long offset(String field) {
            return CREDENTIALW.byteOffset(PathElement.groupElement(field));
        }

        @Test
        @DisplayName("is written at the SDK's offsets, and is the SDK's size")
        void offsets() {
            assertEquals(WindowsCredentials.SIZE, CREDENTIALW.byteSize());
            assertEquals(WindowsCredentials.TYPE, offset("Type"));
            assertEquals(WindowsCredentials.TARGET_NAME, offset("TargetName"));
            assertEquals(WindowsCredentials.COMMENT, offset("Comment"));
            assertEquals(WindowsCredentials.BLOB_SIZE, offset("CredentialBlobSize"));
            assertEquals(WindowsCredentials.BLOB, offset("CredentialBlob"));
            assertEquals(WindowsCredentials.PERSIST, offset("Persist"));
            assertEquals(WindowsCredentials.USER_NAME, offset("UserName"));
        }

        @Test
        @DisplayName("files a credential under service:account")
        void target() {
            assertEquals("tessera:me@example.org", WindowsCredentials.target("tessera", "me@example.org"));
        }
    }
}
