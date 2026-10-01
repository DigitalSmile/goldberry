package dev.goldberry.example.book.pictures;

import dev.goldberry.css.Theme;

/// The two looks every picture in the guide is taken in, which are the book's
/// two themes: mdBook's `light` shows the one, its `navy` the other
/// (`book/theme/goldberry.css`).
///
/// The page colour is the theme's `--gb-bg`, which is what a picture is filled
/// with before the widget is drawn and what the crop recognises as empty. It
/// is written here rather than read from the cascade because a picture's
/// backdrop is a fact about the file, and `nord-light.css` and `nord-dark.css`
/// each name it once.
public enum Shade {

    /// Nord light, `--nord6`.
    LIGHT(Theme.NORD_LIGHT, 0xFFECEFF4, "light"),

    /// Nord dark, `--nord0`.
    DARK(Theme.NORD_DARK, 0xFF2E3440, "dark");

    private final Theme theme;
    private final int page;
    private final String suffix;

    Shade(Theme theme, int page, String suffix) {
        this.theme = theme;
        this.page = page;
        this.suffix = suffix;
    }

    public Theme theme() {
        return theme;
    }

    /// The page colour, `0xAARRGGBB` and opaque.
    public int page() {
        return page;
    }

    /// What the file name ends in before its extension.
    public String suffix() {
        return suffix;
    }
}
