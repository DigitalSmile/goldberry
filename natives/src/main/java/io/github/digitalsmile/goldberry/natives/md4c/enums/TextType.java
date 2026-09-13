package io.github.digitalsmile.goldberry.natives.md4c.enums;

/// What a run of text is — `MD_TEXTTYPE`.
///
/// md4c does not resolve entities or decide what a break means: it says which kind
/// of run this is and hands over the bytes. Whoever consumes the stream decides,
/// and the two consumers decide differently — HTML passes an [#ENTITY] through
/// verbatim because `&amp;` is already correct HTML, while a widget resolves it to
/// `&` because a reader cannot.
public enum TextType implements Md4cEnum {

    /// Ordinary text.
    NORMAL(0, "MD_TEXT_NORMAL"),

    /// A NUL in the input. Rendered as U+FFFD, which is what CommonMark requires.
    NULLCHAR(1, "MD_TEXT_NULLCHAR"),

    /// A hard break — two trailing spaces, or a backslash. Carries the newline
    /// itself, so a consumer that ignores the type still emits something.
    BR(2, "MD_TEXT_BR"),

    /// A newline in the source that means nothing to the document. Also carries it.
    SOFTBR(3, "MD_TEXT_SOFTBR"),

    /// An entity reference, `&` and `;` included, exactly as written.
    ENTITY(4, "MD_TEXT_ENTITY"),

    /// Text inside a code block or a code span, indentation and newlines included.
    CODE(5, "MD_TEXT_CODE"),

    /// Raw HTML, to be passed through rather than escaped.
    HTML(6, "MD_TEXT_HTML"),

    /// Text inside a LaTeX math span.
    LATEXMATH(7, "MD_TEXT_LATEXMATH");

    private final int nativeValue;
    private final String nativeName;

    TextType(int nativeValue, String nativeName) {
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

    /// The run md4c reported, by its C value.
    ///
    /// @throws IllegalArgumentException if no kind has that value
    public static TextType of(int nativeValue) {
        for (var type : values()) {
            if (type.nativeValue == nativeValue) {
                return type;
            }
        }
        throw new IllegalArgumentException("libgoldberry reported MD_TEXTTYPE " + nativeValue
                + ", which these bindings do not know; md4c has gained a text type");
    }
}
