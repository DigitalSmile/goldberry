package io.github.digitalsmile.goldberry.natives.md4c.enums;

/// An inline span — `MD_SPANTYPE`.
///
/// The values are md4c 0.6.0's, and they have a gap: 0.6.0 inserted `MD_SPAN_INS`
/// at 5, between [#CODE] and [#DEL], and moved every span after it up by one.
/// `MD_SPAN_INS` is not bound here — md4c emits it only under `MD_FLAG_INSERT`,
/// which [MarkdownFlag] does not offer — so 5 stays unclaimed rather than taken by
/// a constant nothing can produce. The same goes for the spans 0.6.0 appended
/// (`MD_SPAN_SPOILER` onwards): each needs a flag these bindings do not expose.
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
    DEL(6, "MD_SPAN_DEL"),

    /// `$x$`. Needs [MarkdownFlag#LATEX_MATH].
    LATEXMATH(7, "MD_SPAN_LATEXMATH"),

    /// `$$x$$`. Needs [MarkdownFlag#LATEX_MATH].
    LATEXMATH_DISPLAY(8, "MD_SPAN_LATEXMATH_DISPLAY"),

    /// `[[target]]`. Needs [MarkdownFlag#WIKI_LINKS]. Detail:
    /// [io.github.digitalsmile.goldberry.natives.md4c.SpanDetail.WikiLink].
    WIKILINK(9, "MD_SPAN_WIKILINK"),

    /// `_underlined_`, when [MarkdownFlag#UNDERLINE] has taken `_` away from
    /// emphasis.
    U(10, "MD_SPAN_U");

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
