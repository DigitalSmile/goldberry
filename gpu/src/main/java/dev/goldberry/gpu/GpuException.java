package dev.goldberry.gpu;

import java.io.Serial;
import java.util.Objects;

import dev.goldberry.natives.sdl.SdlException;

/// The GPU driver refused something the API had already checked it could ask:
/// a device out of memory, a shader that does not compile or link, a size past
/// the device's limit, a lost device.
///
/// A misuse the API can see for itself -- a closed resource, another device's
/// texture, a draw with nothing bound -- is an `IllegalArgumentException` or an
/// `IllegalStateException` instead, thrown before the driver is called.
public final class GpuException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// A refusal described by `message`, caused by `cause`.
    public GpuException(String message, Throwable cause) {
        super(message, cause);
    }

    /// The driver's refusal, as SDL reported it.
    static GpuException of(SdlException cause) {
        return new GpuException(Objects.requireNonNullElse(cause.getMessage(), cause.operation()), cause);
    }
}
