package dev.goldberry.keychain;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// [Keychain#inMemory()]: secrets in a map, for tests.
final class MemoryKeychain implements Keychain {

    private record Key(String service, String account) {}

    private final Map<Key, byte[]> secrets = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void store(String service, String account, byte[] secret) {
        secrets.put(PlatformKeychain.key(service, account, Key::new), secret.clone());
    }

    @Override
    public void store(String service, String account, String secret) {
        store(service, account, secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<byte[]> load(String service, String account) {
        return Optional.ofNullable(secrets.get(PlatformKeychain.key(service, account, Key::new)))
                .map(byte[]::clone);
    }

    @Override
    public boolean delete(String service, String account) {
        return secrets.remove(PlatformKeychain.key(service, account, Key::new)) != null;
    }
}
