package dev.goldberry.keychain;

import java.io.Serial;
import java.util.Objects;

/// A [Keychain] that could not do what it was asked, and why.
///
/// Unchecked, like the toolkit's other failures, but worth catching: a
/// keychain that is [Reason#UNAVAILABLE] is a desktop without one (a
/// container, a minimal window manager with no keyring daemon), and an
/// application usually falls back to asking the user to sign in again.
public final class KeychainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// Why a keychain refused.
    public enum Reason {
        /// There is no store to ask: no Secret Service on the session bus, no
        /// session bus, or a platform without a store.
        UNAVAILABLE,
        /// The store is there and said no: the user dismissed the unlock
        /// prompt or denied access, or nobody answered the prompt in time.
        DENIED,
        /// The store answered with an error of its own.
        FAILED
    }

    private final Reason reason;

    public KeychainException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /// Why it refused.
    public Reason reason() {
        return reason;
    }
}
