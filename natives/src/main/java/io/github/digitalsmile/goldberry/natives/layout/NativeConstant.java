package io.github.digitalsmile.goldberry.natives.layout;

import java.util.Objects;

/// A C constant Goldberry hard-codes on the Java side, paired with the name the
/// compiled library reports it under.
///
/// The same argument as [NativeStructLayout], applied to values rather than
/// layouts. Hand-written bindings mean hand-written enumerator values, and those
/// fail more quietly than offsets do: a wrong `SDL_EVENT_WINDOW_CLOSE_REQUESTED`
/// does not crash, it just never matches, and the window never closes.
public record NativeConstant(String name, long value) {

    public NativeConstant {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("constant name must not be blank");
        }
    }
}
