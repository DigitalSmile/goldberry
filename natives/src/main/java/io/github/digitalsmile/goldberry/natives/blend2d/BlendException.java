package io.github.digitalsmile.goldberry.natives.blend2d;

import java.io.Serial;
import java.util.Optional;

/// A Blend2D call that reported failure.
///
/// Blend2D's convention is a `BLResult` return on every function — an unsigned
/// code, zero for success — and no side channel of any kind. That makes it easy
/// to ignore in exactly the way SDL's is, so it gets the same treatment
/// [SdlException][io.github.digitalsmile.goldberry.natives.sdl.SdlException]
/// gets: the result is turned into an exception at the boundary, and a failure
/// cannot be dropped by forgetting to check a return value.
///
/// The difference is that Blend2D has no `GetError` to ask for a message, so
/// what a report can carry is the code and the operation that produced it —
/// which is why [BlendResultCode] exists at all.
public final class BlendException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String operation;
    private final int result;

    BlendException(String operation, int result) {
        super(message(operation, result));
        this.operation = operation;
        this.result = result;
    }

    /// The Blend2D function that failed, as written in C.
    public String operation() {
        return operation;
    }

    /// The raw `BLResult`. Unsigned, so compare it against
    /// [BlendResultCode#nativeValue()] rather than ordering it.
    public int result() {
        return result;
    }

    /// The named code, or empty when Blend2D returned one Goldberry has not
    /// named. An unnamed code is not a failure of this class — it is a code
    /// nobody has needed to read yet.
    public Optional<BlendResultCode> code() {
        return Optional.ofNullable(BlendResultCode.of(result));
    }

    private static String message(String operation, int result) {
        var code = BlendResultCode.of(result);
        var described = code == null
                ? "an unnamed BLResult"
                : code.nativeName();
        return operation + " failed with " + described
                + " (0x" + String.format("%08X", result) + ")";
    }
}
