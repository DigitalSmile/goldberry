package dev.goldberry.css.image;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.background.GradientLayer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.image.ImageAddress;

/// A picture a stylesheet names: CSS's `<image>`.
///
/// ```css
/// .panel { background-image: url("classpath:/ui/leather.png"), linear-gradient(#0000, #0008) }
/// button { border-image-source: url("classpath:/ui/button.png") }
/// ```
///
/// Sealed, with two shapes. A [GradientLayer] is drawn from its own numbers once
/// the box has a size. A [Url] is an address, and the pixels behind it are
/// [StyleImages]'s to find: they are decoded off the frame, and a layer whose
/// picture has not arrived draws nothing until it has.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public sealed interface CssImage permits CssImage.Url, GradientLayer {

    /// This image with its alpha scaled by `alpha`, which is how `opacity`
    /// reaches it.
    CssImage fade(double alpha);

    /// `url("…")`: a picture by its address.
    ///
    /// The address is resolved the way an `image`'s `src` is. `classpath:` names
    /// a resource on the application's class loader, and anything else is a
    /// file. A sheet read with
    /// [dev.goldberry.css.Stylesheet#resource]
    /// resolves a plain name against itself instead, as a resource beside the
    /// sheet. On a display above 100% the picture's `@2x` variant is drawn
    /// when there is one: `panel.png` is `panel@2x.png` there, with the same
    /// natural size and twice the pixels.
    ///
    /// A `#xywh=x,y,width,height` fragment is that rectangle of the picture, in
    /// the 1x picture's pixels, so a sheet of sprites serves every box from one
    /// decode.
    ///
    /// Only the quoted form is read: `url("ui/panel.png")`. CSS's unquoted
    /// `url(ui/panel.png)` is a token of its own, and the tokenizer does not
    /// make it.
    ///
    /// @param href  the address as written, fragment and all
    /// @param alpha how strongly it is drawn, 0 to 1: what `opacity` leaves of it
    record Url(String href, double alpha) implements CssImage {

        public Url {
            Objects.requireNonNull(href, "href");
            if (!(alpha >= 0 && alpha <= 1)) {
                throw new IllegalArgumentException("an image's alpha is between 0 and 1, not " + alpha);
            }
            // Read once here, so an address whose fragment cannot be read is a
            // value that never exists rather than a failure at paint.
            var _ = ImageAddress.parse(href);
        }

        /// A picture drawn at full strength.
        public Url(String href) {
            this(href, 1);
        }

        /// Where the picture is, and the rectangle of it.
        public ImageAddress address() {
            return ImageAddress.parse(href);
        }

        @Override
        public Url fade(double alpha) {
            return alpha >= 1 ? this : new Url(href, this.alpha * Math.max(0, alpha));
        }

        @Override
        public String toString() {
            return "url(\"" + href + "\")" + (alpha < 1 ? " at " + alpha : "");
        }
    }

    /// One `url("…")`, or null when `part` is not one this reads — the unquoted
    /// form, a `url()` with more than a string in it, or an address whose
    /// `#xywh=` fragment is malformed.
    static @Nullable Url url(List<Token> part) {
        var tokens =
                part.stream().filter(token -> !token.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 3
                || !tokens.getFirst().is(TokenType.FUNCTION)
                || !tokens.getFirst().text().toLowerCase(Locale.ROOT).equals("url")
                || !tokens.get(1).is(TokenType.STRING)
                || !tokens.getLast().is(TokenType.CLOSE_PAREN)) {
            return null;
        }
        var href = tokens.get(1).text();
        if (href.isBlank()) {
            return null;
        }
        try {
            return new Url(href);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
