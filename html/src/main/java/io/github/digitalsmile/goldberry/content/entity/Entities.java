package io.github.digitalsmile.goldberry.content.entity;

import io.github.digitalsmile.goldberry.natives.md4c.Md4c;

/// Turns `&amp;` into `&`.
///
/// md4c marks an entity and hands it over as written, because whether it should be
/// resolved depends on where it is going: HTML wants `&amp;` left exactly as it is,
/// and a reader wants an ampersand. The model holds resolved text — a
/// [io.github.digitalsmile.goldberry.markdown.model.Text] node is what a person
/// reads — so this is where the resolving happens, once, on the way in.
///
/// **The named table is md4c's.** All 2125 of them, through one exported symbol, for
/// ADR-0010's reason: a copy of somebody else's table is a copy that drifts, and
/// this one is data rather than code so nothing would notice. Numeric references are
/// resolved here, because they are arithmetic rather than a table.
public final class Entities {

    private Entities() {}

    /// The character CommonMark says an unresolvable reference becomes.
    private static final String REPLACEMENT = "�";

    /// How far a `&` may be from its `;` before [#resolveAll] stops looking.
    ///
    /// The longest reference in HTML5 is `&CounterClockwiseContourIntegral;` at 33
    /// characters. Anything longer is prose with an ampersand in it, and scanning to
    /// the end of a paragraph for a semicolon would make `Q&A, and then` a failed
    /// entity every time somebody wrote it.
    private static final int LONGEST = 34;

    /// Every reference in `text`, resolved where it is one.
    ///
    /// **HTML needs this and Markdown does not**, which is why it arrived with
    /// `html-view` rather than with the parser above it: md4c *marks* an entity, so the
    /// Markdown side is handed `&amp;` already cut out of the prose and calls
    /// [#resolve] on it. An HTML tokenizer has no such help — a run of text is a run of
    /// text, and the ampersands in it are found here (ADR-0298).
    ///
    /// Text with no `&` in it comes straight back, which is almost every run: the scan
    /// below never starts.
    public static String resolveAll(String text) {
        var first = text.indexOf('&');
        if (first < 0) {
            return text;
        }
        var out = new StringBuilder(text.length());
        out.append(text, 0, first);
        var at = first;
        while (at < text.length()) {
            var c = text.charAt(at);
            if (c != '&') {
                out.append(c);
                at++;
                continue;
            }
            var semicolon = text.indexOf(';', at + 1);
            var reference = semicolon < 0 || semicolon - at > LONGEST ? null : text.substring(at, semicolon + 1);
            var resolved = reference == null ? null : resolve(reference);
            if (resolved == null || resolved.equals(reference)) {
                // A bare ampersand, or a `&nope;` that names nothing. Left as it is
                // rather than replaced: `Q&A` is two letters and an ampersand, every
                // browser agrees, and the scan resumes *inside* the run so that
                // `&nope;&amp;` still finds the second one.
                out.append(c);
                at++;
                continue;
            }
            out.append(resolved);
            at = semicolon + 1;
        }
        return out.toString();
    }

    /// Resolves `reference` — `&` and `;` included — or hands it back unchanged.
    ///
    /// Unchanged rather than dropped or replaced, because `&nope;` is not an entity
    /// and CommonMark says it is then literal text. A reader who typed it sees what
    /// they typed.
    public static String resolve(String reference) {
        if (reference.length() < 3 || reference.charAt(0) != '&' || !reference.endsWith(";")) {
            return reference;
        }
        if (reference.charAt(1) == '#') {
            return numeric(reference);
        }
        var codepoints = Md4c.get().entity(reference);
        if (codepoints.length == 0) {
            return reference;
        }
        var out = new StringBuilder(2);
        for (var codepoint : codepoints) {
            out.appendCodePoint(codepoint);
        }
        return out.toString();
    }

    /// `&#38;` and `&#x26;`.
    ///
    /// A reference outside Unicode, or one that names NUL, becomes U+FFFD — which is
    /// CommonMark's rule, and also the only answer that keeps a document from
    /// carrying a NUL into a widget's text.
    private static String numeric(String reference) {
        var body = reference.substring(2, reference.length() - 1);
        var hex = !body.isEmpty() && (body.charAt(0) == 'x' || body.charAt(0) == 'X');
        var digits = hex ? body.substring(1) : body;
        if (digits.isEmpty()) {
            return reference;
        }
        int codepoint;
        try {
            codepoint = Integer.parseInt(digits, hex ? 16 : 10);
        } catch (NumberFormatException e) {
            // Not a number at all: `&#eleven;` is literal text, not an error.
            return reference;
        }
        // The surrogate range is compared as an int rather than through
        // `Character.isSurrogate`, which takes a `char` -- casting first would
        // truncate U+1D800 into a surrogate and replace a perfectly good character.
        var surrogate = codepoint >= 0xD800 && codepoint <= 0xDFFF;
        if (codepoint <= 0 || codepoint > Character.MAX_CODE_POINT || surrogate) {
            return REPLACEMENT;
        }
        return new String(Character.toChars(codepoint));
    }
}
