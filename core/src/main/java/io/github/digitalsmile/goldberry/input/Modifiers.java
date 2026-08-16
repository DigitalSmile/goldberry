package io.github.digitalsmile.goldberry.input;

/// Which modifier keys were held.
///
/// A record of four booleans rather than a bitmask, because every caller asks
/// "was shift down" and none asks for the mask — and a mask invites the toolkit's
/// numbering to leak into application code.
///
/// @param shift any Shift
/// @param control any Ctrl
/// @param alt any Alt / Option
/// @param meta any Super / Command / Windows key
public record Modifiers(boolean shift, boolean control, boolean alt, boolean meta) {

    public static final Modifiers NONE = new Modifiers(false, false, false, false);

    // SDL's SDL_KMOD_* bits. Left and right are separate flags, and every caller
    // here means "either", so they are folded on the way in.
    private static final int SDL_SHIFT = 0x0001 | 0x0002;
    private static final int SDL_CTRL = 0x0040 | 0x0080;
    private static final int SDL_ALT = 0x0100 | 0x0200;
    private static final int SDL_GUI = 0x0400 | 0x0800;

    /// Reads SDL's modifier bitmask.
    public static Modifiers fromSdl(int mask) {
        return new Modifiers(
                (mask & SDL_SHIFT) != 0,
                (mask & SDL_CTRL) != 0,
                (mask & SDL_ALT) != 0,
                (mask & SDL_GUI) != 0);
    }

    /// Whether no modifier was held — the common test for a plain keypress.
    public boolean none() {
        return !shift && !control && !alt && !meta;
    }

    /// Whether only `control` is held, which is what an accelerator usually
    /// means by "Ctrl+S".
    public boolean onlyControl() {
        return control && !shift && !alt && !meta;
    }

    @Override
    public String toString() {
        if (none()) {
            return "none";
        }
        var text = new StringBuilder();
        if (control) {
            text.append("Ctrl+");
        }
        if (alt) {
            text.append("Alt+");
        }
        if (shift) {
            text.append("Shift+");
        }
        if (meta) {
            text.append("Meta+");
        }
        return text.substring(0, text.length() - 1);
    }
}
