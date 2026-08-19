package io.github.digitalsmile.goldberry.weaver;

/// A model the weaver refuses, with the reason a person can act on.
///
/// Every rule in [Bind]-land is checked here rather than left to fail at run
/// time, because the whole point of doing the wiring in the build is that the
/// failures move with it: a `@Bind` on a `static` field, a duplicate path, an
/// `@Action` taking two arguments. A typo that reaches runtime is a control that
/// renders perfectly and never moves, and this is what stops one.
///
/// Thrown by [ModelWeaver] and printed by the build task with the class it came
/// from.
public final class WeaveException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /// @param message what is wrong, naming the member
    public WeaveException(String message) {
        super(message);
    }

    /// @param message what is wrong, naming the member
    /// @param cause   what made it impossible to tell
    public WeaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
