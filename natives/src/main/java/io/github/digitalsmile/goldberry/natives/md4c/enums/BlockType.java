package io.github.digitalsmile.goldberry.natives.md4c.enums;

/// A block in a Markdown document — `MD_BLOCKTYPE`.
///
/// Blocks nest: a [#DOC] holds [#P]s, [#QUOTE]s and lists, a [#LI] holds whatever
/// a paragraph can. What each one carries beside its type is
/// [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail].
public enum BlockType implements Md4cEnum {

    /// The document itself. Exactly one, enclosing everything.
    DOC(0, "MD_BLOCK_DOC"),

    /// `> quoted`.
    QUOTE(1, "MD_BLOCK_QUOTE"),

    /// A bullet list. Detail: [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.BulletList].
    UL(2, "MD_BLOCK_UL"),

    /// A numbered list. Detail: [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.NumberedList].
    OL(3, "MD_BLOCK_OL"),

    /// One item of either list. Detail: [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.Item].
    LI(4, "MD_BLOCK_LI"),

    /// A thematic break — `---`.
    HR(5, "MD_BLOCK_HR"),

    /// A heading. Detail: [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.Heading].
    H(6, "MD_BLOCK_H"),

    /// A fenced or indented code block. Detail:
    /// [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.Code].
    CODE(7, "MD_BLOCK_CODE"),

    /// Raw HTML, passed through verbatim. Suppressed by
    /// [MarkdownFlag#NO_HTML_BLOCKS].
    HTML(8, "MD_BLOCK_HTML"),

    /// A paragraph.
    P(9, "MD_BLOCK_P"),

    /// A table. Needs [MarkdownFlag#TABLES], as do the five below.
    TABLE(10, "MD_BLOCK_TABLE"),

    THEAD(11, "MD_BLOCK_THEAD"),

    TBODY(12, "MD_BLOCK_TBODY"),

    TR(13, "MD_BLOCK_TR"),

    /// A header cell. Detail: [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.Cell].
    TH(14, "MD_BLOCK_TH"),

    /// A body cell. Detail: [io.github.digitalsmile.goldberry.natives.md4c.BlockDetail.Cell].
    TD(15, "MD_BLOCK_TD");

    private final int nativeValue;
    private final String nativeName;

    BlockType(int nativeValue, String nativeName) {
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

    /// The block md4c reported, by its C value.
    ///
    /// @throws IllegalArgumentException if no block has that value, which means the
    ///         library is newer than these bindings rather than that the document
    ///         was odd
    public static BlockType of(int nativeValue) {
        for (var type : values()) {
            if (type.nativeValue == nativeValue) {
                return type;
            }
        }
        throw new IllegalArgumentException("libgoldberry reported MD_BLOCKTYPE " + nativeValue
                + ", which these bindings do not know; md4c has gained a block type");
    }
}
