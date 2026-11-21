package dev.goldberry.html.view;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// What `html-view` does with the styles a document brings: its `<style>` blocks,
/// its `style=` attributes and the `bgcolor` and `<font color>` of mail written
/// before CSS.
///
/// ```java
/// HtmlView.of(mail).authorStyles(AuthorStyles.ADAPTED);   // the default
/// HtmlView.of(help).authorStyles(AuthorStyles.IGNORED);   // the theme's, only
/// ```
///
/// ```kdl
/// html-view bind="mail.body" author-styles="theme-colours"
/// ```
///
/// Whichever is chosen, a document's styles reach its own page and nothing
/// else, and only the safe part of them is read at all: nothing that fetches,
/// nothing that moves a box out of the page, nothing that reads or sets the
/// theme. The choice here is about the rest, and mostly about colour, because a
/// mail is designed on its own white and its reader may be in a dark theme.
///
/// Read more: [HTML view](https://goldberry.dev/docs/components/content.html#html-view).
public enum AuthorStyles {

    /// The document's styles are not applied: the page is drawn by the
    /// application's stylesheets alone, as a help page should be.
    IGNORED,

    /// Sizes, weights, spacing, alignment, borders and boxes as the document
    /// writes them, and every colour the theme's. A mail reads like the rest of
    /// the application, in either theme.
    THEME_COLOURS,

    /// The document's styles, with each colour kept where it can be seen to read:
    /// a background the mail paints is kept, and text on it is held to 4.5:1
    /// against it, its own colour where that reads and dark or light ink where it
    /// does not. A text colour with no background of the mail's under it is the
    /// theme's, because the surface it would sit on is. The default.
    ADAPTED,

    /// The document's styles exactly as written, colours included, for an
    /// application that shows mail on a surface it knows to be white.
    AS_WRITTEN;

    /// The markup spelling: `ignored`, `theme-colours`, `adapted`, `as-written`.
    public String markupName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// `name` as markup spells it, or [#ADAPTED] for none and for a name nobody
    /// defined, which is what a lenient attribute does everywhere else.
    public static AuthorStyles parse(@Nullable String name) {
        if (name == null) {
            return ADAPTED;
        }
        // `theme-colors` too, as the toolkit's other colour attributes take both spellings.
        var wanted = name.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace("colors", "colours");
        for (var value : values()) {
            if (value.markupName().equals(wanted)) {
                return value;
            }
        }
        return ADAPTED;
    }
}
