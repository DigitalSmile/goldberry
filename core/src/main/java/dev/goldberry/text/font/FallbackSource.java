package dev.goldberry.text.font;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/// A face searched for the characters the face a stylesheet chose has no glyph
/// for.
///
/// ```java
/// @Override public List<FallbackSource> fallbacks() {
///     return List.of(
///             FallbackSource.of(FontSource.stream("Noto Sans CJK SC", 400, Style.UPRIGHT,
///                     () -> MyApp.class.getResourceAsStream("fonts/NotoSansCJKsc-Regular.otf")),
///                     UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA),
///             FallbackSource.of(FontSource.stream("Noto Sans Arabic", 400, Style.UPRIGHT,
///                     () -> MyApp.class.getResourceAsStream("fonts/NotoSansArabic-Regular.ttf")),
///                     UnicodeScript.ARABIC));
/// }
/// ```
///
/// A fallback is not a family a stylesheet names. `font-family: Inter` stays
/// Inter for every character Inter has, and a character it lacks is drawn in
/// the first fallback whose face has it, at the same size; a name typed in
/// Han, Arabic or mathematical letters draws as letters rather than as boxes.
///
/// **The face's `cmap` decides.** Whether a fallback draws a character is read
/// from the face itself, once, when it is opened. `scripts` is a hint the book
/// reads before that: a fallback whose scripts are named is not opened for a
/// character of another script, so an Arabic name never parses a CJK face. A
/// character Unicode puts in no script of its own (punctuation, digits, the
/// mathematical alphanumerics) is never ruled out by a hint, and an empty set
/// rules nothing out.
///
/// Several weights or styles of one family are several sources with one
/// family name. The book takes the one nearest the weight and style of the
/// face it stands in for, by the rule a stylesheet's family is matched by, and
/// draws nothing synthetically: bold text in a family shipped only in regular
/// falls back to the regular.
///
/// Read more:
/// [Faces, fonts and the book](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
///
/// @param face    the file, named and weighted as a shipped face is
/// @param scripts the scripts it is opened for, or empty for any
public record FallbackSource(FontSource face, Set<Character.UnicodeScript> scripts) {

    public FallbackSource {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(scripts, "scripts");
        scripts = scripts.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(scripts));
    }

    /// A fallback opened only for characters of `scripts`, or for any character
    /// when none are named.
    public static FallbackSource of(FontSource face, Character.UnicodeScript... scripts) {
        Objects.requireNonNull(scripts, "scripts");
        return new FallbackSource(face, scripts.length == 0 ? Set.of() : EnumSet.copyOf(Arrays.asList(scripts)));
    }

    /// Whether the hint leaves this face worth opening for `codePoint`.
    ///
    /// Not whether it covers it: that is the face's `cmap`, read only once the
    /// face is open.
    public boolean mayCover(int codePoint) {
        if (scripts.isEmpty()) {
            return true;
        }
        var script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.COMMON
                || script == Character.UnicodeScript.INHERITED
                || script == Character.UnicodeScript.UNKNOWN
                || scripts.contains(script);
    }
}
