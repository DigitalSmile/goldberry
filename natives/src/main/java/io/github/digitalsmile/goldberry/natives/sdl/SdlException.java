package io.github.digitalsmile.goldberry.natives.sdl;

import java.io.Serial;

/// An SDL call that reported failure.
///
/// SDL's convention is a `false` return plus a message from `SDL_GetError()`,
/// which is easy to ignore and easy to lose: the message is overwritten by the
/// next failing call on the same thread. [Sdl] turns the pair into this exception
/// at the boundary, so a failure cannot be dropped by forgetting to check a
/// return value, and the message is captured while it is still the right one.
public final class SdlException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String operation;
    private final String sdlError;

    SdlException(String operation, String sdlError) {
        super(message(operation, sdlError));
        this.operation = operation;
        this.sdlError = sdlError;
    }

    /// The SDL function that failed, as written in C.
    public String operation() {
        return operation;
    }

    /// What `SDL_GetError()` said, which SDL leaves empty more often than its
    /// documentation suggests.
    public String sdlError() {
        return sdlError;
    }

    private static String message(String operation, String sdlError) {
        return sdlError == null || sdlError.isBlank()
                ? operation + " failed, and SDL_GetError() said nothing"
                : operation + " failed: " + sdlError;
    }
}
