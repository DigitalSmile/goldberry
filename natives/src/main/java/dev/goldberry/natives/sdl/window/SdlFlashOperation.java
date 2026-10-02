package dev.goldberry.natives.sdl.window;

/// What `SDL_FlashWindow` is asked to do — SDL's `SDL_FlashOperation`.
///
/// Read more: [Asking for attention](https://goldberry.dev/docs/guide/windows.html#asking-for-attention).
public enum SdlFlashOperation {
    /// Stop a flash that is still going.
    CANCEL(0),

    /// Flash once, briefly: one bounce of the dock icon.
    BRIEFLY(1),

    /// Flash until the window has the keyboard again.
    UNTIL_FOCUSED(2);

    private final int value;

    SdlFlashOperation(int value) {
        this.value = value;
    }

    /// The C enum's value.
    public int value() {
        return value;
    }
}
