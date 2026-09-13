package io.github.digitalsmile.goldberry.markdown;

import java.util.Set;

import io.github.digitalsmile.goldberry.natives.md4c.enums.MarkdownFlag;

/// A departure from plain CommonMark that a document may be parsed with.
///
/// Fewer of these than md4c has flags, on purpose. md4c offers fifteen bits, some of
/// which are three spellings of one idea and some of which describe an engine this
/// module does not have; what is here is the set an application chooses between,
/// each named for what it means rather than for how md4c spells it. The translation
/// is [#flags()], and it is the only place in this module that mentions md4c at all
/// (ADR-0294).
///
/// What is deliberately **not** here:
///
/// - **LaTeX math.** md4c will find `$x$` for you, and then there is nothing in the
///   toolkit that can set an equation. Offering the extension would mean a model
///   node that every renderer had to drop.
/// - **Whitespace collapsing.** The HTML output leaves it to the browser, which
///   collapses it by specification, and `markdown-view` lays out words with a gap
///   between them, so a run of spaces has already gone. A flag whose two consumers
///   both ignore it is a flag that lies.
public enum MarkdownExtension {

    /// `| a | b |` tables, with a `|:--|--:|` row deciding alignment.
    TABLES(Set.of(MarkdownFlag.TABLES)),

    /// `~~struck through~~`.
    STRIKETHROUGH(Set.of(MarkdownFlag.STRIKETHROUGH)),

    /// `- [x] done` check boxes.
    TASK_LISTS(Set.of(MarkdownFlag.TASK_LISTS)),

    /// A bare URL, e-mail address or `www.` host is a link with no brackets round it.
    ///
    /// md4c's three permissive-autolink bits together: an application that wants
    /// one of them wants all three, and a note-taking application that wanted
    /// `http://` links but not e-mail ones would be an odd thing to be.
    AUTOLINKS(Set.of(
            MarkdownFlag.PERMISSIVE_URL_AUTOLINKS,
            MarkdownFlag.PERMISSIVE_EMAIL_AUTOLINKS,
            MarkdownFlag.PERMISSIVE_WWW_AUTOLINKS)),

    /// `[[target]]` and `[[target|label]]` wiki links.
    ///
    /// The link is **not resolved**: a target is a string, and what it names is the
    /// application's to know — which is the whole reason a note application wants
    /// this rather than an `href`.
    WIKI_LINKS(Set.of(MarkdownFlag.WIKI_LINKS)),

    /// `_this_` underlines instead of emphasising, leaving `*this*` for emphasis.
    UNDERLINE(Set.of(MarkdownFlag.UNDERLINE)),

    /// Every newline is a line break, as it is in a chat message and in a comment
    /// box — the one setting that makes Markdown behave the way someone who has
    /// never heard of Markdown expects.
    HARD_LINE_BREAKS(Set.of(MarkdownFlag.HARD_SOFT_BREAKS)),

    /// `###heading` with no space after the hashes counts as a heading.
    LENIENT_HEADINGS(Set.of(MarkdownFlag.PERMISSIVE_ATX_HEADERS)),

    /// Four leading spaces is not a code block: only a fence is.
    ///
    /// For documents written in a text box, where an indented line is usually a
    /// mistake rather than a program.
    NO_INDENTED_CODE(Set.of(MarkdownFlag.NO_INDENTED_CODE_BLOCKS)),

    /// Raw HTML is text rather than markup — no
    /// [io.github.digitalsmile.goldberry.markdown.model.HtmlBlock] and no
    /// [io.github.digitalsmile.goldberry.markdown.model.RawHtml] in the model.
    ///
    /// For a document that came from somewhere the application does not control. It
    /// is **not** a sanitiser and does not pretend to be: it stops md4c recognising
    /// HTML at all, so what reaches the output is escaped text.
    NO_HTML(Set.of(MarkdownFlag.NO_HTML_BLOCKS, MarkdownFlag.NO_HTML_SPANS));

    /// `Set.of(...)` is immutable and Error Prone cannot see that through the
    /// interface, which is the whole of this suppression: the alternative was
    /// storing a bitmask `int` here and moving the translation somewhere less
    /// obvious than beside the name it translates.
    @SuppressWarnings("ImmutableEnumChecker")
    private final Set<MarkdownFlag> flags;

    MarkdownExtension(Set<MarkdownFlag> flags) {
        this.flags = flags;
    }

    /// The md4c bits this extension turns on.
    ///
    /// Package-private on purpose: md4c's vocabulary stops here, and an application
    /// that could read this would be able to name a type of a module it cannot
    /// require.
    Set<MarkdownFlag> flags() {
        return flags;
    }
}
