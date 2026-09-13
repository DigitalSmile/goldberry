package io.github.digitalsmile.goldberry.natives.md4c.enums;

/// An inline span — `MD_SPANTYPE`.
public enum SpanType implements Md4cEnum {

    /// `*emphasis*`.
    EM(0, "MD_SPAN_EM"),

    /// `**strong**`.
    STRONG(1, "MD_SPAN_STRONG"),

    /// A link. Detail: [io.github.digitalsmile.goldberry.natives.md4c.SpanDetail.Link].
    A(2, "MD_SPAN_A"),

    /// An image. Detail: [io.github.digitalsmile.goldberry.natives.md4c.SpanDetail.Image];
    /// its alt text arrives as the text events inside the span.
    IMG(3, "MD_SPAN_IMG"),

    /// `` `code` ``.
    CODE(4, "MD_SPAN_CODE"),

    /// `~~struck through~~`. Needs [MarkdownFlag#STRIKETHROUGH].
    DEL(5, "MD_SPAN_DEL"),

    /// `$x$`. Needs [MarkdownFlag#LATEX_MATH].
    LATEXMATH(6, "MD_SPAN_LATEXMATH"),

    /// `$$x$$`. Needs [MarkdownFlag#LATEX_MATH].
    LATEXMATH_DISPLAY(7, "MD_SPAN_LATEXMATH_DISPLAY"),

    /// `[[target]]`. Needs [MarkdownFlag#WIKI_LINKS]. Detail:
    /// [io.github.digitalsmile.goldberry.natives.md4c.SpanDetail.WikiLink].
    WIKILINK(8, "MD_SPAN_WIKILINK"),

    /// `_underlined_`, when [MarkdownFlag#UNDERLINE] has taken `_` away from
    /// emphasis.
    U(9, "MD_SPAN_U");

    private final int nativeValue;
    private final String nativeName;

    SpanType(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    /// The span md4c reported, by its C value.
    ///
    /// @throws IllegalArgumentException if no span has that value
    public static SpanType of(int nativeValue) {
        for (var type : values()) {
            if (type.nativeValue == nativeValue) {
                return type;
            }
        }
        throw new IllegalArgumentException("libgoldberry reported MD_SPANTYPE " + nativeValue
                + ", which these bindings do not know; md4c has gained a span type");
    }
}
