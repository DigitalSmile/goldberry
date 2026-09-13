package io.github.digitalsmile.goldberry.html.model;

import java.util.Locale;
import java.util.Set;

/// What HTML says about a tag, for the two readers that have to agree about it.
///
/// The parser needs to know that `br` never has children and that `script` holds a
/// program rather than markup; the renderer needs to know that `em` flows along a
/// line and `div` stacks down the page. Those are the *same* facts, and the reason
/// this is a class rather than two private tables is that the two answering
/// differently is a specific, silent bug: an element the parser stacked and the
/// renderer laid out inline comes out with its words in the wrong paragraph.
///
/// **Exported**, because an application walking a [HtmlDocument] wants them too — a
/// summary that stops at the first block, a linter that refuses a `div` inside a `p`
/// — and because a second copy of a table like this drifts (ADR-0010's rule, applied
/// to a list of tag names rather than to an entity table).
///
/// The lists are HTML5's, cut to what a **content** renderer meets: there is nothing
/// here about `template`, `slot` or the SVG and MathML namespaces, because
/// `docs/content-widgets.md` §1 is about authored documents and not about
/// applications written in a browser.
public final class Tags {

    private Tags() {}

    /// The elements that never have children — the tag *is* the element.
    ///
    /// A close tag for one of these is a mistake an author makes and this parser
    /// ignores rather than reports, which is HTML's own answer: `</br>` has no meaning
    /// and a renderer that threw would fail a page over a typo.
    private static final Set<String> VOID = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track",
            "wbr");

    /// The elements whose content is **not** markup.
    ///
    /// A `<` inside a `script` is a less-than sign, so the tokenizer reads to the
    /// matching close tag and hands over the text untouched. `html-view` draws
    /// neither: a stylesheet is not content, and a program is certainly not — there is
    /// no scripting here and there never will be (`docs/content-widgets.md` §1.5).
    private static final Set<String> RAW_TEXT = Set.of("script", "style");

    /// The elements that flow along a line.
    ///
    /// Everything else is treated as a block, which is the right default for an
    /// unknown tag: a `<my-callout>` full of paragraphs stacks, and a `<my-badge>`
    /// inside a sentence is the case an author can fix with one `span`. The reverse
    /// default would put a page's sections side by side.
    private static final Set<String> INLINE = Set.of(
            "a", "abbr", "b", "bdi", "bdo", "br", "cite", "code", "data", "del", "dfn", "em", "i", "img", "ins", "kbd",
            "mark", "q", "s", "samp", "small", "span", "strong", "sub", "sup", "time", "u", "var", "wbr");

    /// The elements whose whitespace is content.
    private static final Set<String> PREFORMATTED = Set.of("pre", "textarea");

    /// The elements a content renderer draws nothing for.
    ///
    /// Not the same question as [#isRawText]: a `head` is ordinary markup and its
    /// content is *metadata*, so it is skipped for what it means rather than for how
    /// it is lexed. A `title` is the window's business and an application reads it
    /// off the model.
    private static final Set<String> METADATA = Set.of("head", "title", "base", "link", "meta", "script", "style");

    /// Whether `tag` is one of the elements that cannot have children.
    public static boolean isVoid(String tag) {
        return VOID.contains(lower(tag));
    }

    /// Whether `tag`'s content is text rather than markup.
    public static boolean isRawText(String tag) {
        return RAW_TEXT.contains(lower(tag));
    }

    /// Whether `tag` flows along a line rather than stacking down the page.
    public static boolean isInline(String tag) {
        return INLINE.contains(lower(tag));
    }

    /// Whether `tag` stacks down the page — every tag that is not inline, including
    /// every tag nobody has heard of.
    public static boolean isBlock(String tag) {
        return !isInline(tag);
    }

    /// Whether `tag` keeps the author's own spacing.
    public static boolean isPreformatted(String tag) {
        return PREFORMATTED.contains(lower(tag));
    }

    /// Whether `tag` is metadata rather than content — nothing to draw.
    public static boolean isMetadata(String tag) {
        return METADATA.contains(lower(tag));
    }

    /// `1` to `6` for `h1` to `h6`, and 0 for anything else.
    ///
    /// A number rather than a boolean, because every caller that wants to know whether
    /// something is a heading wants to know *which*: a table of contents nests by it
    /// and a stylesheet sizes by it.
    public static int headingLevel(String tag) {
        var name = lower(tag);
        if (name.length() != 2 || name.charAt(0) != 'h') {
            return 0;
        }
        var digit = name.charAt(1);
        return digit >= '1' && digit <= '6' ? digit - '0' : 0;
    }

    private static String lower(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }
}
