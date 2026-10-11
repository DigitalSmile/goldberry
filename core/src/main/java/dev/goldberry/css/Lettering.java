package dev.goldberry.css;

import java.util.List;
import java.util.Objects;

import dev.goldberry.css.value.Shadow;
import dev.goldberry.text.flow.TextJustify;
import dev.goldberry.text.flow.TextTransform;

/// How the letters of a text are set, beside the face that draws them:
/// `letter-spacing`, `word-spacing`, `text-transform`, `text-shadow` and
/// `text-justify`.
///
/// ```css
/// overline { letter-spacing: 0.08em; text-transform: uppercase }
/// ```
///
/// Part of [Typography], because every one of these inherits, as the font
/// does, and is read by the same thing: what shapes a paragraph. The two
/// spacings are in logical pixels, resolved against the element's own font
/// size when the style is computed, so a child inherits the pixels its parent
/// computed, as CSS says.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#typography).
///
/// @param letterSpacing added after every character, negative to tighten;
///                      zero is `normal`
/// @param wordSpacing   added to every space between words; zero is `normal`
/// @param transform     the case the text is drawn in
/// @param shadows       the `text-shadow` list, first on top; empty for none
/// @param justify       where a justified line takes its room
public record Lettering(
        double letterSpacing, double wordSpacing, TextTransform transform, List<Shadow> shadows, TextJustify justify) {

    /// Nothing added and nothing changed: `normal`, `normal`, `none`, `none`
    /// and `auto`.
    public static final Lettering NONE = new Lettering(0, 0, TextTransform.NONE, List.of(), TextJustify.AUTO);

    public Lettering {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(justify, "justify");
        shadows = List.copyOf(Objects.requireNonNull(shadows, "shadows"));
        if (!Double.isFinite(letterSpacing) || !Double.isFinite(wordSpacing)) {
            throw new IllegalArgumentException(
                    "letter and word spacing are finite lengths, not " + letterSpacing + " and " + wordSpacing);
        }
    }

    /// This, with `letter-spacing` at `value`.
    public Lettering letterSpacing(double value) {
        return new Lettering(value, wordSpacing, transform, shadows, justify);
    }

    /// This, with `word-spacing` at `value`.
    public Lettering wordSpacing(double value) {
        return new Lettering(letterSpacing, value, transform, shadows, justify);
    }

    /// This, with `text-transform` at `value`.
    public Lettering transform(TextTransform value) {
        return new Lettering(letterSpacing, wordSpacing, value, shadows, justify);
    }

    /// This, with `text-shadow` at `value`.
    public Lettering shadows(List<Shadow> value) {
        return new Lettering(letterSpacing, wordSpacing, transform, value, justify);
    }

    /// This, with `text-justify` at `value`.
    public Lettering justify(TextJustify value) {
        return new Lettering(letterSpacing, wordSpacing, transform, shadows, value);
    }

    /// This with every length multiplied by `factor`, for the text-scale token.
    Lettering scaled(double factor) {
        return factor == 1
                ? this
                : new Lettering(letterSpacing * factor, wordSpacing * factor, transform, shadows, justify);
    }

    /// Whether this adds nothing to what the face sets and changes no case.
    public boolean isNone() {
        return letterSpacing == 0
                && wordSpacing == 0
                && transform == TextTransform.NONE
                && shadows.isEmpty()
                && justify == TextJustify.AUTO;
    }
}
