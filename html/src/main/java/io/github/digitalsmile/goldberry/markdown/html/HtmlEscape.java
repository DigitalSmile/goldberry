package io.github.digitalsmile.goldberry.markdown.html;

/// Escaping, in the two dialects HTML needs.
///
/// Two rather than one, and the difference is a real defect when it is missed: text
/// content has to escape `&`, `<` and `>`, while an attribute value has to escape
/// `"` as well — a title containing a quotation mark would otherwise end the
/// attribute and turn the rest of the document into markup.
final class HtmlEscape {

    private HtmlEscape() {}

    /// `&`, `<` and `>` — what goes between tags.
    ///
    /// `'` is deliberately not escaped: this writer quotes every attribute with `"`,
    /// so an apostrophe in text or in a title is safe, and `&#39;` in prose is noise
    /// in the output a reader may well be reading.
    static void text(StringBuilder out, String text) {
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
    }

    /// The same, plus `"`, for the inside of a quoted attribute.
    static void attribute(StringBuilder out, String value) {
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
    }
}
