package dev.goldberry.example.ui.lookalike.library;

import java.util.Locale;

/// The picture on a playlist or an album: a gradient, named here and painted by
/// `chapter-lookalike.css`.
///
/// No bitmaps, because the colours are the whole of what a cover says at the
/// size the screen draws it, and a gradient scales to every one of them.
public enum Cover {
    /// Indigo into a pale lilac, which is what a list of liked songs looks like.
    LIKED,
    EMBER,
    LAGOON,
    MOSS,
    DUSK,
    GOLD,
    SLATE,
    ROSE;

    /// The class a stylesheet paints this cover on, as `cover-ember`.
    public String cssClass() {
        return "cover-" + name().toLowerCase(Locale.ROOT);
    }

    /// The class that tints a page in this cover's colour, as `tint-ember`.
    public String tintClass() {
        return "tint-" + name().toLowerCase(Locale.ROOT);
    }
}
