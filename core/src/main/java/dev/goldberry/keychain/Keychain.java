package dev.goldberry.keychain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/// One secret per service and account, kept in the system's own store: a
/// refresh token, an API key, a database password.
///
/// ```java
/// var keychain = Keychain.system();
/// Thread.startVirtualThread(() -> {
///     keychain.store("tessera", account, refreshToken);
///     // later, on the next launch
///     var token = keychain.loadText("tessera", account);
/// });
/// ```
///
/// | Platform | Store | What its own viewer shows |
/// |----------|-------|---------------------------|
/// | Linux    | the Secret Service over D-Bus: gnome-keyring, KWallet, KeePassXC | an item in the login keyring with
/// the attributes `service` and `username` |
/// | macOS    | the login Keychain, through `Security.framework` | a generic password with the service and account |
/// | Windows  | the Credential Manager, through `advapi32` | a generic credential `service:account`, at most 2560 bytes
/// |
///
/// Nothing is bundled: each store is a library the system already has.
///
/// ## Blocking, and maybe asking the user
///
/// Every call waits for the store, and a locked store asks the user to unlock
/// it first: a keyring password prompt on Linux, an access dialog on macOS.
/// So **never call one on the UI thread**. A virtual thread is enough. A
/// prompt the user dismisses, or does not answer within two minutes, is a
/// [KeychainException] with [KeychainException.Reason#DENIED].
///
/// ## Text and bytes
///
/// A secret is bytes. The `String` overloads store UTF-8, and on Linux mark
/// the item as text so the keyring's own viewer can show it.
///
/// ## In tests
///
/// [#inMemory()] is a keychain that keeps its secrets in a map, so a test of
/// a sign-in flow neither touches the real store nor needs one.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-low-level-path).
public sealed interface Keychain permits PlatformKeychain, MemoryKeychain {

    /// The system's store, the same instance on every call. Where this
    /// platform has none this build can reach, every call throws
    /// [KeychainException.Reason#UNAVAILABLE] and [#isAvailable()] says so.
    static Keychain system() {
        return PlatformKeychain.system();
    }

    /// A keychain of its own that keeps secrets in memory, for tests.
    static Keychain inMemory() {
        return new MemoryKeychain();
    }

    /// What the store is called: `Secret Service`, `macOS Keychain`,
    /// `Windows Credential Manager`, `memory`, or `none`.
    String name();

    /// Whether there is a store to ask. True does not promise it answers:
    /// on Linux that is a keyring daemon running, which only a call finds out.
    boolean isAvailable();

    /// Stores `secret` for `service` and `account`, replacing what was there.
    ///
    /// @param service the application, or a part of it: `tessera`
    /// @param account whose secret it is, within the service
    /// @throws KeychainException when the store could not or would not
    void store(String service, String account, byte[] secret);

    /// Stores `secret` as UTF-8 text.
    ///
    /// @throws KeychainException when the store could not or would not
    void store(String service, String account, String secret);

    /// The secret for `service` and `account`, or empty when there is none.
    ///
    /// @throws KeychainException when the store could not or would not
    Optional<byte[]> load(String service, String account);

    /// The secret for `service` and `account` as UTF-8 text, or empty.
    ///
    /// @throws KeychainException when the store could not or would not
    default Optional<String> loadText(String service, String account) {
        return load(service, account).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /// Deletes the secret for `service` and `account`.
    ///
    /// @return whether there was one
    /// @throws KeychainException when the store could not or would not
    boolean delete(String service, String account);
}
