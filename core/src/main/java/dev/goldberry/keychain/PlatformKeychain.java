package dev.goldberry.keychain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.keychain.SecretStore;
import dev.goldberry.natives.desktop.keychain.SecretStoreException;

/// [Keychain#system()]: the platform's [SecretStore], with the toolkit's words
/// on its refusals.
final class PlatformKeychain implements Keychain {

    /// Made on first use, because finding the store binds a system library.
    private static final class Holder {
        private static final PlatformKeychain SYSTEM =
                new PlatformKeychain(SecretStore.forThisPlatform().orElse(null));
    }

    static PlatformKeychain system() {
        return Holder.SYSTEM;
    }

    private final @Nullable SecretStore store;

    PlatformKeychain(@Nullable SecretStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return store == null ? "none" : store.name();
    }

    @Override
    public boolean isAvailable() {
        return store != null;
    }

    @Override
    public void store(String service, String account, byte[] secret) {
        Objects.requireNonNull(secret, "secret");
        key(
                service,
                account,
                (s, a) -> run(() -> {
                    store().store(s, a, label(s, a), secret, SecretStore.BYTES);
                    return true;
                }));
    }

    @Override
    public void store(String service, String account, String secret) {
        var bytes = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
        key(
                service,
                account,
                (s, a) -> run(() -> {
                    store().store(s, a, label(s, a), bytes, SecretStore.TEXT);
                    return true;
                }));
    }

    @Override
    public Optional<byte[]> load(String service, String account) {
        return key(service, account, (s, a) -> run(() -> store().load(s, a)));
    }

    @Override
    public boolean delete(String service, String account) {
        return key(service, account, (s, a) -> run(() -> store().delete(s, a)));
    }

    private SecretStore store() {
        if (store == null) {
            throw new KeychainException(
                    KeychainException.Reason.UNAVAILABLE, "this platform has no secret store this build can reach");
        }
        return store;
    }

    /// What the store's own viewer shows for an item.
    static String label(String service, String account) {
        return service + " (" + account + ")";
    }

    /// Checks a service and an account, and hands them on.
    static <T> T key(String service, String account, BiFunction<String, String, T> then) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(account, "account");
        if (service.isBlank()) {
            throw new IllegalArgumentException("a keychain service is not blank");
        }
        return then.apply(service, account);
    }

    /// Runs `call`, putting the toolkit's words on a store's refusal.
    static <T> T run(Supplier<T> call) {
        try {
            return call.get();
        } catch (SecretStoreException e) {
            throw translate(e);
        }
    }

    static KeychainException translate(SecretStoreException e) {
        var reason = switch (e.reason()) {
            case UNAVAILABLE -> KeychainException.Reason.UNAVAILABLE;
            case DENIED -> KeychainException.Reason.DENIED;
            case FAILED -> KeychainException.Reason.FAILED;
        };
        var translated = new KeychainException(reason, Objects.requireNonNullElse(e.getMessage(), reason.name()));
        translated.initCause(e);
        return translated;
    }
}
