package dev.goldberry.natives.desktop.keychain;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.desktop.keychain.SecretStoreException.Reason;

/// The Windows Credential Manager, through `advapi32`'s `Cred*` calls: a
/// generic credential per service and account, kept for this user on this
/// machine.
///
/// UNVERIFIED: written against the SDK's documentation and not yet run,
/// because no lane drives a Windows credential store. The structure's offsets
/// are checked against the SDK's layout by a test that runs anywhere.
///
/// The credential's target is `service:account` and its user name is the
/// account, which is what Credential Manager shows and what other libraries'
/// keyrings write. A credential read back whose user name is not the account
/// is somebody else's under a colliding target, and is not answered.
///
/// ## What Windows does not say
///
/// Why a call failed is in `GetLastError`, which a downcall cannot read
/// reliably without capturing it, and a captured call state is a shape the
/// native image would have to be told about separately. So a read that fails
/// is answered as no secret, and a delete that fails as nothing deleted. Only
/// a write that fails is an error.
public final class WindowsCredentials implements SecretStore {

    /// The most a credential's blob holds: `CRED_MAX_CREDENTIAL_BLOB_SIZE`.
    public static final int MAX_SECRET_BYTES = 5 * 512;

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;

    // CREDENTIALW, 64-bit.
    static final long SIZE = 80;
    static final long TYPE = 4;
    static final long TARGET_NAME = 8;
    static final long COMMENT = 16;
    static final long BLOB_SIZE = 32;
    static final long BLOB = 40;
    static final long PERSIST = 48;
    static final long USER_NAME = 72;

    /// `BOOL CredWriteW(PCREDENTIALW, DWORD)`.
    private static final FunctionDescriptor WRITE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `BOOL CredReadW(LPCWSTR, DWORD, DWORD, PCREDENTIALW *)`.
    private static final FunctionDescriptor READ =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

    /// `BOOL CredDeleteW(LPCWSTR, DWORD, DWORD)`.
    private static final FunctionDescriptor DELETE =
            Downcalls.describe(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));

    /// `void CredFree(PVOID)`.
    private static final FunctionDescriptor FREE = Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS));

    private final MethodHandle write;
    private final MethodHandle read;
    private final MethodHandle delete;
    private final MethodHandle free;

    private WindowsCredentials(MethodHandle write, MethodHandle read, MethodHandle delete, MethodHandle free) {
        this.write = write;
        this.read = read;
        this.delete = delete;
        this.free = free;
    }

    /// The user's credentials, or empty off Windows.
    @SuppressWarnings("restricted")
    public static Optional<WindowsCredentials> open() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        try {
            var advapi = SymbolLookup.libraryLookup("advapi32.dll", Arena.global());
            return Optional.of(new WindowsCredentials(
                    Downcalls.link(WRITE).bindTo(advapi.find("CredWriteW").orElseThrow()),
                    Downcalls.link(READ).bindTo(advapi.find("CredReadW").orElseThrow()),
                    Downcalls.link(DELETE).bindTo(advapi.find("CredDeleteW").orElseThrow()),
                    Downcalls.link(FREE).bindTo(advapi.find("CredFree").orElseThrow())));
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// The target a credential is filed under.
    static String target(String service, String account) {
        return service + ":" + account;
    }

    @Override
    public String name() {
        return "Windows Credential Manager";
    }

    @Override
    public void store(String service, String account, String label, byte[] secret, String contentType) {
        if (secret.length > MAX_SECRET_BYTES) {
            throw new SecretStoreException(
                    Reason.FAILED,
                    "the Credential Manager holds at most " + MAX_SECRET_BYTES + " bytes, and this is "
                            + secret.length);
        }
        try (var arena = Arena.ofConfined()) {
            var blob = arena.allocate(Math.max(1, secret.length));
            MemorySegment.copy(secret, 0, blob, JAVA_BYTE, 0, secret.length);
            var credential = arena.allocate(SIZE, 8);
            credential.fill((byte) 0);
            credential.set(JAVA_INT, TYPE, CRED_TYPE_GENERIC);
            credential.set(ADDRESS, TARGET_NAME, wide(arena, target(service, account)));
            credential.set(ADDRESS, COMMENT, wide(arena, label));
            credential.set(JAVA_INT, BLOB_SIZE, secret.length);
            credential.set(ADDRESS, BLOB, blob);
            credential.set(JAVA_INT, PERSIST, CRED_PERSIST_LOCAL_MACHINE);
            credential.set(ADDRESS, USER_NAME, wide(arena, account));
            var written = (int) call(write, credential, 0) != 0;
            blob.fill((byte) 0);
            if (!written) {
                throw new SecretStoreException(Reason.FAILED, "CredWriteW refused the credential");
            }
        }
    }

    @Override
    @SuppressWarnings("restricted")
    public Optional<byte[]> load(String service, String account) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ADDRESS);
            if ((int) call(read, wide(arena, target(service, account)), CRED_TYPE_GENERIC, 0, out) == 0) {
                return Optional.empty();
            }
            var credential = out.get(ADDRESS, 0).reinterpret(SIZE);
            try {
                var user = credential.get(ADDRESS, USER_NAME);
                if (user.address() == 0
                        || !account.equals(
                                user.reinterpret(Integer.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE))) {
                    return Optional.empty();
                }
                var size = Integer.toUnsignedLong(credential.get(JAVA_INT, BLOB_SIZE));
                var blob = credential.get(ADDRESS, BLOB);
                return Optional.of(
                        size == 0 ? new byte[0] : blob.reinterpret(size).toArray(JAVA_BYTE));
            } finally {
                call(free, credential);
            }
        }
    }

    @Override
    public boolean delete(String service, String account) {
        if (load(service, account).isEmpty()) {
            return false;
        }
        try (var arena = Arena.ofConfined()) {
            return (int) call(delete, wide(arena, target(service, account)), CRED_TYPE_GENERIC, 0) != 0;
        }
    }

    @Override
    public void close() {
        // Nothing is held between calls.
    }

    private static MemorySegment wide(Arena arena, String text) {
        return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
    }

    private static Object call(MethodHandle handle, Object... arguments) {
        try {
            var result = handle.invokeWithArguments(arguments);
            return result == null ? Boolean.TRUE : result;
        } catch (Throwable e) {
            throw new SecretStoreException(Reason.FAILED, "a Credential Manager call failed: " + e);
        }
    }
}
