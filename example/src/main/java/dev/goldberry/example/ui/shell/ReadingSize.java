package dev.goldberry.example.ui.shell;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// How large the showcase sets its text: three steps of the type scale, each a
/// stylesheet of tokens.
///
/// The toolkit's scale is 13px body text, which is right for a label beside a
/// control and small for the paragraphs a gallery is mostly made of. The
/// showcase reads at [#MEDIUM], 15px, and a reader can step down to the
/// toolkit's own sizes or up to [#LARGE].
///
/// A size is a sheet of custom properties on `:root`, in the application's
/// layer, so it wins over the theme's tokens and reaches every tree the window
/// draws — the menus and the dialogs as well as the screens, which a class on
/// the window's root would not. The same rule sets the root's own `font-size`
/// and `line-height` to the body's, so a `text` nobody classed, which inherits
/// rather than reading a token, grows too. Nothing else changes: a control's
/// height is the density's, and the text grows inside it.
///
/// Read more: [Text scale](https://goldberry.dev/docs/guide/text.html#text-scale).
public enum ReadingSize {

    /// The toolkit's own scale: 13px body text.
    SMALL("Small", new Scale(28, 34, 20, 26, 15, 20, 13, 18, 11, 14, 13, 18)),

    /// The showcase's: 15px body text.
    MEDIUM("Medium", new Scale(32, 40, 24, 30, 18, 24, 15, 22, 13, 18, 14, 20)),

    /// 17px body text, for a reader a long way from the screen.
    LARGE("Large", new Scale(36, 44, 28, 34, 20, 28, 17, 26, 15, 20, 16, 24));

    /// One step of the scale: a font size and a line height for each rank, in
    /// logical pixels.
    record Scale(
            int display,
            int displayLine,
            int title,
            int titleLine,
            int heading,
            int headingLine,
            int body,
            int bodyLine,
            int caption,
            int captionLine,
            int code,
            int codeLine) {

        /// The step as the theme's custom properties.
        String css() {
            return """
                    :root {
                      --gb-font-display: %dpx;
                      --gb-line-display: %dpx;
                      --gb-font-title: %dpx;
                      --gb-line-title: %dpx;
                      --gb-font-heading: %dpx;
                      --gb-line-heading: %dpx;
                      --gb-font-body: %dpx;
                      --gb-line-body: %dpx;
                      --gb-font-caption: %dpx;
                      --gb-line-caption: %dpx;
                      --gb-font-code: %dpx;
                      --gb-line-code: %dpx;
                      font-size: %dpx;
                      line-height: %dpx;
                    }
                    """.formatted(
                            display,
                            displayLine,
                            title,
                            titleLine,
                            heading,
                            headingLine,
                            body,
                            bodyLine,
                            caption,
                            captionLine,
                            code,
                            codeLine,
                            body,
                            bodyLine);
        }
    }

    /// Parsed once each: a restyle asks for the sheets again, and a size that
    /// has not changed is the same sheet.
    private static final Map<ReadingSize, Stylesheet> SHEETS = new EnumMap<>(ReadingSize.class);

    static {
        for (var size : values()) {
            SHEETS.put(size, Stylesheet.parse(CascadeLayer.APPLICATION, size.css));
        }
    }

    /// The sizes, smallest first.
    private static final List<ReadingSize> ORDER = List.of(values());

    private final String title;
    private final int body;
    private final String css;

    ReadingSize(String title, Scale scale) {
        this.title = title;
        this.body = scale.body();
        this.css = scale.css();
    }

    /// What the View menu calls it.
    public String title() {
        return title;
    }

    /// Body text at this size, in logical pixels.
    public int body() {
        return body;
    }

    /// The tokens, as a sheet to put after the showcase's own.
    public Stylesheet stylesheet() {
        return Objects.requireNonNull(SHEETS.get(this), "every size has a sheet");
    }

    /// One step larger, or this size when it is the largest.
    public ReadingSize larger() {
        return ORDER.get(Math.min(ORDER.indexOf(this) + 1, ORDER.size() - 1));
    }

    /// One step smaller, or this size when it is the smallest.
    public ReadingSize smaller() {
        return ORDER.get(Math.max(ORDER.indexOf(this) - 1, 0));
    }

    /// The size `name` spells, case-insensitively, or [#MEDIUM] for anything
    /// else: a document that misspells it should still be readable.
    public static ReadingSize named(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "small", "s" -> SMALL;
            case "large", "l" -> LARGE;
            default -> MEDIUM;
        };
    }
}
