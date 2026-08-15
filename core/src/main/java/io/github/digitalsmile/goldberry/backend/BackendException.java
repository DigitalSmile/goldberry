package io.github.digitalsmile.goldberry.backend;

import java.io.Serial;

/// A platform operation failed.
///
/// Backends translate their own failure conventions into this — SDL's `false`
/// plus `SDL_GetError()`, an X11 error code, a Wayland protocol error — so code
/// above the SPI does not learn which backend it is on by catching exceptions.
public class BackendException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BackendException(String message) {
        super(message);
    }

    public BackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
