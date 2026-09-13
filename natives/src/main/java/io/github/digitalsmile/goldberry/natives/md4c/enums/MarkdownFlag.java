package io.github.digitalsmile.goldberry.natives.md4c.enums;

import java.util.Set;

/// One bit of md4c's dialect mask — `MD_FLAG_*`.
///
/// A flag is not an enumerator, and the failure is quieter than a wrong
/// enumerator's: a mask missing [#TABLES] is a table rendered as a paragraph full
/// of pipe characters, which looks like a document somebody wrote badly rather
/// than like a parser configured wrongly. So these are verified against the
/// compiled library exactly as the enums are.
///
/// The names are md4c's meanings spelled out: `MD_FLAG_PERMISSIVEATXHEADERS`
/// becomes [#PERMISSIVE_ATX_HEADERS]. What an application chooses from is
/// `MarkdownExtension` in `:html`, which is a smaller list — this is the whole
/// surface md4c offers, and translating one into the other is the seam ADR-0294
/// puts between a binding and a vocabulary.
public enum MarkdownFlag implements Md4cEnum {

    /// Collapse runs of whitespace in ordinary text into one space.
    COLLAPSE_WHITESPACE(0x0001, "MD_FLAG_COLLAPSEWHITESPACE"),

    /// Allow `###heading` with no space after the hashes.
    PERMISSIVE_ATX_HEADERS(0x0002, "MD_FLAG_PERMISSIVEATXHEADERS"),

    /// Recognise a bare URL as a link.
    PERMISSIVE_URL_AUTOLINKS(0x0004, "MD_FLAG_PERMISSIVEURLAUTOLINKS"),

    /// Recognise a bare e-mail address as a link.
    PERMISSIVE_EMAIL_AUTOLINKS(0x0008, "MD_FLAG_PERMISSIVEEMAILAUTOLINKS"),

    /// Recognise `www.example.com`, with no scheme at all, as a link.
    PERMISSIVE_WWW_AUTOLINKS(0x0400, "MD_FLAG_PERMISSIVEWWWAUTOLINKS"),

    /// Take indented code blocks away, leaving only fenced ones.
    NO_INDENTED_CODE_BLOCKS(0x0010, "MD_FLAG_NOINDENTEDCODEBLOCKS"),

    /// Refuse raw HTML blocks.
    NO_HTML_BLOCKS(0x0020, "MD_FLAG_NOHTMLBLOCKS"),

    /// Refuse raw inline HTML.
    NO_HTML_SPANS(0x0040, "MD_FLAG_NOHTMLSPANS"),

    /// `| a | b |` tables.
    TABLES(0x0100, "MD_FLAG_TABLES"),

    /// `~~struck through~~`.
    STRIKETHROUGH(0x0200, "MD_FLAG_STRIKETHROUGH"),

    /// `- [x] done`.
    TASK_LISTS(0x0800, "MD_FLAG_TASKLISTS"),

    /// `$x$` and `$$x$$`.
    LATEX_MATH(0x1000, "MD_FLAG_LATEXMATHSPANS"),

    /// `[[target]]`.
    WIKI_LINKS(0x2000, "MD_FLAG_WIKILINKS"),

    /// `_this_` underlines rather than emphasises.
    UNDERLINE(0x4000, "MD_FLAG_UNDERLINE"),

    /// Every soft break acts as a hard one — a newline in the source is a newline
    /// on the screen.
    HARD_SOFT_BREAKS(0x8000, "MD_FLAG_HARD_SOFT_BREAKS");

    private final int bit;
    private final String nativeName;

    MarkdownFlag(int bit, String nativeName) {
        this.bit = bit;
        this.nativeName = nativeName;
    }

    /// This flag's bit.
    public int bit() {
        return bit;
    }

    @Override
    public int nativeValue() {
        return bit;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    /// The mask `flags` add up to, which is what
    /// [io.github.digitalsmile.goldberry.natives.md4c.Md4c#parse] takes.
    public static int mask(Set<MarkdownFlag> flags) {
        var mask = 0;
        for (var flag : flags) {
            mask |= flag.bit;
        }
        return mask;
    }
}
