package io.github.digitalsmile.goldberry.markdown;

import java.util.Objects;

import io.github.digitalsmile.goldberry.markdown.model.Document;

/// Markdown, as a document you can walk.
///
/// The whole entry point of the Markdown half of `goldberry-html`:
///
/// ```java
/// var document = Markdown.parse(source);                       // GitHub's dialect
/// var plain = Markdown.parse(source, MarkdownSyntax.commonMark());
///
/// var html = MarkdownHtml.of(document);                        // to serve
/// var view = MarkdownView.of(document);                        // to show
/// ```
///
/// ## What this is not
///
/// **Not a second text stack.** Parsing is md4c inside `libgoldberry` — the same
/// place Blend2D, HarfBuzz and Yoga live — and everything above it is records.
/// Nothing here shapes a glyph, measures a line or owns a font; `markdown-view`
/// builds `text` widgets and the toolkit's own cascade and text stack do the rest.
/// That was the condition for Markdown being the toolkit's problem at all
/// (`docs/gaps.md` G8).
///
/// **Not an HTML renderer.** Markdown *to* HTML is
/// [io.github.digitalsmile.goldberry.markdown.html.MarkdownHtml], which is a text
/// transform with no window under it. HTML *in* — an `html-view` with litehtml behind
/// it — is a gap and says so: it needs a native paint surface the export list does
/// not have yet (`book/src/TODO.md`).
///
/// **Not a resolver.** A link's href and a wiki link's target are strings. Whether
/// one may be followed, what a relative path is relative to, and which note
/// `[[Meeting]]` means are the application's answers, in the same division ADR-0291
/// drew for URL schemes.
public final class Markdown {

    private Markdown() {}

    /// Parses `markdown` in [MarkdownSyntax#gitHub()].
    ///
    /// That dialect rather than plain CommonMark because a document written anywhere
    /// in the last decade assumes tables, strikethrough, task lists and bare URLs —
    /// and a note whose check boxes rendered as literal `[x]` would look broken to
    /// whoever wrote it. [#parse(String, MarkdownSyntax)] is how a caller asks for
    /// the specification and nothing else.
    ///
    /// @param markdown the document
    /// @return its blocks. [Document#EMPTY] for empty text, never null
    public static Document parse(String markdown) {
        return parse(markdown, MarkdownSyntax.gitHub());
    }

    /// Parses `markdown` in `syntax`.
    ///
    /// **There is no failure mode.** Markdown has no syntax errors: every string is a
    /// document, and text that looks like nothing in particular is a paragraph. What
    /// can fail is the parse itself — the library not being loadable, or a document
    /// too large to hold — and that is an
    /// [IllegalStateException] rather than something a caller catches per document.
    ///
    /// @param markdown the document
    /// @param syntax which dialect to read it as
    public static Document parse(String markdown, MarkdownSyntax syntax) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(syntax, "syntax");
        if (markdown.isEmpty()) {
            return Document.EMPTY;
        }
        return MarkdownParser.parse(markdown, syntax);
    }

    /// Flips the `index`th task box in `markdown`, and hands back the new source.
    ///
    /// ```java
    /// MarkdownView.following(model.source())
    ///         .onTask(index -> notes.setSource(Markdown.toggleTask(notes.source(), index)));
    /// ```
    ///
    /// ## Why the source and not the model
    ///
    /// A rendered document is a **view** of text the application owns, and ticking a
    /// box is an edit to that text — so the round trip is: the view reports which box,
    /// this rewrites one character, the application stores it, and the binding brings
    /// the parsed document back (ADR-0296, ADR-0300). Nothing in the toolkit writes to
    /// anything, and an application that keeps its notes somewhere else — a CRDT, a
    /// database column, a file — still owns every write.
    ///
    /// The index is the task's **ordinal in document order**, counted the same way the
    /// renderer counts them, which is what makes "the third box on the screen" and
    /// "the third marker in the text" the same thing.
    ///
    /// ## What it does not do
    ///
    /// **It is a rewrite of one character, not a re-serialisation.** Everything else in
    /// the document — the author's spacing, their line endings, a table nobody aligned
    /// — comes back byte for byte, which is the whole reason this is not
    /// `parse`-then-`write`.
    ///
    /// **A marker inside a fenced code block is not a task**, and is skipped, because
    /// it is a program that happens to contain `- [ ]`. An *indented* code block is not
    /// skipped: telling one from a list continuation needs the block structure this
    /// deliberately does not build. A document that hits that is a document with a
    /// four-space-indented `- [ ]` in it, and the escape is a fence.
    ///
    /// @param markdown the source, exactly as the author has it
    /// @param index which task, from zero
    /// @return the source with that one box flipped — or `markdown` itself, unchanged,
    ///         when there is no such task. Unchanged rather than thrown: the index
    ///         comes from a frame that may be one edit behind the text, and a stale
    ///         click should do nothing rather than end the application
    public static String toggleTask(String markdown, int index) {
        Objects.requireNonNull(markdown, "markdown");
        if (index < 0) {
            return markdown;
        }
        return Tasks.toggle(markdown, index);
    }
}
