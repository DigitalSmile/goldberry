package io.github.digitalsmile.goldberry.widgets.form.textinput;

/// What a `password` field draws instead of what it holds, and how to get back.
///
/// One bullet per **code point**, which is what every platform does — a masked
/// field must not leak how many characters a password has beyond how many
/// symbols it has, and it must not draw two bullets for one emoji.
///
/// ## Why there is a mapping and not just a string
///
/// The caret, the selection and a click are all offsets, and a masked field draws
/// something whose offsets are not the real text's: `🎨ab` is three code points
/// and four `char`s, so display offset 2 is real offset 3. Every geometry
/// question is asked about the display and every edit is applied to the real
/// text, so something has to convert, and doing it by arithmetic at each call
/// site is how a password field ends up deleting the wrong character.
///
/// [#NONE] is the identity, so an ordinary field pays for none of this.
///
/// @param display   what to draw
/// @param toReal    `toReal[i]` is the real offset of display offset `i`, with
///                  one more entry than `display` has characters
/// @param toDisplay `toDisplay[i]` is the display offset of real offset `i`, with
///                  one more entry than the real text has characters
record Mask(String display, int[] toReal, int[] toDisplay) {

    /// The character a masked field draws.
    ///
    /// `U+2022 BULLET`, which is what macOS and Windows use. Not an asterisk:
    /// an asterisk is a footnote marker and sits on the cap line, so a row of
    /// them reads as superscript.
    private static final char BULLET = '•';

    /// The mask for `text`, or the identity when `masked` is false.
    static Mask of(String text, boolean masked) {
        if (!masked) {
            return identity(text);
        }
        var bullets = new StringBuilder();
        var toReal = new java.util.ArrayList<Integer>();
        var toDisplay = new int[text.length() + 1];
        var real = 0;
        while (real < text.length()) {
            toDisplay[real] = bullets.length();
            var codePoint = text.codePointAt(real);
            var width = Character.charCount(codePoint);
            // Every char of a surrogate pair maps to the bullet that stands in
            // for it, so an offset that somehow landed inside one still converts
            // to something legal rather than to the next character's place.
            for (var i = 1; i < width; i++) {
                toDisplay[real + i] = bullets.length();
            }
            toReal.add(real);
            bullets.append(BULLET);
            real += width;
        }
        toDisplay[text.length()] = bullets.length();
        toReal.add(text.length());
        return new Mask(bullets.toString(),
                toReal.stream().mapToInt(Integer::intValue).toArray(), toDisplay);
    }

    private static Mask identity(String text) {
        var straight = new int[text.length() + 1];
        for (var i = 0; i <= text.length(); i++) {
            straight[i] = i;
        }
        // The same array for both directions: for unmasked text the map is the
        // identity in either direction, and two copies of it could only ever
        // disagree by being edited separately.
        return new Mask(text, straight, straight);
    }

    /// The display offset that stands for real offset `offset`.
    int display(int offset) {
        return toDisplay[Math.clamp(offset, 0, toDisplay.length - 1)];
    }

    /// The real offset that display offset `offset` stands for.
    int real(int offset) {
        return toReal[Math.clamp(offset, 0, toReal.length - 1)];
    }

    /// `edit`'s caret and selection, expressed in display offsets — what
    /// [TextField] draws against.
    TextEdit displayed(TextEdit edit) {
        return new TextEdit(display, display(edit.anchor()), display(edit.caret()));
    }
}
