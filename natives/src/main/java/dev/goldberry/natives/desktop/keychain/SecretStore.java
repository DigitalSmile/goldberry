package dev.goldberry.natives.desktop.keychain;

import java.util.Locale;
import java.util.Optional;

/// One secret per service and account, in the system's own store.
///
/// Blocking: a store may ask the user to unlock it, and every call waits for
/// the answer. Never call one on the UI thread. Safe to call from several
/// threads; each call is one conversation with the store.
public sealed interface SecretStore extends AutoCloseable permits SecretService, MacKeychain, WindowsCredentials {

    /// The plain-text content type, which a store's own viewer shows.
    String TEXT = "text/plain";

    /// The content type of anything else.
    String BYTES = "application/octet-stream";

    /// The store this platform has, or empty where there is none this build can
    /// reach. Says nothing about whether the store answers, which only asking
    /// can tell.
    static Optional<SecretStore> forThisPlatform() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return WindowsCredentials.open().map(SecretStore.class::cast);
        }
        if (os.contains("mac")) {
            return MacKeychain.open().map(SecretStore.class::cast);
        }
        if (os.contains("linux") || os.contains("bsd")) {
            return SecretService.connect().map(SecretStore.class::cast);
        }
        return Optional.empty();
    }

    /// What this store is called, for a log line or a settings page.
    String name();

    /// Stores `secret` for `service` and `account`, replacing what was there.
    ///
    /// @param label       what the store's own viewer shows for it
    /// @param contentType [#TEXT] or [#BYTES]; only the Secret Service keeps it
    /// @throws SecretStoreException when the store could not or would not
    void store(String service, String account, String label, byte[] secret, String contentType);

    /// The secret for `service` and `account`, or empty when there is none.
    ///
    /// @throws SecretStoreException when the store could not or would not
    Optional<byte[]> load(String service, String account);

    /// Deletes the secret for `service` and `account`.
    ///
    /// @return whether there was one
    /// @throws SecretStoreException when the store could not or would not
    boolean delete(String service, String account);

    @Override
    void close();
}
