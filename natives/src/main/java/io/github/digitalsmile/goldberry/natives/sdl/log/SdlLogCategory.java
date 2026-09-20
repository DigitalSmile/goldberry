package io.github.digitalsmile.goldberry.natives.sdl.log;

import java.util.Locale;

/// SDL's `SDL_LogCategory`, which becomes the last segment of a logger name.
///
/// `native.sdl.video`, `native.sdl.render`, `native.sdl.input` — so an
/// application debugging a display problem can raise one of them without also
/// turning on the others.
///
/// Ordinals in a C enum, on the layout table for
/// [SdlLogPriority]'s reason. The nine reserved values between [#GPU] and
/// [#CUSTOM] are deliberately absent: they are SDL's room to grow, they have no
/// meaning to name, and a row on the table for each would be nine constants
/// asserting that a gap is still a gap. [#nameOf] is what handles them.
public enum SdlLogCategory {

    /// Where SDL puts an application's own `SDL_Log` calls. Goldberry makes
    /// none, so anything arriving here came from an application or from a
    /// library inside SDL that borrowed it.
    APPLICATION(0),

    ERROR(1),

    ASSERT(2),

    SYSTEM(3),

    AUDIO(4),

    /// The busiest one in this toolkit: the driver, the window, the display.
    VIDEO(5),

    RENDER(6),

    INPUT(7),

    TEST(8),

    GPU(9),

    /// `SDL_LOG_CATEGORY_CUSTOM`, where an application's own categories start.
    /// Anything at or above this is somebody else's numbering, and [#nameOf]
    /// reports it by number rather than guessing.
    CUSTOM(19);

    private final int value;

    SdlLogCategory(int value) {
        this.value = value;
    }

    /// The `SDL_LOG_CATEGORY_*` value.
    public int value() {
        return value;
    }

    /// The name `goldberry_shim.c` reports this value under.
    public String nativeName() {
        return "SDL_LOG_CATEGORY_" + name();
    }

    /// The logger-name segment for this category — its name, lower-cased.
    public String segment() {
        return name().toLowerCase(Locale.ROOT);
    }

    /// The logger-name segment for any category number SDL hands over.
    ///
    /// A number this enum does not know becomes `category-<n>`, which is a
    /// logger name an application can still spell in its configuration. That is
    /// the whole requirement: a reserved category SDL starts using tomorrow, or
    /// an application's own, must be *routable* even though it cannot be named.
    public static String nameOf(int value) {
        for (var category : values()) {
            if (category.value == value) {
                return category.segment();
            }
        }
        return "category-" + value;
    }
}
