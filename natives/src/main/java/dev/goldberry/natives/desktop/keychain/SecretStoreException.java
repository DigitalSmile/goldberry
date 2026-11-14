package dev.goldberry.natives.desktop.keychain;

import java.util.Objects;

/// A [SecretStore] that could not do what it was asked, and why.
public final class SecretStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /// Why a store refused.
    public enum Reason {
        /// There is no store to ask: no Secret Service on the session bus, no
        /// session bus, or a platform without one.
        UNAVAILABLE,
        /// The store is there and said no: the user dismissed an unlock prompt
        /// or denied access, or nobody answered the prompt in time.
        DENIED,
        /// The store answered with an error of its own.
        FAILED
    }

    private final Reason reason;

    public SecretStoreException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
